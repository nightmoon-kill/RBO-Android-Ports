#include "skill_quad.h"
#include <string.h>
#include <math.h>
static int fits(size_t size,uint32_t a,size_t n){return a>=0x400000&&a<=size&&n<=size-a;}
static uint32_t u32(const uint8_t *m,uint32_t a){uint32_t v;memcpy(&v,m+a,4);return v;}
static float f32(const uint8_t *m,uint32_t a){float v;memcpy(&v,m+a,4);return v;}
static void put32(uint8_t *m,uint32_t a,uint32_t v){memcpy(m+a,&v,4);}
static void putf(uint8_t *m,uint32_t a,double v){float f=(float)v;memcpy(m+a,&f,4);}
static int overlaps(uint32_t a,size_t n,uint32_t b,size_t len){return (uint64_t)a+n>b&&(uint64_t)b+len>a;}
static void compare(CPU *s,double a,double b){
    s->fsw_cc&=~((1<<8)|(1<<9)|(1<<10)|(1<<14));
    if(isnan(a)||isnan(b))s->fsw_cc|=(1<<8)|(1<<10)|(1<<14);
    else if(a<b)s->fsw_cc|=1<<8;
    else if(a==b)s->fsw_cc|=1<<14;
}
static int test_coordinate(CPU *s,double value,double border,int mask){
    s->st[(s->top-1)&7]=value;
    compare(s,value,border);
    s->r[EAX]=(s->r[EAX]&0xffff0000u)|(uint16_t)(s->fsw_cc|(s->top<<11));
    unsigned result=((s->r[EAX]>>8)&(unsigned)mask);
    s->CF=s->OF=s->AF=s->SF=0;s->ZF=result==0;s->PF=__builtin_parity(result)==0;
    return result!=0;
}
int skill_quad_apply(uint8_t *m,size_t size,CPU *s){
    if(!m||!s||!fits(size,0x4862a8,0x1c))return 0;
    uint32_t base=s->r[ESI],v=s->r[EDI],stack=s->r[ESP],desc=s->r[EBP],object=s->r[EBX];
    if((uint64_t)base+0x80!=v||!fits(size,base,0x104)||!fits(size,stack,0x1d8)||
       !fits(size,desc,0x16)||!fits(size,object,0x28))return 0;
    uint32_t actor=u32(m,stack+0x1d4);
    if(!fits(size,actor,0x80c))return 0;
    uint32_t resource=u32(m,actor+0x808);
    if(!fits(size,resource,0x6e94))return 0;
    uint32_t textures=u32(m,resource+0x6e90);
    int16_t index;memcpy(&index,m+desc+0x14,2);
    if(index<0||(uint64_t)textures+(unsigned)index*4>0xffffffffull)return 0;
    uint32_t tex_address=textures+(unsigned)index*4;
    if(!fits(size,tex_address,4))return 0;
    /* These are stable source objects in the real layout. Reject aliasing rather
       than speculatively reading values before the original writes occur. */
    const uint32_t sources[]={stack,desc,object,actor,resource,tex_address,0x4862a8};
    const size_t lengths[]={0x1d8,0x16,0x28,0x80c,0x6e94,4,0x1c};
    for(unsigned i=0;i<sizeof sources/sizeof *sources;i++)
        if(overlaps(base+0x80,0x84,sources[i],lengths[i]))return 0;
    double zero=f32(m,0x4862b8);
    for(int i=0;i<4;i++){
        double z=f32(m,v+i*32+8);compare(s,z,zero);
        putf(m,v+i*32+12,(s->fsw_cc&(1<<14))?1.0:(double)f32(m,0x4862ac)/z);
    }
    for(int i=0;i<4;i++)putf(m,v+i*32+8,(double)f32(m,v+i*32+8)*(double)f32(m,0x4862a8));
    uint32_t color=u32(m,object+0x24),specular=u32(m,stack+0x20);
    for(int i=0;i<4;i++){put32(m,v+i*32+20,color);put32(m,v+i*32+16,specular);}
    uint32_t texture=u32(m,tex_address);put32(m,base+0x100,texture);
    for(int i=0;i<4;i++){
        putf(m,v+i*32,(double)f32(m,v+i*32)+(double)f32(m,0x4862c0));
        double y=(double)f32(m,v+i*32+4)+(double)f32(m,0x4862bc);
        s->st[(s->top-1)&7]=y;putf(m,v+i*32+4,y);
    }
    s->r[EAX]=v+128;s->r[ECX]=0;s->r[EDX]=texture;s->r[EBX]=specular;
    for(int side=0;side<4;side++){
        double border=f32(m,side==1?0x4862b4:side==3?0x4862b0:0x4862b8);
        int all=1;
        for(int i=0;i<4;i++){
            double value=f32(m,v+i*32+(side>=2?4:0));
            int result=test_coordinate(s,value,border,side%2?0x41:1);
            if(result!=(side%2?0:1)){all=0;break;}
        }
        if(all){s->eip=SKILL_QUAD_CULLED;return 1;}
    }
    s->eip=SKILL_QUAD_VISIBLE;
    return 1;
}
