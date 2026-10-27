"""Builds src/main/resources/whitebag/collection-items.txt for the Collection tab.

It reads ut_items.json (made by the Femcore bot's build_ut_data.py from the RealmEye wiki) and writes one line
per item and place it drops:  place|tier|item name

    python tools/build_collection.py path/to/ut_items.json

Only items that can drop in a bag are kept (shop, mystery box and event-chest-only items, and Retro items that no
longer drop, are left out). Holiday event items, which the wiki lists under every boss that drops them during the
event, go under "Events and seasonal"; items from bosses that wander into many dungeons go under "Many dungeons".
"""
import json
import re
import os
import sys

REALM = {"The Realm", "Beach", "Low Forest", "Mid Forest", "High Forest", "Low Plains", "Mid Plains", "High Plains",
         "Mid Desert", "High Desert", "Highlands", "Midlands", "Mountains", "Adept Biomes", "Veteran Biomes"}
ELSEWHERE = {"Dungeons", "Various Dungeons", "Lava Fissure setpiece)"}
SAME = {"Spectral Penetentiary": "Spectral Penitentiary"}
# Drops the wiki doesn't give a place for: the boss names that tell us where (old "Retro" items, seasonal versions)
BOSS_PLACES = [
    ("septavius", "Undead Lair"), ("arachna", "Spider Den"), ("malphas", "Abyss of Demons"),
    ("pyyr", "Lair of Draconis"), ("limoz", "Lair of Draconis"), ("feargus", "Lair of Draconis"),
    ("nikao", "Lair of Draconis"), ("bilgewater", "Pirate Cave"), ("shaitan", "Lair of Shaitan"),
    ("megamoth", "Woodland Labyrinth"), ("killer bee queen", "Plagued Nest"), ("twilight archmage", "The Shatters"),
    ("forgotten king", "The Shatters"), ("limon", "Sprite World"),
]
EVENTS = "Events and seasonal"
MANY = "Many dungeons"
# Bosses that only drop things during a holiday event
EVENT_WORDS = r"christmas|oryxmas|halloween|candy|valentine|snowman|easter|jolly|rudolph|\bloot elf\b|frost present|new year|event chest"
# An item dropped in this many places or more comes from a boss that can turn up anywhere, not from those dungeons
WANDERING = 8
# Dungeons whose fights are other dungeons' bosses again: their drops are filed under where they really come from
BORROWED = {"The Trials of Cronus"}
KEEP = set()


def dungeons():
    """The dungeons on the Dungeons tab (dungeon-atlas.txt): they always keep their own page in the Collection."""
    atlas = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "whitebag", "dungeon-atlas.txt")
    try:
        with open(atlas, encoding="utf-8") as f:
            return {l.split("|")[1] for l in f if l.startswith("D|")} - BORROWED
    except OSError:
        return set()


def place(name):
    name = name.replace("’", "'").strip()
    name = SAME.get(name, name)
    if name in REALM:
        return "The Realm"
    if name in ELSEWHERE:
        return "Elsewhere"  # dropped again below if the item has a real place
    return name


def main(src, out):
    global KEEP
    KEEP = dungeons()
    data = json.load(open(src, encoding="utf-8"))
    items, where = data["items"], data["enemy_locations"]
    lines = set()
    per_item = {}
    for item in items.values():
        if not item.get("drops"):
            continue
        if "(Retro)" in item["name"]:
            continue  # old versions from past events: they don't drop any more
        places = set()
        holiday = False
        for enemy in item["drops"]:
            if re.search(EVENT_WORDS, enemy.lower()):
                holiday = True  # only during a holiday event: doesn't say where it drops the rest of the year
                continue
            for loc in where.get(item.get("links", {}).get(enemy), []):
                places.add(place(loc["name"]))
        many = "Elsewhere" in places   # the wiki only says "various dungeons"
        places.discard("Elsewhere")
        places -= BORROWED
        if not places:
            for enemy in item["drops"]:
                for key, p in BOSS_PLACES:
                    if key in enemy.lower() and not re.search(EVENT_WORDS, enemy.lower()):
                        places.add(p)
        if not places and any("quest chest" in e.lower() for e in item["drops"]):
            places = {"Quest chests"}
        if len(places) >= WANDERING:
            # during a holiday event almost every boss drops it; otherwise it's from a boss that wanders into lots of
            # dungeons (Interregnum and the like)
            places = {EVENTS if len(item["drops"]) >= 15 else MANY}
        elif not places and holiday:
            places = {EVENTS}
        per_item[(item['tier'], item['name'].strip())] = places or {MANY if many else EVENTS}
    # A place whose every item also drops somewhere else has nothing of its own (e.g. The Trials of Cronus, whose
    # bosses are other dungeons' bosses): leave it out, so its items only show where they really come from.
    left_out = []
    while True:
        own, size = {}, {}
        for places in per_item.values():
            for p in places:
                own.setdefault(p, 0)
                size[p] = size.get(p, 0) + 1
            if len(places) == 1:
                own[next(iter(places))] += 1
        removed = False
        # variants (Heroic, Legacy, Neo...) go before the dungeon they're a version of
        variant = lambda x: not re.match(r"(Heroic|Legacy|Neo|Infernal) |.*(Rift|Takeover)", x)
        for p in sorted((p for p, n in own.items() if n == 0), key=lambda x: (variant(x), size[x])):
            # only if every item there still has somewhere else to go, and it isn't a dungeon you can run yourself
            if p not in KEEP and all(len(pl) > 1 for pl in per_item.values() if p in pl):
                for key in per_item:
                    per_item[key] = per_item[key] - {p}
                left_out.append(p)
                removed = True
                break
        if not removed:
            break
    print("left out (nothing of its own):", ", ".join(sorted(left_out)))
    for (tier, name), places in per_item.items():
        for p in places:
            lines.add(f"{p}|{tier}|{name}")
    with open(out, "w", encoding="utf-8") as f:
        f.write(f"# White Bag Pup collection book: place|tier|item (from the RealmEye wiki, {data.get('generated', '?')})\n")
        for line in sorted(lines):
            f.write(line + "\n")
    print(f"{len(lines)} lines, {len({l.split('|')[2] for l in lines})} items -> {out}")


if __name__ == "__main__":
    here = os.path.dirname(os.path.abspath(__file__))
    main(sys.argv[1] if len(sys.argv) > 1 else "ut_items.json",
         os.path.join(here, "..", "src", "main", "resources", "whitebag", "collection-items.txt"))
