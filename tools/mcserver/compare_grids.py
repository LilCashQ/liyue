#!/usr/bin/env python3
"""Compare a vanilla biome grid dump with a refgen output; print agreement rate."""
import sys


def load_grid(path: str):
    return [[int(v) for v in line.strip().split(",")] for line in open(path) if line.strip()]


def load_refgen(path: str):
    rows = []
    started = False
    for line in open(path):
        line = line.strip()
        if line.startswith("FIELDS"):
            started = True
            continue
        if not started or line.startswith("HASH"):
            continue
        if line:
            rows.append([int(v) for v in line.split(",")])
    return rows


def main() -> None:
    vanilla = load_grid(sys.argv[1])
    native = load_refgen(sys.argv[2])
    if len(vanilla) != len(native) or len(vanilla[0]) != len(native[0]):
        raise SystemExit(
            f"size mismatch {len(vanilla)}x{len(vanilla[0])} vs {len(native)}x{len(native[0])}"
        )

    total = matches = 0
    mismatches = []
    for z in range(len(vanilla)):
        for x in range(len(vanilla[0])):
            total += 1
            if vanilla[z][x] == native[z][x]:
                matches += 1
            elif len(mismatches) < 20:
                mismatches.append((x, z, vanilla[z][x], native[z][x]))

    rate = matches * 100.0 / total
    print(f"cells={total} matches={matches} agreement={rate:.2f}%")
    if mismatches:
        print("first mismatches (x, z, vanilla, native):")
        for row in mismatches:
            print(" ", row)
    else:
        print("no mismatches")


if __name__ == "__main__":
    main()
