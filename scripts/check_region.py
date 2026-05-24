#!/usr/bin/env python3
import struct, sys, os

def check_region(mca_path):
    size = os.path.getsize(mca_path)
    if size < 8192:
        print(f"File too small ({size} bytes) — empty or corrupt")
        return

    chunks_found = []
    with open(mca_path, 'rb') as f:
        for i in range(1024):
            f.seek(i * 4)
            b = f.read(4)
            offset = (b[0] << 16) | (b[1] << 8) | b[2]
            if offset == 0:
                continue
            rx, rz = i % 32, i // 32
            f.seek(offset * 4096)
            length = struct.unpack('>I', f.read(4))[0]
            comp = struct.unpack('B', f.read(1))[0]
            chunks_found.append((rx, rz, length, comp))

    comp_names = {1:'gzip', 2:'zlib', 3:'none', 4:'lz4', 128:'external'}
    print(f"{len(chunks_found)} chunks found")
    comps = {}
    for _, _, _, c in chunks_found:
        comps[c] = comps.get(c, 0) + 1
    for c, n in comps.items():
        print(f"  compression {c} ({comp_names.get(c,'unknown')}): {n} chunks")

check_region(sys.argv[1])