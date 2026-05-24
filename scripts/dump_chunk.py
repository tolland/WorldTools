#!/usr/bin/env python3
import struct, zlib, sys
from io import BytesIO

def read_payload(buf, t):
    if t == 1:  return struct.unpack('b', buf.read(1))[0]
    if t == 2:  return struct.unpack('>h', buf.read(2))[0]
    if t == 3:  return struct.unpack('>i', buf.read(4))[0]
    if t == 4:  return struct.unpack('>q', buf.read(8))[0]
    if t == 5:  return struct.unpack('>f', buf.read(4))[0]
    if t == 6:  return struct.unpack('>d', buf.read(8))[0]
    if t == 7:  n=struct.unpack('>i',buf.read(4))[0]; return buf.read(n)
    if t == 8:  n=struct.unpack('>H',buf.read(2))[0]; return buf.read(n).decode('utf-8','replace')
    if t == 9:
        et=struct.unpack('B',buf.read(1))[0]; n=struct.unpack('>i',buf.read(4))[0]
        return [read_payload(buf,et) for _ in range(n)]
    if t == 10:
        d={}
        while True:
            ct=struct.unpack('B',buf.read(1))[0]
            if ct==0: break
            nl=struct.unpack('>H',buf.read(2))[0]; k=buf.read(nl).decode('utf-8','replace')
            d[k]=read_payload(buf,ct)
        return d
    if t == 11: n=struct.unpack('>i',buf.read(4))[0]; return list(struct.unpack(f'>{n}i',buf.read(n*4)))
    if t == 12: n=struct.unpack('>i',buf.read(4))[0]; buf.read(n*8); return f'[long[{n}]]'

def read_chunk(mca, cx, cz):
    lx,lz = cx&31, cz&31
    with open(mca,'rb') as f:
        f.seek((lz*32+lx)*4); b=f.read(4)
        off=(b[0]<<16)|(b[1]<<8)|b[2]
        if not off: return None
        f.seek(off*4096); length=struct.unpack('>I',f.read(4))[0]; f.read(1)
        raw=f.read(length-1)
    buf=BytesIO(zlib.decompress(raw))
    struct.unpack('B',buf.read(1)); nl=struct.unpack('>H',buf.read(2))[0]; buf.read(nl)
    return read_payload(buf,10)

import json
chunk = read_chunk(sys.argv[1], int(sys.argv[2]), int(sys.argv[3]))
bes = chunk.get('block_entities', [])
print(json.dumps(bes, indent=2, default=str))