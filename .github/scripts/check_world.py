#!/usr/bin/env python3
"""Reads the region files saved by the smoke-test server and checks what the mod did to them.

  random-chunks        every fully generated chunk must be made of a single block, apart from
                       air, water, lava and bedrock.
  no-block-generation  natural terrain and fluids must be gone from every fully generated chunk.
"""
import argparse
import gzip
import os
import struct
import sys
import zlib
from collections import Counter

import numpy as np

AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}

# Left in place on purpose by Random Chunks.
RANDOM_CHUNKS_IGNORED = AIR | {"minecraft:water", "minecraft:lava", "minecraft:bedrock"}

# Things the running game adds to a chunk after it was generated, per dimension: water and lava
# meeting in the ticking spawn chunks, snow and ice from the weather, and the End exit podium,
# which the dragon fight builds at run time.
RANDOM_CHUNKS_TOLERATED = {
    "overworld": {"minecraft:stone", "minecraft:cobblestone", "minecraft:obsidian", "minecraft:snow",
                  "minecraft:ice", "minecraft:fire"},
    "the_nether": {"minecraft:fire", "minecraft:obsidian", "minecraft:cobblestone", "minecraft:basalt"},
    "the_end": {"minecraft:end_portal", "minecraft:wall_torch", "minecraft:torch", "minecraft:dragon_egg",
                "minecraft:end_gateway", "minecraft:fire"},
}

TERRAIN = {
    "overworld": {"minecraft:stone", "minecraft:deepslate", "minecraft:tuff", "minecraft:granite",
                  "minecraft:diorite", "minecraft:andesite", "minecraft:dirt", "minecraft:grass_block",
                  "minecraft:gravel", "minecraft:sand", "minecraft:bedrock"},
    "the_nether": {"minecraft:netherrack", "minecraft:soul_sand", "minecraft:soul_soil", "minecraft:bedrock"},
    "the_end": {"minecraft:end_stone", "minecraft:bedrock"},
}
FLUIDS = {"minecraft:water", "minecraft:lava"}


class Reader:
    def __init__(self, data):
        self.data = data
        self.pos = 0

    def take(self, size):
        value = self.data[self.pos:self.pos + size]
        self.pos += size
        return value

    def unpack(self, fmt):
        size = struct.calcsize(fmt)
        return struct.unpack(fmt, self.take(size))[0]

    def string(self):
        return self.take(self.unpack(">H")).decode("utf-8", "replace")


def read_payload(reader, tag):
    if tag == 1:
        return reader.unpack(">b")
    if tag == 2:
        return reader.unpack(">h")
    if tag == 3:
        return reader.unpack(">i")
    if tag == 4:
        return reader.unpack(">q")
    if tag == 5:
        return reader.unpack(">f")
    if tag == 6:
        return reader.unpack(">d")
    if tag == 7:
        return reader.take(reader.unpack(">i"))
    if tag == 8:
        return reader.string()
    if tag == 9:
        element = reader.unpack(">b")
        return [read_payload(reader, element) for _ in range(reader.unpack(">i"))]
    if tag == 10:
        compound = {}
        while True:
            element = reader.unpack(">b")
            if element == 0:
                return compound
            name = reader.string()
            compound[name] = read_payload(reader, element)
    if tag == 11:
        count = reader.unpack(">i")
        return np.frombuffer(reader.take(4 * count), dtype=">i4")
    if tag == 12:
        count = reader.unpack(">i")
        return np.frombuffer(reader.take(8 * count), dtype=">i8")
    raise ValueError(f"unknown NBT tag {tag}")


def read_nbt(data):
    reader = Reader(data)
    tag = reader.unpack(">b")
    reader.string()
    return read_payload(reader, tag)


def region_chunks(path):
    with open(path, "rb") as handle:
        data = handle.read()
    for index in range(1024):
        (location,) = struct.unpack(">I", data[index * 4:index * 4 + 4])
        offset = location >> 8
        if offset == 0:
            continue
        start = offset * 4096
        (length,) = struct.unpack(">I", data[start:start + 4])
        kind = data[start + 4]
        payload = data[start + 5:start + 4 + length]
        if kind & 128:
            raise SystemExit(f"{path}: chunk stored in an external .mcc file, not supported here")
        if kind == 1:
            raw = gzip.decompress(payload)
        elif kind == 2:
            raw = zlib.decompress(payload)
        elif kind == 3:
            raw = payload
        else:
            raise SystemExit(f"{path}: unsupported chunk compression {kind}")
        yield read_nbt(raw)


def block_name(entry):
    """A palette entry: a compound with a Name, or a plain state string. A list mixing both is
    saved with every plain element wrapped in a compound under an empty key (since 1.21.5)."""
    if isinstance(entry, dict):
        for key in ("", "Name", "name", "id", "block"):
            if key in entry:
                return block_name(entry[key])
        raise SystemExit(f"unknown palette entry layout: {describe(entry)}")
    return str(entry).split("[", 1)[0]


def palette_of(states):
    palette = states.get("palette", [])
    if isinstance(palette, dict):
        # A compound keyed by state rather than a list.
        return list(palette.keys())
    return palette


def describe(value, depth=0):
    """Short structural description of an NBT value, to see the chunk format of a new version."""
    pad = "  " * depth
    if isinstance(value, dict):
        lines = [f"{pad}{{"]
        for key, item in list(value.items())[:12]:
            lines.append(f"{pad}  {key}: {describe(item, depth + 1).strip()}")
        return "\n".join(lines + [f"{pad}}}"])
    if isinstance(value, list):
        head = describe(value[0], depth + 1).strip() if value else ""
        return f"{pad}list[{len(value)}] of {head}"
    if isinstance(value, np.ndarray):
        return f"{pad}array[{len(value)}]"
    if isinstance(value, bytes):
        return f"{pad}bytes[{len(value)}]"
    return f"{pad}{value!r}"[:120]


def section_counts(section):
    states = section.get("block_states")
    if not states:
        return Counter()
    palette = [block_name(entry) for entry in palette_of(states)]
    data = states.get("data")
    if data is None or len(data) == 0 or len(palette) == 1:
        return Counter({palette[0]: 4096}) if palette else Counter()
    bits = max(4, (len(palette) - 1).bit_length())
    per_long = 64 // bits
    words = np.asarray(data).astype(np.uint64)
    shifts = np.arange(per_long, dtype=np.uint64) * np.uint64(bits)
    values = ((words[:, None] >> shifts[None, :]) & np.uint64((1 << bits) - 1)).reshape(-1)[:4096]
    counts = np.bincount(values.astype(np.int64), minlength=len(palette))
    return Counter({palette[i]: int(n) for i, n in enumerate(counts) if n})


def dimension_of(region_dir):
    parts = region_dir.replace("\\", "/").split("/")
    if "DIM-1" in parts or "the_nether" in parts:
        return "the_nether"
    if "DIM1" in parts or "the_end" in parts:
        return "the_end"
    return "overworld"


def full_chunks(world):
    """Yields (dimension, x, z, block counts) for every chunk that finished generating."""
    described = False
    for root, _, files in os.walk(world):
        if os.path.basename(root) != "region":
            continue
        dimension = dimension_of(os.path.relpath(root, world))
        for name in sorted(files):
            if not name.endswith(".mca"):
                continue
            for chunk in region_chunks(os.path.join(root, name)):
                if not described:
                    described = True
                    sample = dict(chunk)
                    sections = sample.get("sections") or []
                    sample["sections"] = [s for s in sections if s.get("block_states")][:1]
                    print("First chunk read, for reference:")
                    print(describe(sample))
                status = chunk.get("Status", chunk.get("status", ""))
                if not str(status).endswith("full"):
                    continue
                counts = Counter()
                for section in chunk.get("sections", []):
                    counts.update(section_counts(section))
                yield dimension, chunk.get("xPos"), chunk.get("zPos"), counts


def check_random_chunks(world, dimensions):
    failures = []
    stats = {}
    for dimension, x, z, counts in full_chunks(world):
        stat = stats.setdefault(dimension, {"chunks": 0, "uniform": 0, "tolerated": 0, "blocks": Counter()})
        stat["chunks"] += 1
        solid = Counter({block: n for block, n in counts.items() if block not in RANDOM_CHUNKS_IGNORED})
        if not solid:
            stat["uniform"] += 1
            continue
        main, _ = solid.most_common(1)[0]
        stat["blocks"][main] += 1
        extras = {block: n for block, n in solid.items() if block != main}
        if not extras:
            stat["uniform"] += 1
        elif set(extras) <= RANDOM_CHUNKS_TOLERATED.get(dimension, set()):
            stat["tolerated"] += 1
        else:
            failures.append(f"{dimension} chunk [{x}, {z}] made of {main} also holds {extras}")

    for dimension, stat in sorted(stats.items()):
        print(f"{dimension}: {stat['chunks']} full chunks, {stat['uniform']} made of a single block, "
              f"{stat['tolerated']} with run-time additions only, {len(stat['blocks'])} different blocks")
        print("  most common:", ", ".join(f"{b} x{n}" for b, n in stat["blocks"].most_common(8)))
    for dimension in dimensions:
        if stats.get(dimension, {}).get("chunks", 0) == 0:
            failures.append(f"no fully generated chunk found in {dimension}")
    return failures


def check_no_block_generation(world, dimensions):
    failures = []
    stats = {}
    for dimension, x, z, counts in full_chunks(world):
        stat = stats.setdefault(dimension, {"chunks": 0, "volume": 0, "solid": 0, "terrain": Counter(), "fluids": 0})
        stat["chunks"] += 1
        stat["volume"] += sum(counts.values())
        stat["solid"] += sum(n for block, n in counts.items() if block not in AIR)
        for block, n in counts.items():
            if block in TERRAIN.get(dimension, set()):
                stat["terrain"][block] += n
            if block in FLUIDS:
                stat["fluids"] += n

    for dimension, stat in sorted(stats.items()):
        terrain = sum(stat["terrain"].values())
        volume = max(stat["volume"], 1)
        print(f"{dimension}: {stat['chunks']} full chunks, {100 * stat['solid'] / volume:.3f}% of the volume "
              f"is not air, natural terrain {100 * terrain / volume:.4f}% {dict(stat['terrain'])}, "
              f"fluid blocks {stat['fluids']}")
        if terrain / volume > 0.005:
            failures.append(f"{dimension}: natural terrain still fills {100 * terrain / volume:.3f}% of the volume")
        if stat["fluids"] > 0:
            failures.append(f"{dimension}: {stat['fluids']} water or lava blocks left")
    for dimension in dimensions:
        if stats.get(dimension, {}).get("chunks", 0) == 0:
            failures.append(f"no fully generated chunk found in {dimension}")
    return failures


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=["random-chunks", "no-block-generation"])
    parser.add_argument("world")
    parser.add_argument("--dimensions", default="overworld,the_nether,the_end")
    args = parser.parse_args()
    dimensions = [d for d in args.dimensions.split(",") if d]
    check = check_random_chunks if args.mode == "random-chunks" else check_no_block_generation
    failures = check(args.world, dimensions)
    for failure in failures[:40]:
        print("FAIL:", failure)
    if len(failures) > 40:
        print(f"... and {len(failures) - 40} more")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
