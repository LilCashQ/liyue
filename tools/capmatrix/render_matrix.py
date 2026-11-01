#!/usr/bin/env python3
"""Turn build/capability.csv into the Markdown tables in docs/capability-matrix.md."""
import csv
import sys

ORDER = [
    "1.7.10", "1.8.9", "1.9.4", "1.10.2", "1.11.2", "1.12.2", "1.13.2",
    "1.14.4", "1.15.2", "1.16.1", "1.16.5", "1.17.1", "1.18.2", "1.19.2",
    "1.19.4", "1.20.6", "1.21.1", "1.21.3",
]

CN = {
    "village": "村庄", "monument": "海底神殿", "mansion": "林地府邸",
    "outpost": "掠夺者前哨", "ruined_portal": "废弃传送门",
    "ancient_city": "远古城市", "trial_chambers": "试炼密室",
    "fortress": "下界要塞", "bastion": "堡垒遗迹", "end_city": "末地城",
    "desert_pyramid": "沙漠神殿", "jungle_temple": "丛林神庙",
    "swamp_hut": "沼泽小屋", "igloo": "雪屋", "ocean_ruin": "海底废墟",
    "shipwreck": "沉船", "treasure": "埋藏的宝藏", "mineshaft": "废弃矿井",
    "geode": "紫水晶洞", "trail_ruins": "古迹废墟",
    "end_gateway": "末地折跃门", "end_island": "末地外岛",
    "stronghold": "要塞",
}

def main(path: str) -> None:
    rows = list(csv.DictReader(open(path)))
    rows.sort(key=lambda r: ORDER.index(r["version"]))

    print("### 版本 × 维度（群系）\n")
    print("| 版本 | 主世界 | 下界 | 末地 |")
    print("| --- | --- | --- | --- |")
    for r in rows:
        cells = []
        for key in ("dim0", "dim-1", "dim1"):
            cells.append("✓" if r[key] == "1" else "—")
        print(f"| {r['version']} | {' | '.join(cells)} |")

    print("\n### 结构支持区间\n")
    print("| 结构 | 支持版本 |")
    print("| --- | --- |")
    for key, label in CN.items():
        supported = [r["version"] for r in rows if r[key] == "1"]
        if not supported:
            span = "不支持"
        elif supported == ORDER:
            span = "1.7.10 – 1.21.3（全版本）"
        else:
            first = supported[0]
            last = supported[-1]
            contiguous = ORDER[ORDER.index(first):ORDER.index(last) + 1] == supported
            span = f"{first} – {last}" if contiguous else "、".join(supported)
        print(f"| {label} | {span} |")

if __name__ == "__main__":
    main(sys.argv[1])
