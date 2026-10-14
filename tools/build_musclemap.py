"""把 MuscleMap（MIT 许可，github.com/melihcolpan/MuscleMap）的人体肌肉轮廓转成 App 资源。

用法：python build_musclemap.py <MuscleMap 的 Sources/MuscleMap/Data 目录> <输出 assets 目录>
输出 musclemap/{male,female}_{front,back}.json：
  {"viewBox": [x, y, w, h], "parts": [{"slug": "chest", "paths": ["M..."]}]}
"""
import json
import os
import re
import sys

VIEWBOXES = {
    "male_front": (0, 95, 727, 1280),
    "male_back": (718, 95, 727, 1280),
    "female_front": (0, 0, 650, 1450),
    "female_back": (823, 0, 650, 1450),
}

SOURCES = {
    "male_front": "MaleFrontPaths.swift",
    "male_back": "MaleBackPaths.swift",
    "female_front": "FemaleFrontPaths.swift",
    "female_back": "FemaleBackPaths.swift",
}

# 细分区域和主肌群重叠，默认视图里不画（与 MuscleMap 默认模式一致）
SKIP = {
    "upperChest", "lowerChest", "innerQuad", "outerQuad", "upperAbs", "lowerAbs",
    "frontDeltoid", "rearDeltoid", "upperTrapezius", "lowerTrapezius",
    "serratus", "hipFlexors", "rhomboids", "rotatorCuff",
}


def parse(text):
    parts = []
    # 每个 BodyPartPathData(slug: .xxx, ...) 到下一个 slug 之间的字符串字面量都是它的路径
    pieces = re.split(r"slug:\s*\.", text)[1:]
    for piece in pieces:
        slug = re.match(r"(\w+)", piece).group(1)
        paths = re.findall(r'"([Mm][^"]+)"', piece)
        if slug in SKIP or not paths:
            continue
        parts.append({"slug": slug, "paths": paths})
    return parts


def main(src, out):
    os.makedirs(os.path.join(out, "musclemap"), exist_ok=True)
    for name, file in SOURCES.items():
        with open(os.path.join(src, file), encoding="utf-8") as f:
            parts = parse(f.read())
        data = {"viewBox": VIEWBOXES[name], "parts": parts}
        target = os.path.join(out, "musclemap", f"{name}.json")
        with open(target, "w", encoding="utf-8") as f:
            json.dump(data, f, separators=(",", ":"))
        print(name, len(parts), "parts", os.path.getsize(target) // 1024, "KB")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
