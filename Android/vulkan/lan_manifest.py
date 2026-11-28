"""Add the normal INTERNET permission to the pinned binary manifest."""
from struct import pack, unpack_from, pack_into

def add_internet(data):
    chunks=[]; offset=8; names=None
    while offset<len(data):
        kind,header,size=unpack_from('<HHI',data,offset)
        chunk=bytes(data[offset:offset+size])
        if kind==1:
            count,styles,flags,start,_=unpack_from('<IIIII',chunk,8)
            assert not styles and not flags&0x100
            names=[]
            for i in range(count):
                pos=start+unpack_from('<I',chunk,header+i*4)[0]
                length=unpack_from('<H',chunk,pos)[0];assert length<0x8000
                names.append(chunk[pos+2:pos+2+length*2].decode('utf-16le'))
        chunks.append((kind,chunk));offset+=size
    assert names is not None and offset==len(data)
    tag=len(names);names+=['uses-permission','android.permission.INTERNET']
    offsets=[];strings=bytearray()
    for name in names:
        encoded=name.encode('utf-16le');offsets.append(len(strings));strings+=pack('<H',len(encoded)//2)+encoded+b'\0\0'
    while len(strings)%4:strings+=b'\0'
    start=28+4*len(names)
    pool=pack('<HHI',1,28,start+len(strings))+pack('<IIIII',len(names),0,0,start,0)+pack(f'<{len(names)}I',*offsets)+strings
    uri=names.index('http://schemas.android.com/apk/res/android');name_attr=names.index('name')
    common=lambda kind,size:pack('<HHIII',kind,16,size,1,0xffffffff)
    permission=common(0x102,56)+pack('<IIHHHHHH',0xffffffff,tag,20,20,1,0,0,0)+pack('<IIIHBBI',uri,name_attr,tag+1,8,0,3,tag+1)
    permission+=common(0x103,24)+pack('<II',0xffffffff,tag)
    body=bytearray();inserted=False
    for kind,chunk in chunks:
        if kind==1:chunk=pool
        if kind==0x102 and names[unpack_from('<I',chunk,20)[0]]=='application':
            assert not inserted;body+=permission;inserted=True
        body+=chunk
    assert inserted
    return pack('<HHI',3,8,8+len(body))+body
