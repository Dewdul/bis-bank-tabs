#!/usr/bin/env python3
"""Write src/test/resources/com/ultimatebanktabs/fixture.json: item ids and
equipment stats (from the OSRS Wiki) for the items the unit tests own.

RuneLite downloads item stats at runtime, so tests carry their own copy.
Usage: python tools/make_test_fixture.py
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_data as gd  # noqa: E402
import wiki_recipes  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/test/resources/com/ultimatebanktabs/fixture.json"

ITEMS = [
    # maxed melee
    "Torva full helm", "Torva platebody", "Torva platelegs", "Infernal cape", "Amulet of rancour", "Ferocious gloves",
    "Primordial boots", "Ultor ring", "Avernic defender", "Rada's blessing 4", "Scythe of Vitur", "Osmumten's fang",
    "Blade of Saeldor (c)", "Inquisitor's mace", "Abyssal tentacle", "Abyssal whip", "Dragon warhammer",
    "Bandos godsword", "Dragon claws", "Slayer helmet (i)", "Salve amulet(ei)", "Inquisitor's great helm",
    "Inquisitor's hauberk", "Inquisitor's plateskirt", "Neitiznot faceguard", "Bandos chestplate", "Bandos tassets",
    "Fire cape", "Barrows gloves", "Dragon defender", "Soulreaper axe", "Ghrazi rapier", "Elder maul",
    "Dragon hunter lance", "Ursine chainmace",
    "Super combat potion", "Saradomin brew", "Super restore", "Shark", "Anglerfish",
    # low level
    "Rune scimitar", "Rune longsword", "Rune full helm", "Rune platebody", "Rune platelegs", "Rune kiteshield",
    "Amulet of strength", "Leather boots", "Climbing boots", "Iron scimitar", "Chef's hat",
    # ranged
    "Twisted bow", "Dragon arrow", "Ruby dragon bolts (e)", "Diamond dragon bolts (e)", "Armadyl crossbow",
    "Masori mask (f)", "Masori body (f)", "Masori chaps (f)", "Zaryte vambraces", "Necklace of anguish",
    "Dizana's quiver", "Pegasian boots", "Venator ring", "Bow of Faerdhinen (c)", "Holy blessing",
    "Toxic blowpipe", "Dragon dart", "Twisted buckler", "Magic shortbow", "Amethyst arrow",
    # magic
    "Tumeken's shadow", "Ancestral hat", "Ancestral robe top", "Ancestral robe bottom", "Occult necklace",
    "Tormented bracelet", "Eternal boots", "Magus ring", "Imbued Saradomin cape", "Elidinis' ward (f)",
    "Kodai wand", "Harmonised Nightmare staff", "Blood rune", "Death rune", "Rune pouch",
    # movement
    "Graceful hood", "Graceful cape", "Graceful top", "Graceful legs", "Graceful gloves", "Graceful boots",
    "Graceful hood (Arceuus)", "Graceful top (Arceuus)", "Stamina potion", "Ring of dueling", "Amulet of glory",
    "Varrock teleport (tablet)", "Law rune", "Boots of lightness",
    # skills
    "Dragon axe", "Rune axe", "Crystal axe", "Lumberjack hat", "Lumberjack top", "Grimy ranarr weed", "Ranarr weed",
    "Ranarr potion (unf)", "Snape grass", "Prayer potion", "Logs", "Oak logs", "Knife", "Tinderbox", "Saw",
    "Crystal saw", "Amy's saw", "Hammer", "Silverlight", "Emberlight", "Guam leaf", "Eye of newt", "Irit leaf",
    "Rune crossbow", "Runite bolts", "Broad bolts", "Book of Law", "Ardougne cloak 4", "Cape of Legends",
    # blighted supplies
    "Blighted super restore", "Blighted anglerfish", "Blighted manta ray", "Blighted karambwan",
    "Blighted ancient ice sack", "Blighted vengeance sack", "Unpowered orb",
    # light sources: only the lit versions count
    "Mining helmet", "Kandarin headgear 1",
    # magic tab runes
    "Fire rune", "Water rune", "Cosmic rune", "Mind rune", "Mist rune",
    # skill tab equipment blocks
    "Penance gloves", "Spottier cape", "Agility cape", "Graceful cape (Arceuus)", "Ring of endurance",
    "Spirit angler headband", "Spirit angler top", "Angler hat", "Angler top", "Angler waders", "Angler boots",
    "Rogue mask", "Rogue gloves", "Gloves of silence", "Thieving cape", "Max cape",
]
STAT_FIELDS = ["stab_attack_bonus", "slash_attack_bonus", "crush_attack_bonus", "range_attack_bonus",
               "magic_attack_bonus", "strength_bonus", "ranged_strength_bonus", "magic_damage_bonus", "prayer_bonus"]
DEF_FIELDS = ["stab_defence_bonus", "slash_defence_bonus", "crush_defence_bonus", "range_defence_bonus",
              "magic_defence_bonus"]
SLOT = {"head": 0, "cape": 1, "neck": 2, "weapon": 3, "2h": 3, "body": 4, "shield": 5, "legs": 7, "hands": 9,
        "feet": 10, "ring": 12, "ammo": 13}


def main():
    rows = gd.cached("items.json", gd.fetch_items)
    bonuses = gd.cached("bonuses.json", lambda: wiki_recipes.fetch_all(
        "infobox_bonuses", ["page_name", "page_name_sub", "equipment_slot", "weapon_attack_speed"] + STAT_FIELDS +
        DEF_FIELDS))
    weights = gd.cached("weights.json", lambda: wiki_recipes.fetch_all(
        "infobox_item", ["page_name_sub", "weight"]))
    weight = {w["page_name_sub"]: float(w.get("weight") or 0) for w in weights}
    by_sub = {}
    for b in bonuses:
        by_sub.setdefault(b["page_name_sub"], b)
        by_sub.setdefault(b["page_name"], b)

    items, stats = {}, {}
    wanted = {n.lower(): n for n in ITEMS}
    for r in rows:
        name = wanted.get((r.get("page_name") or "").lower())
        if not name:
            continue
        sub = r.get("page_name_sub") or r["page_name"]
        b = by_sub.get(sub) or by_sub.get(r["page_name"])
        for raw in r.get("item_id") or []:
            try:
                item_id = int(raw)
            except ValueError:
                continue
            items.setdefault(name, []).append(item_id)
            g = (lambda k: float(b.get(k) or 0)) if b else (lambda k: 0.0)
            slot = SLOT.get(b.get("equipment_slot"), -1) if b else -1
            stats[str(item_id)] = [slot, int(g("weapon_attack_speed"))] + [g(k) for k in STAT_FIELDS] + \
                [sum(g(k) for k in DEF_FIELDS), weight.get(sub, 0.0)]
    missing = [n for n in ITEMS if n not in items]
    if missing:
        sys.exit("not found: %s" % missing)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({"items": items, "stats": stats}, indent=0), encoding="utf-8")
    print("wrote %s: %d items, %d ids" % (OUT, len(items), len(stats)))


if __name__ == "__main__":
    main()
