"""把 free-exercise-db（公共领域）和 exercises_zh.txt 的中文说明合并成 App 的动作库资源。

用法：python build_exercises.py <free-exercise-db 目录> <输出目录，app/src/commonMain/composeResources/files>
需要 Pillow，用来把图片缩小到 400px 宽。
"""
import json
import os
import sys

from PIL import Image

MUSCLES = {
    "abdominals": "腹肌", "abductors": "臀中肌", "adductors": "大腿内侧", "biceps": "肱二头肌",
    "calves": "小腿", "chest": "胸", "forearms": "前臂", "glutes": "臀", "hamstrings": "大腿后侧",
    "lats": "背阔肌", "lower back": "下背", "middle back": "中背", "neck": "颈部",
    "quadriceps": "大腿前侧", "shoulders": "肩", "traps": "斜方肌", "triceps": "肱三头肌",
}
EQUIPMENT = {
    "barbell": "杠铃", "dumbbell": "哑铃", "body only": "徒手", "cable": "龙门架/绳索",
    "machine": "器械", "kettlebells": "壶铃", "bands": "弹力带", "medicine ball": "药球",
    "exercise ball": "瑜伽球", "foam roll": "泡沫轴", "e-z curl bar": "曲杆", "other": "其他", None: "徒手",
}
CATEGORY = {
    "strength": "力量", "stretching": "拉伸", "plyometrics": "跳跃", "powerlifting": "力量",
    "olympic weightlifting": "举重", "strongman": "力量", "cardio": "有氧",
}
LEVEL = {"beginner": "初级", "intermediate": "中级", "expert": "高级"}


def main(src, out):
    data = {d["id"]: d for d in json.load(open(os.path.join(src, "dist", "exercises.json"), encoding="utf-8"))}
    zh_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "exercises_zh.txt")
    img_out = os.path.join(out, "exercises", "img")
    os.makedirs(img_out, exist_ok=True)
    result = []
    for line in open(zh_path, encoding="utf-8"):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        ex_id, name, equip, steps = line.split("|", 3)
        d = data.get(ex_id)
        if d is None:
            print("找不到动作：", ex_id, file=sys.stderr)
            continue
        images = []
        for i, rel in enumerate(d.get("images", [])[:2]):
            target = f"{ex_id}_{i}.jpg"
            im = Image.open(os.path.join(src, "exercises", rel)).convert("RGB")
            if im.width > 400:
                im = im.resize((400, round(im.height * 400 / im.width)), Image.LANCZOS)
            im.save(os.path.join(img_out, target), "JPEG", quality=62, optimize=True)
            images.append("exercises/img/" + target)
        result.append({
            "id": ex_id,
            "name": name,
            "nameEn": d["name"],
            "category": CATEGORY.get(d["category"], d["category"]),
            "equipment": equip or EQUIPMENT.get(d.get("equipment"), d.get("equipment")),
            "primary": [MUSCLES.get(m, m) for m in d.get("primaryMuscles", [])],
            "secondary": [MUSCLES.get(m, m) for m in d.get("secondaryMuscles", [])],
            "level": LEVEL.get(d.get("level"), d.get("level")),
            "steps": [s.strip() for s in steps.split("；") if s.strip()],
            "images": images,
        })
    with open(os.path.join(out, "exercises", "exercises.json"), "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, separators=(",", ":"))
    print(f"共 {len(result)} 个动作")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
