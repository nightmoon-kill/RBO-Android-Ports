/* Bounded raw asset cache, shared across reopens of the same immutable file.
   A single low-priority worker reads ahead; it never accesses guest memory. */
#include "asset_cache.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <errno.h>
#ifdef _WIN32
#include <windows.h>
#include <io.h>
typedef SRWLOCK Mutex;
typedef CONDITION_VARIABLE Condition;
#define MUTEX_INIT SRWLOCK_INIT
#define CONDITION_INIT CONDITION_VARIABLE_INIT
static void lock(Mutex *m){AcquireSRWLockExclusive(m);}
static void unlock(Mutex *m){ReleaseSRWLockExclusive(m);}
static void wake(Condition *c){WakeConditionVariable(c);}
static void wait_for(Condition *c,Mutex *m){SleepConditionVariableSRW(c,m,INFINITE,0);}
#define CLOSE _close
#define DUP _dup
static Mutex read_lock=MUTEX_INIT;
static int64_t read_at(int fd,void *p,size_t n,uint64_t off){
    lock(&read_lock);
    int64_t result=_lseeki64(fd,(int64_t)off,SEEK_SET)<0?-1:_read(fd,p,(unsigned)n);
    unlock(&read_lock); return result;
}
#else
#include <unistd.h>
#include <pthread.h>
#include <sys/resource.h>
typedef pthread_mutex_t Mutex;
typedef pthread_cond_t Condition;
#define MUTEX_INIT PTHREAD_MUTEX_INITIALIZER
#define CONDITION_INIT PTHREAD_COND_INITIALIZER
static void lock(Mutex *m){pthread_mutex_lock(m);}
static void unlock(Mutex *m){pthread_mutex_unlock(m);}
static void wake(Condition *c){pthread_cond_signal(c);}
static void wait_for(Condition *c,Mutex *m){pthread_cond_wait(c,m);}
#define CLOSE close
#define DUP dup
static int64_t read_at(int fd,void *p,size_t n,uint64_t off){
    ssize_t result;
    do { result=pread(fd,p,n,(off_t)off); } while(result<0&&errno==EINTR);
    return result;
}
#endif

enum { BLOCK=128*1024, SLOTS=256, QUEUED=8 };
typedef struct { uint64_t path, size, modified; } FileKey;
struct AssetFile { int fd; FileKey key; };
typedef struct {
    FileKey key;
    uint64_t offset, used;
    unsigned char *data;
    size_t bytes;
} Block;
typedef struct { AssetFile file; uint64_t offset; } Job;
static Block blocks[SLOTS];
static Job jobs[QUEUED];
static unsigned head,tail;
static int worker_started,inflight;
static Mutex cache_lock=MUTEX_INIT;
static Condition available=CONDITION_INIT;
static uint64_t age,hits,misses,prefetched,read_bytes,resident;

static int same(FileKey a,FileKey b){return a.path==b.path&&a.size==b.size&&a.modified==b.modified;}
static int find(FileKey key,uint64_t offset){
    for(int i=0;i<SLOTS;i++) if(blocks[i].data&&blocks[i].offset==offset&&same(blocks[i].key,key)) return i;
    return -1;
}
static int read_block(const AssetFile *file,uint64_t offset,unsigned char *out,size_t *bytes){
    size_t length=file->key.size-offset>BLOCK?BLOCK:(size_t)(file->key.size-offset),done=0;
    while(done<length){
        int64_t got=read_at(file->fd,out+done,length-done,offset+done);
        if(got<=0) return 0;
        done+=(size_t)got;
    }
    *bytes=done;return 1;
}
/* Caller holds cache_lock. The replacement policy cannot exceed 32 MiB. */
static void insert(FileKey key,uint64_t offset,unsigned char *data,size_t bytes){
    int slot=find(key,offset);
    if(slot>=0){free(data);return;}
    slot=0;
    for(int i=0;i<SLOTS;i++){
        if(!blocks[i].data){slot=i;break;}
        if(blocks[i].used<blocks[slot].used)slot=i;
    }
    if(blocks[slot].data){resident-=BLOCK;free(blocks[slot].data);}
    blocks[slot]=(Block){key,offset,++age,data,bytes};
    resident+=BLOCK;
}
static void *worker(void *unused){
    (void)unused;
#ifndef _WIN32
    (void)setpriority(PRIO_PROCESS,0,5);
#endif
    for(;;){
        lock(&cache_lock);
        while(head==tail)wait_for(&available,&cache_lock);
        Job job=jobs[tail++%QUEUED];
        inflight++;
        int cached=find(job.file.key,job.offset)>=0;
        unlock(&cache_lock);
        unsigned char *data=cached?NULL:malloc(BLOCK);size_t n=0;
        if(data&&read_block(&job.file,job.offset,data,&n)){
            lock(&cache_lock);insert(job.file.key,job.offset,data,n);prefetched++;unlock(&cache_lock);
        }else free(data);
        CLOSE(job.file.fd);
        lock(&cache_lock);inflight--;unlock(&cache_lock);
    }
    return NULL;
}
#ifdef _WIN32
static DWORD WINAPI win_worker(LPVOID p){worker(p);return 0;}
#endif
static void start_worker(void){
    if(worker_started)return;
#ifdef _WIN32
    HANDLE thread=CreateThread(NULL,0,win_worker,NULL,0,NULL);
    if(thread){CloseHandle(thread);worker_started=1;}
#else
    pthread_t thread;
    if(!pthread_create(&thread,NULL,worker,NULL)){pthread_detach(thread);worker_started=1;}
#endif
}
AssetFile *asset_cache_open(const char *path){
#ifdef _WIN32
    int fd=_open(path,_O_RDONLY|_O_BINARY);struct _stat64 info;
    if(fd<0)return NULL;
    if(_fstat64(fd,&info)||!(info.st_mode&_S_IFREG)){CLOSE(fd);return NULL;}
#else
    int fd=open(path,O_RDONLY|O_CLOEXEC);struct stat info;
    if(fd<0)return NULL;
    if(fstat(fd,&info)||!S_ISREG(info.st_mode)){CLOSE(fd);return NULL;}
#endif
    AssetFile *file=malloc(sizeof *file);if(!file){CLOSE(fd);return NULL;}
    uint64_t hash=1469598103934665603ull;
    for(const unsigned char *p=(const unsigned char*)path;*p;p++)hash=(hash^*p)*1099511628211ull;
    file->fd=fd;
    uint64_t stamp=(uint64_t)info.st_mtime;
#ifndef _WIN32
    stamp=stamp*1000000000ull+(uint64_t)info.st_mtim.tv_nsec;
#endif
    file->key=(FileKey){hash,(uint64_t)info.st_size,stamp};
    return file;
}
void asset_cache_close(AssetFile *file){if(file){CLOSE(file->fd);free(file);}}
uint64_t asset_cache_size(const AssetFile *file){return file?file->key.size:0;}
int64_t asset_cache_read(AssetFile *file,uint64_t offset,void *out,size_t size){
    if(!file||(!out&&size))return -1;
    if(offset>=file->key.size)return 0;
    if(size>file->key.size-offset)size=(size_t)(file->key.size-offset);
    size_t done=0;
    while(done<size){
        uint64_t base=(offset+done)/BLOCK*BLOCK;
        size_t inside=(size_t)(offset+done-base),n=BLOCK-inside;
        if(n>size-done)n=size-done;
        lock(&cache_lock);
        int slot=find(file->key,base);
        if(slot>=0){
            memcpy((unsigned char*)out+done,blocks[slot].data+inside,n);
            blocks[slot].used=++age;hits++;read_bytes+=n;
            unlock(&cache_lock);
        }else{
            misses++;unlock(&cache_lock);
            unsigned char *data=malloc(BLOCK);size_t got=0;
            if(!data||!read_block(file,base,data,&got)){free(data);return done?(int64_t)done:-1;}
            memcpy((unsigned char*)out+done,data+inside,n);
            lock(&cache_lock);insert(file->key,base,data,got);read_bytes+=n;unlock(&cache_lock);
        }
        done+=n;
    }
    return (int64_t)done;
}
void asset_cache_prefetch(AssetFile *file,uint64_t offset){
    if(!file)return;
    offset=offset/BLOCK*BLOCK;
    if(offset>=file->key.size)return;
    lock(&cache_lock);
    if(head-tail>=QUEUED||find(file->key,offset)>=0){unlock(&cache_lock);return;}
    for(unsigned i=tail;i<head;i++)if(jobs[i%QUEUED].offset==offset&&same(jobs[i%QUEUED].file.key,file->key)){
        unlock(&cache_lock);return;
    }
    start_worker();
    if(worker_started){
        int fd=DUP(file->fd);
        if(fd>=0){jobs[head++%QUEUED]=(Job){{fd,file->key},offset};wake(&available);}
    }
    unlock(&cache_lock);
}
void asset_cache_stats(char *out,int size){
    if(!out||size<=0)return;
    lock(&cache_lock);
    snprintf(out,(size_t)size,"[Asset cache] hits=%llu misses=%llu prefetch=%llu queued=%u inflight=%d resident_kib=%llu read_kib=%llu\n",
        (unsigned long long)hits,(unsigned long long)misses,(unsigned long long)prefetched,head-tail,inflight,
        (unsigned long long)(resident/1024),(unsigned long long)(read_bytes/1024));
    unlock(&cache_lock);
}
