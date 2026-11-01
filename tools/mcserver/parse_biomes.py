#!/usr/bin/env python3
"""Dump the surface biome grid (y=63) from vanilla Anvil region files.

This is ground truth produced by the real Minecraft 1.20.1 server, used to
validate the cubiomes-based generator. No third-party NBT library is required.

usage: parse_biomes.py <region_dir> <x0> <z0> <cells> <cellBlocks> <out.csv>
"""
import os
import struct
import sys
import zlib


class Reader:
    def __init__(self, data: bytes):
        self.data = data
        self.pos = 0

    def u1(self) -> int:
        v = self.data[self.pos]
        self.pos += 1
        return v

    def i1(self) -> int:
        v = struct.unpack_from(">b", self.data, self.pos)[0]
        self.pos += 1
        return v

    def i2(self) -> int:
        v = struct.unpack_from(">h", self.data, self.pos)[0]
        self.pos += 2
        return v

    def i4(self) -> int:
        v = struct.unpack_from(">i", self.data, self.pos)[0]
        self.pos += 4
        return v

    def i8(self) -> int:
        v = struct.unpack_from(">q", self.data, self.pos)[0]
        self.pos += 8
        return v

    def f4(self) -> float:
        v = struct.unpack_from(">f", self.data, self.pos)[0]
        self.pos += 4
        return v

    def f8(self) -> float:
        v = struct.unpack_from(">d", self.data, self.pos)[0]
        self.pos += 8
        return v

    def string(self) -> str:
        n = struct.unpack_from(">H", self.data, self.pos)[0]
        self.pos += 2
        s = self.data[self.pos:self.pos + n].decode("utf-8")
        self.pos += n
        return s

    def payload(self, tag: int):
        if tag == 1:
            return self.i1()
        if tag == 2:
            return self.i2()
        if tag == 3:
            return self.i4()
        if tag == 4:
            return self.i8()
        if tag == 5:
            return self.f4()
        if tag == 6:
            return self.f8()
        if tag == 7:
            n = self.i4()
            v = self.data[self.pos:self.pos + n]
            self.pos += n
            return v
        if tag == 8:
            return self.string()
        if tag == 9:
            elem = self.u1()
            n = self.i4()
            return [self.payload(elem) for _ in range(n)]
        if tag == 10:
            out = {}
            while True:
                t = self.u1()
                if t == 0:
                    return out
                name = self.string()
                out[name] = self.payload(t)
        if tag == 11:
            n = self.i4()
            v = list(struct.unpack_from(f">{n}i", self.data, self.pos))
            self.pos += 4 * n
            return v
        if tag == 12:
            n = self.i4()
            v = list(struct.unpack_from(f">{n}q", self.data, self.pos))
            self.pos += 8 * n
            return v
        raise ValueError(f"unsupported NBT tag {tag}")

    def named_root(self):
        tag = self.u1()
        if tag != 10:
            raise ValueError(f"root tag {tag} != compound")
        self.string()
        return self.payload(10)


def read_chunk(region: bytes, cx: int, cz: int):
    idx = (cx % 32) + (cz % 32) * 32
    entry = struct.unpack_from(">I", region, idx * 4)[0]
    offset = entry >> 8
    if offset == 0:
        return None
    pos = offset * 4096
    length = struct.unpack_from(">I", region, pos)[0]
    compression = region[pos + 4]
    payload = region[pos + 5:pos + 4 + length]
    if compression == 1:
        payload = zlib.decompress(payload, 16 + zlib.MAX_WBITS)
    elif compression == 2:
        payload = zlib.decompress(payload)
    elif compression != 3:
        raise ValueError(f"unsupported compression {compression}")
    return Reader(payload).named_root()


class RegionSource:
    def __init__(self, directory: str):
        self.directory = directory
        self.cache = {}

    def chunk(self, cx: int, cz: int):
        rx, rz = cx >> 5, cz >> 5
        key = (rx, rz)
        if key not in self.cache:
            path = os.path.join(self.directory, f"r.{rx}.{rz}.mca")
            self.cache[key] = open(path, "rb").read() if os.path.exists(path) else None
        region = self.cache[key]
        return read_chunk(region, cx, cz) if region else None


def section_at_y(chunk, section_y: int):
    for sec in chunk["sections"]:
        if sec.get("Y") == section_y:
            return sec
    raise KeyError(f"section Y={section_y} missing")


def main() -> None:
    region_dir, x0, z0, cells, cell_blocks, out_path = sys.argv[1:7]
    x0, z0, cells, cell_blocks = int(x0), int(z0), int(cells), int(cell_blocks)

    ids = {}
    id_file = os.path.join(os.path.dirname(__file__), "biome_ids.tsv")
    for line in open(id_file):
        bid, name = line.rstrip("\n").split("\t", 1)
        ids.setdefault(name, int(bid))

    source = RegionSource(region_dir)
    section_y = 63 >> 4
    local_y = (63 & 15) >> 2

    grid = []
    for iz in range(cells):
        block_z = z0 + iz * cell_blocks
        row = []
        for ix in range(cells):
            block_x = x0 + ix * cell_blocks
            chunk = source.chunk(block_x >> 4, block_z >> 4)
            if chunk is None:
                raise SystemExit(f"chunk {block_x >> 4},{block_z >> 4} missing")

            section = section_at_y(chunk, section_y)
            biomes = section["biomes"]
            palette = biomes["palette"]
            if len(palette) == 1:
                row.append(ids.get(palette[0].split(":")[-1], -1))
                continue
            data = biomes["data"]

            bits = max(1, (len(palette) - 1).bit_length())
            per_long = 64 // bits
            index = (local_y * 4 + ((block_z & 15) >> 2)) * 4 + ((block_x & 15) >> 2)
            value = (data[index // per_long] >> ((index % per_long) * bits)) & ((1 << bits) - 1)
            name = palette[value].split(":")[-1]
            row.append(ids.get(name, -1))
        grid.append(row)

    with open(out_path, "w") as fh:
        for row in grid:
            fh.write(",".join(str(v) for v in row) + "\n")
    print(f"wrote {out_path}: {cells}x{cells} cells, {cell_blocks} blocks/cell")


if __name__ == "__main__":
    main()
