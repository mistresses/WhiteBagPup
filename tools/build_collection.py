"""Builds src/main/resources/whitebag/collection-items.txt for the Collection tab.

It reads ut_items.json (made by the Femcore bot's build_ut_data.py from the RealmEye wiki) and writes one line
per item and place it drops:  place|tier|item name

    python tools/build_collection.py path/to/ut_items.json

Only items that can drop in a bag are kept (shop, mystery box and event-chest-only items are left out).
"""
import json
import os
import sys

REALM = {"The Realm", "Beach", "Low Forest", "Mid Forest", "High Forest", "Low Plains", "Mid Plains", "High Plains",
         "Mid Desert", "High Desert", "Highlands", "Midlands", "Mountains", "Adept Biomes", "Veteran Biomes"}
ELSEWHERE = {"Dungeons", "Various Dungeons", "Lava Fissure setpiece)"}
SAME = {"Spectral Penetentiary": "Spectral Penitentiary"}


def place(name):
    name = name.replace("’", "'").strip()
    name = SAME.get(name, name)
    if name in REALM:
        return "The Realm"
    if name in ELSEWHERE:
        return "Elsewhere"
    return name


def main(src, out):
    data = json.load(open(src, encoding="utf-8"))
    items, where = data["items"], data["enemy_locations"]
    lines = set()
    for item in items.values():
        if not item.get("drops"):
            continue
        places = set()
        for enemy in item["drops"]:
            for loc in where.get(item.get("links", {}).get(enemy), []):
                places.add(place(loc["name"]))
        for p in places or {"Elsewhere"}:
            lines.add(f"{p}|{item['tier']}|{item['name'].strip()}")
    with open(out, "w", encoding="utf-8") as f:
        f.write(f"# White Bag Pup collection book: place|tier|item (from the RealmEye wiki, {data.get('generated', '?')})\n")
        for line in sorted(lines):
            f.write(line + "\n")
    print(f"{len(lines)} lines, {len({l.split('|')[2] for l in lines})} items -> {out}")


if __name__ == "__main__":
    here = os.path.dirname(os.path.abspath(__file__))
    main(sys.argv[1] if len(sys.argv) > 1 else "ut_items.json",
         os.path.join(here, "..", "src", "main", "resources", "whitebag", "collection-items.txt"))
