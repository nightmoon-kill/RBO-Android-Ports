"""Exercise the actual native cache and its background reader on host files."""
from pathlib import Path
import ctypes as C
import concurrent.futures
import os
import random
import re
import sys
import tempfile
import time
import unittest

lib=C.CDLL(str(Path(sys.argv.pop(1)).resolve()))
lib.asset_cache_open.argtypes=[C.c_char_p];lib.asset_cache_open.restype=C.c_void_p
lib.asset_cache_close.argtypes=[C.c_void_p]
lib.asset_cache_read.argtypes=[C.c_void_p,C.c_uint64,C.c_void_p,C.c_size_t];lib.asset_cache_read.restype=C.c_int64
lib.asset_cache_prefetch.argtypes=[C.c_void_p,C.c_uint64]
lib.asset_cache_stats.argtypes=[C.c_char_p,C.c_int]
BLOCK=128*1024
def stats():
    out=C.create_string_buffer(512);lib.asset_cache_stats(out,512)
    return {k:int(v) for k,v in re.findall(r'(\w+)=(\d+)',out.value.decode())}

class AssetCacheTests(unittest.TestCase):
    def setUp(self):
        self.dir=tempfile.TemporaryDirectory()
        self.path=Path(self.dir.name)/'skill.PAC'
        self.data=random.Random(42).randbytes(BLOCK*5+371)
        self.path.write_bytes(self.data)
        self.file=lib.asset_cache_open(os.fsencode(self.path))
        self.assertTrue(self.file)
    def tearDown(self):
        deadline=time.monotonic()+4
        while (stats()['queued'] or stats()['inflight']) and time.monotonic()<deadline:time.sleep(.01)
        lib.asset_cache_close(self.file);self.dir.cleanup()
    def read(self,offset,size,file=None):
        out=C.create_string_buffer(size+16)
        out.raw=b'\xA5'*len(out)
        got=lib.asset_cache_read(file or self.file,offset,out,size)
        self.assertGreaterEqual(got,0)
        self.assertEqual(out.raw[got:],b'\xA5'*(len(out)-got))
        return out.raw[:got]
    def test_random_and_boundary_reads(self):
        cases=[(0,4),(BLOCK-7,50),(len(self.data)-20,300),(len(self.data)+1,500),(10,0)]
        rng=random.Random(19)
        cases += [(rng.randrange(len(self.data)),rng.randrange(1,BLOCK*2)) for _ in range(70)]
        for offset,size in cases:self.assertEqual(self.read(offset,size),self.data[offset:offset+size])
    def test_reopen_reuses_cached_bytes(self):
        self.read(BLOCK+5,2048)
        before=stats()
        second=lib.asset_cache_open(os.fsencode(self.path))
        try:self.assertEqual(self.read(BLOCK+5,2048,second),self.data[BLOCK+5:BLOCK+2053])
        finally:lib.asset_cache_close(second)
        self.assertGreater(stats()['hits'],before['hits'])
        self.assertEqual(stats()['misses'],before['misses'])
    def test_worker_prefetch_and_close(self):
        before=stats()['prefetch']
        lib.asset_cache_prefetch(self.file,BLOCK*4)
        # The worker owns a duplicate descriptor, so closing its source is safe.
        lib.asset_cache_close(self.file);self.file=None
        deadline=time.monotonic()+4
        while stats()['prefetch']==before and time.monotonic()<deadline:time.sleep(.01)
        self.assertGreater(stats()['prefetch'],before)
        self.file=lib.asset_cache_open(os.fsencode(self.path))
        misses=stats()['misses']
        self.assertEqual(self.read(BLOCK*4,100),self.data[BLOCK*4:BLOCK*4+100])
        self.assertEqual(stats()['misses'],misses)
    def test_concurrent_reads_and_prefetch(self):
        def check(index):
            rng=random.Random(index)
            for _ in range(40):
                offset=rng.randrange(len(self.data)-9000)
                lib.asset_cache_prefetch(self.file,offset)
                self.assertEqual(self.read(offset,9000),self.data[offset:offset+9000])
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:list(pool.map(check,range(4)))
    def test_size_change_invalidates_on_reopen(self):
        self.read(0,100)
        lib.asset_cache_close(self.file);self.file=None
        self.data=b'new data'*100
        self.path.write_bytes(self.data)
        self.file=lib.asset_cache_open(os.fsencode(self.path))
        self.assertEqual(self.read(0,100),self.data[:100])
    def test_memory_limit_and_eviction(self):
        path=Path(self.dir.name)/'large.PAC'
        with path.open('wb') as out:
            for i in range(270):out.write(bytes([i%251])*BLOCK)
        large=lib.asset_cache_open(os.fsencode(path))
        try:
            for i in range(270):self.assertEqual(self.read(i*BLOCK,32,large),bytes([i%251])*32)
            self.assertLessEqual(stats()['resident_kib'],32768)
            self.assertEqual(self.read(0,32,large),bytes(32))
        finally:lib.asset_cache_close(large)
    def test_missing_and_invalid_arguments(self):
        self.assertFalse(lib.asset_cache_open(os.fsencode(self.path.with_name('missing.DAT'))))
        self.assertEqual(lib.asset_cache_read(None,0,None,1),-1)

if __name__=='__main__':unittest.main()
