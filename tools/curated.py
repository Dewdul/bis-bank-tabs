"""Hand-curated tab definitions for Ultimate Bank Tabs.

Names are OSRS Wiki *page* names (case-insensitive). A page covers every
version of an item (charges, doses, charged/uncharged), so "Amulet of glory"
matches glory(1)..(6) and the uncharged amulet. A name starting with "re:" is a
regular expression matched against page names; every page it matches forms one
entry (same priority).

generate_data.py resolves these to item ids, fails on unknown names, and writes
src/main/resources/com/ultimatebanktabs/tabs.json.

Pick modes (see PickResolver.java for the runtime side):
  first  - the highest-priority owned entry (all owned ids of that entry)
  all    - every owned entry, in order
  auto   - best owned item for an equipment slot by a stat profile
           (melee / ranged / magic / weight), optionally preferring entries
  weapon - best owned melee weapon by a damage model, for one attack style
  set    - a group of slot pieces; "complete" sets need every piece owned
  ammo   - best owned ammo for the launcher chosen by another pick
"""
import re

SLOTS = ("HEAD", "CAPE", "AMULET", "AMMO", "WEAPON", "BODY", "SHIELD", "LEGS", "GLOVES", "BOOTS", "RING")


def first(key, entries, slot=None, **kw):
    return dict(kw, key=key, mode="first", slot=slot, entries=list(entries))


def all_(key, entries, slot=None, **kw):
    return dict(kw, key=key, mode="all", slot=slot, entries=list(entries))


def auto(key, slot, profile, prefer=(), **kw):
    return dict(kw, key=key, mode="auto", slot=slot, profile=profile, entries=list(prefer))


def weapon(key, style, prefer=(), **kw):
    return dict(kw, key=key, mode="weapon", slot="WEAPON", style=style, entries=list(prefer))


def ammo(key, launcher_pick, **kw):
    return dict(kw, key=key, mode="ammo", slot="AMMO", launcher=launcher_pick, entries=[])


def set_(key, pieces, complete=False, **kw):
    """pieces: list of (slot, [entries]) - each piece is a 'first' over its entries."""
    return dict(kw, key=key, mode="set", complete=complete,
                pieces=[dict(slot=s, entries=list(e)) for s, e in pieces])


def section(key, title, layout, picks):
    return dict(key=key, title=title, layout=layout, picks=picks)


def tab(key, name, icon, sections, kind="skill"):
    return dict(key=key, name=name, icon=icon, kind=kind, sections=sections)


def tiers(base, n=4, start=1):
    """'Ardougne cloak' -> ['Ardougne cloak 4', ..., 'Ardougne cloak 1']"""
    return ["%s %d" % (base, i) for i in range(n + start - 1, start - 1, -1)]


def outfit(key, slots_entries, **kw):
    """A skilling outfit: one 'first' per slot, any pieces owned."""
    return set_(key, slots_entries, **kw)


# ---------------------------------------------------------------- shared lists

IMBUED_SLAYER_HELMS = ["Slayer helmet (i)", "re:^[A-Z][a-z]+ slayer helmet \\(i\\)$"]
SLAYER_HELMS = ["Slayer helmet", "re:^[A-Z][a-z]+ slayer helmet$"]

# Best healing first (wiki Food/All_food). Raid- and boss-only foods are left out.
FOOD = [("Marlin", "Heals 24"), ("Cooked moonlight antelope", "Heals 14, then 12 more after 3 seconds"),
        ("Anglerfish", "Heals 22 and can overheal"), ("Manta ray", "Heals 22"), ("Dark crab", "Heals 22"),
        ("Bluefin", "Heals 22"), ("Tuna potato", "Heals 22"),
        ("Cooked dashing kebbit", "Heals 13, then 10 more after 3 seconds"), ("Sea turtle", "Heals 21"),
        ("Cooked sunlight antelope", "Heals 12, then 9 more after 3 seconds"), ("Shark", "Heals 20"),
        ("Halibut", "Heals 20"), ("Mushroom potato", "Heals 20"), ("Pineapple pizza", "Heals 11 per bite, 2 bites"),
        ("Yellowfin", "Heals 19"), ("Curry", "Heals 19"), ("Cooked pyre fox", "Heals 11, then 8 more after 3 seconds"),
        ("Haddock", "Heals 18"), ("Monkfish", "Heals 16"), ("Swordfish", "Heals 14"), ("Bass", "Heals 13"),
        ("Lobster", "Heals 12"), ("Tuna", "Heals 10"), ("Salmon", "Heals 9"), ("Trout", "Heals 7")]

ANTIPOISON = ["Extended anti-venom+", "Anti-venom+", "Anti-venom", "Antidote++", "Antidote+",
              "Superantipoison", "Antipoison"]
ANTIFIRE = ["Extended super antifire", "Super antifire potion", "Extended antifire", "Antifire potion"]
RESTORE = ["Super restore", "Prayer potion"]

ELEMENTAL_RUNES = ["Fire rune", "Water rune", "Air rune", "Earth rune"]
COMBO_RUNES = ["Smoke rune", "Steam rune", "Mist rune", "Dust rune", "Mud rune", "Lava rune"]
RUNE_POUCH = ["Divine rune pouch", "Rune pouch"]


def combat_supplies(boosts):
    return section("supplies", "Supplies", "rows", boosts + [
        first("restore", RESTORE),
        first("brew", ["Saradomin brew"]),
        # how many foods comes from the "Food items" setting
        first("food", FOOD, take=4),
        first("karambwan", [("Cooked karambwan", "Combo food: eat in the same tick as other food (heals 18)")]),
        first("antipoison", ANTIPOISON),
        first("antifire", ANTIFIRE),
    ])


def situational_common(imbued_only):
    helms = IMBUED_SLAYER_HELMS + ([] if imbued_only else SLAYER_HELMS) + \
        ["Black mask (i)"] + ([] if imbued_only else ["Black mask"])
    salve = ["Salve amulet(ei)", "Salve amulet (e)", "Salve amulet(i)", "Salve amulet"] if not imbued_only \
        else ["Salve amulet(ei)", "Salve amulet(i)"]
    return [first("slayer_helm", helms, slot="HEAD"), first("salve", salve, slot="AMULET")]


VOID_TOP = ["Elite void top", "Void knight top"]
VOID_ROBE = ["Elite void robe", "Void knight robe"]

# ---------------------------------------------------------------- melee

MELEE_MAIN_WEAPONS = []  # melee main weapon is chosen by the damage model

MELEE = tab("melee", "Melee", "Abyssal whip", kind="gear", sections=[
    section("main", "Best in slot", "equipment", [
        auto("head", "HEAD", "melee"),
        auto("cape", "CAPE", "melee"),
        auto("neck", "AMULET", "melee"),
        auto("ammo", "AMMO", "melee"),
        weapon("weapon", "any"),
        auto("body", "BODY", "melee"),
        auto("shield", "SHIELD", "melee"),
        auto("legs", "LEGS", "melee"),
        auto("hands", "GLOVES", "melee"),
        auto("feet", "BOOTS", "melee"),
        auto("ring", "RING", "melee"),
    ]),
    section("situational", "Situational", "alts", situational_common(False) + [
        first("blood_fury", ["Amulet of blood fury"], slot="AMULET"),
        set_("inquisitor", [("HEAD", ["Inquisitor's great helm"]), ("BODY", ["Inquisitor's hauberk"]),
                            ("LEGS", ["Inquisitor's plateskirt"])]),
        set_("void", [("HEAD", ["Void melee helm"]), ("BODY", VOID_TOP), ("LEGS", VOID_ROBE),
                      ("GLOVES", ["Void knight gloves"])], complete=True),
        set_("blood_moon", [("HEAD", ["Blood Moon helm"]), ("BODY", ["Blood Moon chestplate"]),
                            ("LEGS", ["Blood Moon tassets"])], requires=["Dual macuahuitl"]),
        set_("obsidian", [("HEAD", ["Obsidian helmet"]), ("BODY", ["Obsidian platebody"]),
                          ("LEGS", ["Obsidian platelegs"]), ("AMULET", ["Berserker necklace"])],
             requires=["Tzhaar-ket-om", "Toktz-xil-ak", "Toktz-xil-ek", "Tzhaar-ket-em"]),
        first("lightbearer", ["Lightbearer"], slot="RING"),
    ]),
    section("weapons", "Weapons", "rows", [
        weapon("stab", "stab"),
        weapon("slash", "slash"),
        weapon("crush", "crush"),
        first("dragonbane", ["Dragon hunter lance"]),
        first("demonbane", ["Emberlight", "Arclight", "Darklight", "Silverlight"]),
        first("kalphite", ["Keris partisan of breaching", "Keris partisan of the sun", "Keris partisan of corruption",
                           "Keris partisan of amascut", "Keris partisan", "Keris"]),
        first("vampyre", ["Blisterwood flail", "Ivandis flail", "Blisterwood sickle", "Wolfbane"]),
        first("leafbladed", ["Leaf-bladed battleaxe", "Leaf-bladed sword", "Leaf-bladed spear"]),
        first("wilderness", ["Ursine chainmace", "Viggora's chainmace"]),
        first("dharok", ["Dharok's greataxe"], requires=["Dharok's helm"]),
    ]),
    section("specials", "Special attacks", "rows", [
        all_("specs", ["Dragon claws", "Burning claws", "Voidwaker", "Dragon warhammer", "Elder maul",
                       "Bandos godsword", "Armadyl godsword", "Ancient godsword", "Saradomin godsword",
                       "Zamorak godsword", "Crystal halberd", "Granite maul", "Dragon dagger", "Abyssal dagger",
                       "Dragon longsword", "Dragon mace", "Barrelchest anchor", "Bone dagger", "Crimson kisten", "Dragon halberd",
                       "Dragon scimitar", "Saradomin sword", "Saradomin's blessed sword"]),
    ]),
    combat_supplies([
        first("boost", ["Divine super combat potion", "Super combat potion", "Combat potion"]),
        first("boost_str", ["Divine super strength potion", "Super strength", "Strength potion"],
              unless=["boost"]),
        first("boost_att", ["Divine super attack potion", "Super attack", "Attack potion"], unless=["boost"]),
    ]),
])

# ---------------------------------------------------------------- ranged

BOWS = ["Twisted bow", "Dark bow", "Corrupted dark bow", "Magic shortbow (i)", "Magic shortbow", "Magic longbow",
        "Magic comp bow", "Yew shortbow", "Yew longbow", "Yew comp bow", "Maple shortbow", "Maple longbow",
        "Willow shortbow", "Willow longbow", "Oak shortbow", "Oak longbow", "Shortbow", "Longbow", "Seercull",
        "3rd Age bow", "Scorching bow", "Venator bow", "Webweaver bow", "Craw's bow"]
CROSSBOWS = ["Zaryte crossbow", "Armadyl crossbow", "Dragon hunter crossbow", "Dragon crossbow", "Rune crossbow",
             "Karil's crossbow", "Adamant crossbow", "Mithril crossbow", "Steel crossbow", "Iron crossbow",
             "Bronze crossbow", "Blurite crossbow", "Dorgeshuun crossbow", "Hunters' crossbow"]

ARROWS = ["Dragon arrow", "Amethyst arrow", "Rune arrow", "Adamant arrow", "Mithril arrow", "Steel arrow",
          "Iron arrow", "Bronze arrow"]
BOLTS = ["Ruby dragon bolts (e)", "Diamond dragon bolts (e)", "Dragonstone dragon bolts (e)", "Onyx dragon bolts (e)",
         "Ruby bolts (e)", "Diamond bolts (e)", "Dragonstone bolts (e)", "Onyx bolts (e)", "Dragon bolts",
         "Runite bolts", "Broad bolts", "Adamant bolts", "Mithril bolts", "Steel bolts", "Iron bolts", "Bronze bolts"]
JAVELINS = ["Dragon javelin", "Amethyst javelin", "Rune javelin", "Adamant javelin", "Mithril javelin"]
DARTS = ["Dragon dart", "Amethyst dart", "Rune dart", "Adamant dart", "Mithril dart", "Black dart", "Steel dart"]

# Ordered best-first for general PvM. Launchers carry their ammo class so the
# diagram's ammo slot follows whichever launcher wins.
RANGED_WEAPONS = [
    ("Twisted bow", "arrow"),
    ("re:^Bow of Faerdhinen(?: \\(c\\)(?: \\((?:Amlodd|Cadarn|Crwys|Iorwerth|Ithell|Meilyr|Trahaearn)\\))?)?$", None),
    ("Zaryte crossbow", "bolt"),
    ("Toxic blowpipe", None),
    ("Blazing blowpipe", None),
    ("Armadyl crossbow", "bolt"),
    ("Tonalztics of Ralos", None),
    ("Dragon hunter crossbow", "bolt"),
    ("Dragon crossbow", "bolt"),
    ("Magic shortbow (i)", "arrow"),
    ("Rune crossbow", "bolt"),
    ("Eclipse atlatl", "atlatl"),
    ("Hunters' sunlight crossbow", "antler"),
    ("Crystal bow", None),
    ("Karil's crossbow", "bolt"),
    ("Magic shortbow", "arrow"),
    ("Heavy ballista", "javelin"),
    ("Light ballista", "javelin"),
    ("Magic longbow", "arrow"),
    ("Adamant crossbow", "bolt"),
    ("Yew shortbow", "arrow"),
    ("Mithril crossbow", "bolt"),
    ("Maple shortbow", "arrow"),
    ("Steel crossbow", "bolt"),
    ("Willow shortbow", "arrow"),
    ("Oak shortbow", "arrow"),
    ("Shortbow", "arrow"),
]

AMMO_CLASSES = {
    "arrow": ARROWS,
    "bolt": BOLTS,
    "javelin": JAVELINS,
    "atlatl": ["Atlatl dart"],
    "antler": ["Sunlight antler bolts", "Moonlight antler bolts"],
}

RANGED = tab("ranged", "Ranged", "Magic shortbow", kind="gear", sections=[
    section("main", "Best in slot", "equipment", [
        auto("head", "HEAD", "ranged"),
        auto("cape", "CAPE", "ranged"),
        auto("neck", "AMULET", "ranged"),
        ammo("ammo", "weapon"),
        dict(first("weapon", [w for w, _ in RANGED_WEAPONS], slot="WEAPON"), fallback="ranged"),
        auto("body", "BODY", "ranged"),
        auto("shield", "SHIELD", "ranged"),
        auto("legs", "LEGS", "ranged"),
        auto("hands", "GLOVES", "ranged"),
        auto("feet", "BOOTS", "ranged"),
        auto("ring", "RING", "ranged"),
    ]),
    section("situational", "Situational", "alts", situational_common(True) + [
        set_("crystal", [("HEAD", ["Crystal helm"]), ("BODY", ["Crystal body"]), ("LEGS", ["Crystal legs"])],
             requires=["re:^Bow of Faerdhinen", "Crystal bow"]),
        set_("void", [("HEAD", ["Void ranger helm"]), ("BODY", VOID_TOP), ("LEGS", VOID_ROBE),
                      ("GLOVES", ["Void knight gloves"])], complete=True),
        set_("eclipse_moon", [("HEAD", ["Eclipse Moon helm"]), ("BODY", ["Eclipse Moon chestplate"]),
                              ("LEGS", ["Eclipse Moon tassets"])], requires=["Eclipse atlatl"]),
    ]),
    section("weapons", "Weapons", "rows", [
        first("dragonbane", ["Dragon hunter crossbow"]),
        first("demonbane", ["Scorching bow"]),
        first("blowpipe", ["Blazing blowpipe", "Toxic blowpipe"]),
        first("multi", ["Venator bow", "Black chinchompa", "Red chinchompa", "Chinchompa"]),
        first("wilderness", ["Webweaver bow", "Craw's bow"]),
        first("karil", ["Karil's crossbow"], requires=["Karil's coif"]),
    ]),
    section("specials", "Special attacks", "rows", [
        all_("specs", ["Zaryte crossbow", "Dragon thrownaxe", "Heavy ballista", "Light ballista", "Dragon knife",
                       "Dark bow", "Corrupted dark bow", "Magic shortbow (i)", "Magic shortbow", "Armadyl crossbow",
                       "Dorgeshuun crossbow", "Rune thrownaxe",
                       "Seercull", "Magic longbow", "Magic comp bow"]),
    ]),
    section("ammo", "Ammunition", "rows", [
        first("arrows", ARROWS, requires=BOWS, placed=True),
        first("bolts_ruby", ["Ruby dragon bolts (e)", "Ruby bolts (e)"], requires=CROSSBOWS, placed=True),
        first("bolts_diamond", ["Diamond dragon bolts (e)", "Diamond bolts (e)"], requires=CROSSBOWS, placed=True),
        first("bolts_dragonstone", ["Dragonstone dragon bolts (e)", "Dragonstone bolts (e)"], requires=CROSSBOWS, placed=True),
        first("bolts_onyx", ["Onyx dragon bolts (e)", "Onyx bolts (e)"], requires=CROSSBOWS, placed=True),
        first("bolts", ["Dragon bolts", "Runite bolts", "Adamant bolts", "Mithril bolts", "Steel bolts",
                        "Iron bolts", "Bronze bolts"], requires=CROSSBOWS, placed=True),
        first("broad_bolts", ["Amethyst broad bolts", "Broad bolts"], requires=CROSSBOWS, placed=True),
        first("javelins", JAVELINS, requires=["Heavy ballista", "Light ballista"], placed=True),
        first("darts", DARTS, requires=["Toxic blowpipe", "Blazing blowpipe"], placed=True),
        first("atlatl_darts", ["Atlatl dart"], requires=["Eclipse atlatl"], placed=True),
        first("antler_bolts", ["Sunlight antler bolts", "Moonlight antler bolts"],
              requires=["Hunters' sunlight crossbow"], placed=True),
    ]),
    combat_supplies([
        first("boost", ["Divine bastion potion", "Divine ranging potion", "Bastion potion", "Ranging potion"]),
    ]),
])

# ---------------------------------------------------------------- magic

POWERED_STAVES = ["Tumeken's shadow", "Eye of Ayak", "Holy sanguinesti staff", "Sanguinesti staff",
                  "Trident of the Swamp (e) (o)", "Trident of the Swamp (e)", "Trident of the Swamp (o)",
                  "Trident of the Swamp", "Warped sceptre", "Trident of the Seas (e) (o)", "Trident of the Seas (e)",
                  "Trident of the Seas (o)", "Trident of the Seas"]
ANCIENT_STAVES = ["Blood ancient sceptre", "Ice ancient sceptre", "Smoke ancient sceptre", "Shadow ancient sceptre",
                  "Ancient sceptre", "Kodai wand", "Master wand", "Ancient staff"]
STANDARD_STAVES = ["Harmonised Nightmare staff", "Twinflame staff", "Kodai wand", "Nightmare staff",
                   "Toxic Staff of the Dead", "Staff of the Dead", "Staff of Light", "Staff of Balance",
                   "Master wand", "Ahrim's staff", "Mystic smoke staff", "Mystic steam staff", "Mystic lava staff",
                   "Mystic dust staff", "Mystic mud staff", "Mystic mist staff", "Iban's staff", "Slayer's staff (e)",
                   "Mystic fire staff", "Mystic water staff", "Mystic air staff", "Mystic earth staff",
                   "Smoke battlestaff", "Steam battlestaff", "Lava battlestaff", "Fire battlestaff",
                   "Water battlestaff", "Air battlestaff", "Earth battlestaff", "Staff of fire", "Staff of water",
                   "Staff of air", "Staff of earth"]

MAGIC = tab("magic", "Magic", "Mystic air staff", kind="gear", sections=[
    section("main", "Best in slot", "equipment", [
        auto("head", "HEAD", "magic"),
        auto("cape", "CAPE", "magic"),
        auto("neck", "AMULET", "magic"),
        auto("ammo", "AMMO", "magic"),
        dict(first("weapon", POWERED_STAVES + ANCIENT_STAVES + STANDARD_STAVES, slot="WEAPON"), fallback="magic"),
        auto("body", "BODY", "magic"),
        auto("shield", "SHIELD", "magic"),
        auto("legs", "LEGS", "magic"),
        auto("hands", "GLOVES", "magic"),
        auto("feet", "BOOTS", "magic"),
        auto("ring", "RING", "magic"),
    ]),
    section("situational", "Situational", "alts", situational_common(True) + [
        set_("virtus", [("HEAD", ["Virtus mask"]), ("BODY", ["Virtus robe top"]), ("LEGS", ["Virtus robe bottom"])]),
        set_("void", [("HEAD", ["Void mage helm"]), ("BODY", VOID_TOP), ("LEGS", VOID_ROBE),
                      ("GLOVES", ["Void knight gloves"])], complete=True),
        set_("blue_moon", [("HEAD", ["Blue Moon helm"]), ("BODY", ["Blue Moon chestplate"]),
                           ("LEGS", ["Blue Moon tassets"])], requires=["Blue Moon spear"]),
        all_("tomes", ["Tome of Fire", "Tome of Water", "Tome of Earth"], slot="SHIELD"),
    ]),
    section("weapons", "Weapons", "rows", [
        first("ancients", ANCIENT_STAVES),
        first("standard", STANDARD_STAVES),
        first("demonbane", ["Purging staff"]),
        first("dragonbane", ["Dragon hunter wand"]),
        first("wilderness", ["Accursed sceptre", "Thammaron's sceptre"]),
        first("blue_moon_spear", ["Blue Moon spear"]),
        first("ahrim", ["Ahrim's staff"], requires=["Ahrim's hood"]),
    ]),
    section("specials", "Special attacks", "rows", [
        all_("specs", ["Volatile Nightmare staff", "Eldritch Nightmare staff", "Accursed sceptre"]),
    ]),
    section("runes", "Runes", "rows", [
        first("pouch", RUNE_POUCH),
        all_("runes", ["Wrath rune", "Death rune", "Blood rune", "Soul rune", "Chaos rune", "Sunfire rune",
                       "Aether rune"] + ELEMENTAL_RUNES + COMBO_RUNES + ["Mind rune"]),
    ]),
    combat_supplies([
        first("boost", ["Divine magic potion", "Magic potion", "Divine battlemage potion", "Battlemage potion",
                        "Forgotten brew", "Ancient brew"]),
        first("heart", ["Saturated heart", "Imbued heart"]),
    ]),
])

# ---------------------------------------------------------------- movement

GRACEFUL_SETS = ["", " (Arceuus)", " (Hosidius)", " (Lovakengj)", " (Piscarilius)", " (Shayzien)", " (Kourend)",
                 " (Hallowed)", " (Agility Arena)", " (Varlamore)", " (Adventurer)"]
GRACEFUL_PIECES = [("HEAD", "Graceful hood"), ("CAPE", "Graceful cape"), ("BODY", "Graceful top"),
                   ("LEGS", "Graceful legs"), ("GLOVES", "Graceful gloves"), ("BOOTS", "Graceful boots")]


def graceful(key):
    """One complete-as-possible graceful outfit: the colour with the most pieces wins;
    empty slots fall back to the lightest weight-reducing item."""
    return dict(key=key, mode="outfit", profile="weight",
                variants=[[dict(slot=s, entries=[n + c]) for s, n in GRACEFUL_PIECES] for c in GRACEFUL_SETS])


ENERGY = ["Stamina potion", "Super energy", "Energy potion"]

TELEPORT_TABLETS = [
    "re:(?i)^(?!Teleport anchoring|Target teleport|Crystal teleport|Corrupted teleport|Ancient teleporter|"
    "Teleport crystal|Eternal teleport|Enhanced crystal|Blighted teleport|Teleport card|"
    "Trailblazer|Annihilation|Armageddon|Shattered|Speedy|Twisted|Chasm)"
    ".*(?: teleport(?: \\(tablet\\))?| teleport scroll|^Teleport to (?:house|boat) \\(tablet\\))$",
    "Master scroll book",
]

MOVEMENT = tab("movement", "Movement", "Graceful boots", kind="movement", sections=[
    section("run", "Run energy", "rows", [
        graceful("graceful"),
        first("energy", ENERGY),
        first("endurance", ["Ring of endurance"]),
    ]),
    section("jewellery", "Teleport jewellery", "rows", [
        first("glory", ["Amulet of eternal glory", "Amulet of glory (t)", "Amulet of glory"]),
        all_("jewellery", ["Ring of dueling", "Games necklace", "Skills necklace", "Combat bracelet"]),
        first("wealth", ["Ring of wealth (i)", "Ring of wealth"]),
        first("slayer_ring", ["Slayer ring (eternal)", "Slayer ring"]),
        all_("jewellery2", ["Necklace of passage", "Digsite pendant", "Burning amulet", "Ring of returning",
                            "Ring of the elements", "Ring of shadows", "Giantsoul amulet", "Pendant of Ates",
                            "Amulet of the Eye", "Camulet", "Xeric's talisman", "Drakan's medallion"]),
    ]),
    section("diary", "Diary rewards", "rows", [
        first("ardy", tiers("Ardougne cloak")),
        first("desert", tiers("Desert amulet")),
        first("explorer", tiers("Explorer's ring")),
        first("karamja", tiers("Karamja gloves")),
        first("fremennik", tiers("Fremennik sea boots")),
        first("kandarin", tiers("Kandarin headgear")),
        first("morytania", tiers("Morytania legs")),
        first("western", tiers("Western banner")),
        first("wilderness", tiers("Wilderness sword")),
        first("kourend", tiers("Rada's blessing")),
        first("ghommal", tiers("Ghommal's hilt", 6)),
    ]),
    section("items", "Teleport items", "rows", [
        first("fairy", ["Lunar staff", "Dramen staff"]),
        all_("items", ["Ectophial", "Royal seed pod", "Pharaoh's sceptre", "Chronicle", "Enchanted lyre",
                       "Icy basalt", "Stony basalt", "Mythical cape"]),
        first("memoirs", ["Book of the Dead", "Kharedst's memoirs"]),
        first("crystal", ["Eternal teleport crystal", "Teleport crystal"]),
        first("skull", ["Skull sceptre (i)", "Skull sceptre"]),
        first("quetzal", ["Perfected quetzal whistle(i)", "Perfected quetzal whistle", "Enhanced quetzal whistle",
                          "Basic quetzal whistle"]),
        first("max_cape", ["Max cape"]),
        all_("capes", ["Achievement diary cape", "Quest point cape", "Music cape", "Construct. cape",
                       "Crafting cape", "Farming cape", "Fishing cape", "Hunter cape", "Strength cape"]),
    ]),
    section("tablets", "Tablets & scrolls", "rows", [
        all_("tablets", TELEPORT_TABLETS),
    ]),
    section("runes", "Teleport runes", "rows", [
        first("pouch", RUNE_POUCH),
        # the basic teleport runes in the order players keep them: law, fire, water, air, earth
        all_("runes", ["Law rune", "Fire rune", "Water rune", "Air rune", "Earth rune", "Astral rune", "Soul rune",
                       "Blood rune", "Dust rune", "Smoke rune", "Mist rune", "Lava rune", "Mud rune", "Steam rune"]),
    ]),
])

# ---------------------------------------------------------------- skills


# The everyday saw comes first; the crystal saw's +3 boost burns charges that
# need recharging, so it is a situational pick for builds just above your level.
SAWS = [
    first("saw", [("Amy's saw", "Everyday saw: equipable, nothing to recharge"),
                  ("Saw", "Everyday saw: nothing to recharge")]),
    first("crystal_saw", [("Crystal saw", "+3 invisible Construction boost when you need it (uses charges)")]),
]


def cape(skill_cape):
    return first("cape", [skill_cape])


PRAYER = tab("prayer", "Prayer", "Dragon bones", sections=[
    section("gear", "Gear", "rows", [
        first("bonecrusher", ["Bonecrusher necklace", "Bonecrusher"]),
        first("sanctifier", ["Ash sanctifier"]),
        first("dragonbone", ["Dragonbone necklace"]),
        first("wrench", ["Holy wrench"]),
        cape("Prayer cape"),
        outfit("zealot", [("HEAD", ["Zealot's helm"]), ("BODY", ["Zealot's robe top"]),
                          ("LEGS", ["Zealot's robe bottom"]), ("BOOTS", ["Zealot's boots"])]),
    ]),
    section("bones", "Bones", "rows", [
        # by Prayer experience, highest first
        all_("bones", ["Superior dragon bones", "Ourg bones", "Dagannoth bones", "Hydra bones",
                       "Frost dragon bones", "Raurg bones", "Lava dragon bones", "Fayrg bones", "Drake bones",
                       "Dragon bones", "Wyvern bones", "Strykewyrm bones", "Wyrm bones", "Babydragon bones",
                       "Shaikahan bones", "Zogre bones", "Wyrmling bones", "Jogre bones", "Big bones", "Bat bones",
                       "Monkey bones", "Wolf bones", "Bones"]),
        all_("blessed", ["re:^Blessed [\\w' -]*bones$", "Sun-kissed bones"]),
    ]),
    section("ashes", "Ashes", "rows", [
        all_("ashes", ["Infernal ashes", "Abyssal ashes", "Malicious ashes", "Vile ashes", "Fiendish ashes"]),
    ]),
    section("heads", "Ensouled heads", "rows", [
        all_("heads", ["re:^Ensouled [\\w' -]+ head$"]),
    ]),
])

SLAYER = tab("slayer", "Slayer", "Enchanted gem", sections=[
    section("gear", "Slayer gear", "rows", [
        first("helm", IMBUED_SLAYER_HELMS + SLAYER_HELMS + ["Black mask (i)", "Black mask"]),
        first("gem", ["Eternal gem", "Enchanted gem"]),
        first("ring", ["Slayer ring (eternal)", "Slayer ring"]),
        all_("bracelets", ["Bracelet of slaughter", "Expeditious bracelet"]),
        cape("Slayer cape"),
        first("herb_sack", ["Silklined herb sack", "Herb sack"]),
        first("gem_bag", ["Gem bag"]),
        first("looting_bag", ["Looting bag"]),
        first("bonecrusher", ["Bonecrusher necklace", "Bonecrusher"]),
        first("sanctifier", ["Ash sanctifier"]),
    ]),
    section("protection", "Task items", "rows", [
        first("earmuffs", ["Earmuffs"], unless=["helm"]),
        first("nose_peg", ["Nose peg"], unless=["helm"]),
        first("face_mask", ["Facemask", "Gas mask"], unless=["helm"]),
        first("spiny", ["Spiny helmet"], unless=["helm"]),
        first("goggles", ["Reinforced goggles"], unless=["helm"]),
        first("mirror", ["V's shield", "Mirror shield"]),
        first("insulated", ["Insulated boots"]),
        first("wyvern_boots", ["Boots of brimstone", "Boots of stone", "Granite boots"]),
        first("witchwood", ["Witchwood icon"]),
        first("slayer_gloves", ["Slayer gloves"]),
        first("rock_hammer", ["Rock thrownhammer", "Granite hammer", "Rock hammer"]),
        first("salt", ["Bag of salt"]),
        first("ice", ["Ice cooler"]),
        first("fungicide", ["Fungicide spray"]),
        first("fungicide_refill", ["Fungicide"]),
        first("lantern", ["Bug lantern"]),
        first("explosive", ["Fishing explosive"]),
        first("rope", ["Rope"]),
        first("leafbladed", ["Leaf-bladed battleaxe", "Leaf-bladed sword", "Leaf-bladed spear"]),
        first("broad", ["Amethyst broad bolts", "Broad bolts", "Broad arrows"]),
    ]),
    section("cannon", "Cannon", "rows", [
        first("cannon_base", ["Cannon base (or)", "Cannon base"]),
        first("cannon_stand", ["Cannon stand (or)", "Cannon stand"]),
        first("cannon_barrels", ["Cannon barrels (or)", "Cannon barrels"]),
        first("cannon_furnace", ["Cannon furnace (or)", "Cannon furnace"]),
        first("cannonball", ["Granite cannonball", "Steel cannonball"]),
    ]),
])

WOODCUTTING = tab("woodcutting", "Woodcutting", "Rune axe", sections=[
    section("gear", "Tools", "rows", [
        first("axe", ["Crystal axe", "Infernal axe", "Dragon axe", "3rd Age axe", "Rune axe", "Gilded axe",
                      "Adamant axe", "Mithril axe", "Black axe", "Steel axe", "Iron axe", "Bronze axe"]),
        first("felling", ["Crystal felling axe", "3rd Age felling axe", "Dragon felling axe", "Rune felling axe",
                          "Adamant felling axe", "Mithril felling axe", "Black felling axe", "Steel felling axe",
                          "Iron felling axe", "Bronze felling axe"]),
        first("kit", ["Forestry kit"]),
        first("basket", ["Forestry basket", "Log basket"]),
        first("brace", ["Log brace"]),
        cape("Woodcutting cape"),
    ]),
    section("outfit", "Outfit", "rows", [
        outfit("lumberjack", [("HEAD", ["Forestry hat", "Lumberjack hat"]), ("BODY", ["Forestry top", "Lumberjack top"]),
                              ("LEGS", ["Forestry legs", "Lumberjack legs"]),
                              ("BOOTS", ["Forestry boots", "Lumberjack boots"])]),
    ]),
])

MINING = tab("mining", "Mining", "Rune pickaxe", sections=[
    section("gear", "Tools", "rows", [
        first("pickaxe", ["Crystal pickaxe", "Infernal pickaxe (or)", "Infernal pickaxe", "Dragon pickaxe (or)",
                          "Dragon pickaxe (upgraded)", "Dragon pickaxe", "3rd Age pickaxe", "Gilded pickaxe",
                          "Rune pickaxe", "Adamant pickaxe", "Mithril pickaxe", "Black pickaxe", "Steel pickaxe",
                          "Iron pickaxe", "Bronze pickaxe"]),
        first("gloves", ["Expert mining gloves", "Superior mining gloves", "Mining gloves"]),
        first("ring", ["Celestial signet", "Celestial ring"]),
        first("varrock", tiers("Varrock armour")),
        first("gem_bag", ["Gem bag"]),
        first("coal_bag", ["Coal bag"]),
        first("hammer", ["Imcando hammer", "Hammer"], why="Motherlode Mine: repairs the broken water wheel struts"),
        cape("Mining cape"),
    ]),
    section("outfit", "Outfit", "rows", [
        outfit("prospector", [("HEAD", ["Golden prospector helmet", "Prospector helmet"]),
                              ("BODY", ["Golden prospector jacket", "Prospector jacket"]),
                              ("LEGS", ["Golden prospector legs", "Prospector legs"]),
                              ("BOOTS", ["Golden prospector boots", "Prospector boots"])]),
    ]),
])

FISHING = tab("fishing", "Fishing", "Lobster pot", sections=[
    section("gear", "Tools", "rows", [
        first("harpoon", ["Crystal harpoon", "Infernal harpoon (or)", "Infernal harpoon", "Dragon harpoon (or)",
                          "Dragon harpoon", "Barb-tail harpoon", "Harpoon"]),
        first("rod", ["Pearl fishing rod", "Fishing rod"]),
        first("fly_rod", ["Pearl fly fishing rod", "Fly fishing rod"]),
        first("barbarian_rod", ["Pearl barbarian rod", "Barbarian rod"]),
        first("oily_rod", ["Oily pearl fishing rod", "Oily fishing rod"]),
        first("small_net", ["Small fishing net"]),
        first("big_net", ["Big fishing net"]),
        first("lobster_pot", ["Lobster pot"]),
        first("vessel", ["Karambwan vessel"]),
        first("barrel", ["Fish barrel"]),
        first("tackle", ["Tackle box"]),
        cape("Fishing cape"),
    ]),
    section("outfit", "Outfit", "rows", [
        outfit("angler", [("HEAD", ["Spirit angler headband", "Angler hat"]), ("BODY", ["Spirit angler top", "Angler top"]),
                          ("LEGS", ["Spirit angler waders", "Angler waders"]),
                          ("BOOTS", ["Spirit angler boots", "Angler boots"])]),
    ]),
    section("bait", "Bait", "rows", [
        all_("bait", ["Feather", "Fishing bait", "Sandworms", "Dark fishing bait", "Raw karambwanji",
                      "Fish offcuts", "Spirit flakes", "Stripy feather"]),
    ]),
])

HUNTER = tab("hunter", "Hunter", "Box trap", sections=[
    section("gear", "Traps & tools", "rows", [
        first("box_trap", ["Box trap"]),
        first("bird_snare", ["Bird snare"]),
        first("rabbit_snare", ["Rabbit snare"]),
        first("net", ["Magic butterfly net", "Butterfly net"]),
        first("noose", ["Noose wand"]),
        first("teasing", ["Teasing stick"]),
        first("rope", ["Rope"]),
        first("small_net", ["Small fishing net"]),
        first("knife", ["Knife"]),
        first("tinderbox", ["Tinderbox"]),
        first("impling_jar", ["Impling jar"]),
        first("butterfly_jar", ["Butterfly jar"]),
        first("kit", ["Huntsman's kit"]),
        first("crossbow", ["Hunters' sunlight crossbow", "Hunters' crossbow"]),
        first("spear", ["Hunter's spear"]),
        cape("Hunter cape"),
    ]),
    section("outfit", "Outfit", "rows", [
        outfit("guild", [("HEAD", ["Guild hunter headwear"]), ("BODY", ["Guild hunter top"]),
                         ("LEGS", ["Guild hunter legs"]), ("BOOTS", ["Guild hunter boots"])]),
    ]),
])

THIEVING = tab("thieving", "Thieving", "Lockpick", sections=[
    section("gear", "Gear", "rows", [
        first("dodgy", ["Dodgy necklace"]),
        first("silence", ["Gloves of silence"]),
        first("lockpick", ["Lockpick"]),
        first("stethoscope", ["Stethoscope"]),
        first("blackjack", ["Maple blackjack(d)", "Maple blackjack(o)", "Maple blackjack", "Willow blackjack(d)",
                            "Willow blackjack(o)", "Willow blackjack", "Oak blackjack(d)", "Oak blackjack(o)",
                            "Oak blackjack"]),
        first("ardy", tiers("Ardougne cloak")),
        cape("Thieving cape"),
    ]),
    section("outfit", "Outfit", "rows", [
        outfit("rogue", [("HEAD", ["Rogue mask"]), ("BODY", ["Rogue top"]), ("LEGS", ["Rogue trousers"]),
                         ("GLOVES", ["Rogue gloves"]), ("BOOTS", ["Rogue boots"])]),
    ]),
])

AGILITY = tab("agility", "Agility", "Graceful hood", sections=[
    section("gear", "Gear", "rows", [
        graceful("graceful"),
        cape("Agility cape"),
        first("energy", ENERGY),
        first("pie", ["Summer pie"]),
        first("agility_potion", ["Agility potion"]),
        first("endurance", ["Ring of endurance"]),
    ]),
    section("currency", "Rewards", "rows", [
        all_("currency", ["Mark of grace", "Amylase crystal", "Agility arena ticket", "Hallowed mark",
                          "Hallowed crystal shard"]),
    ]),
])

FARMING = tab("farming", "Farming", "Seed dibber", sections=[
    section("gear", "Tools", "rows", [
        first("spade", ["Spade"]),
        first("rake", ["Rake"]),
        first("dibber", ["Seed dibber"]),
        first("secateurs", ["Magic secateurs", "Secateurs"]),
        first("trowel", ["Gardening trowel"]),
        first("can", ["Gricoller's can", "Watering can"]),
        first("bucket", ["Bottomless compost bucket"]),
        first("seed_box", ["Seed box"]),
        all_("compost", [("Ultracompost", "Best compost: +3 harvest lives, more disease protection"),
                         ("Supercompost", "Compost: +2 harvest lives"), ("Compost", "Compost: +1 harvest life"),
                         ("Compost potion", "Turns compost into supercompost"),
                         ("Volcanic ash", "Turns supercompost into ultracompost")]),
        first("cure", ["Plant cure"]),
        cape("Farming cape"),
    ]),
    section("outfit", "Outfit", "rows", [
        outfit("farmer", [("HEAD", ["Farmer's strawhat"]), ("BODY", ["Farmer's jacket", "Farmer's shirt"]),
                          ("LEGS", ["Farmer's boro trousers"]), ("BOOTS", ["Farmer's boots"])]),
    ]),
])

RUNECRAFT = tab("runecraft", "Runecraft", "Pure essence", sections=[
    section("gear", "Gear", "rows", [
        first("colossal", ["Colossal pouch"]),
        all_("pouches", ["Giant pouch", "Large pouch", "Medium pouch", "Small pouch"], unless=["colossal"]),
        first("binding", ["Binding necklace"]),
        first("eye", ["Amulet of the Eye"]),
        cape("Runecraft cape"),
    ]),
    section("outfit", "Outfit", "rows", [
        outfit("eye_outfit", [("HEAD", ["Hat of the Eye"]), ("BODY", ["Robe top of the Eye"]),
                              ("LEGS", ["Robe bottoms of the Eye"]), ("BOOTS", ["Boots of the Eye"])]),
    ]),
    section("altar", "Tiaras & talismans", "rows", [
        all_("tiaras", ["Catalytic tiara", "Elemental tiara", "re:^(?!Fancy|Gold)\\w+ tiara$"]),
        all_("talismans", ["Catalytic talisman", "Elemental talisman", "re:^(?!Ancient|Monkey)\\w+ talisman$"]),
    ]),
    section("essence", "Essence", "rows", [
        all_("essence", ["Pure essence", "Daeyalt essence", "Rune essence"]),
    ]),
])

COOKING = tab("cooking", "Cooking", "Cooking gauntlets", sections=[
    section("gear", "Gear", "rows", [
        first("gauntlets", ["Cooking gauntlets"]),
        cape("Cooking cape"),
    ]),
])

FIREMAKING = tab("firemaking", "Firemaking", "Tinderbox", sections=[
    section("gear", "Gear", "rows", [
        first("tinderbox", ["Tinderbox"]),
        first("torch", ["Bruma torch (off-hand)", "Bruma torch"]),
        first("knife", ["Knife"], requires=["Bruma torch", "Pyromancer hood"]),
        first("hammer", ["Hammer"], requires=["Bruma torch", "Pyromancer hood"]),
        cape("Firemaking cape"),
    ]),
    section("outfit", "Outfit", "rows", [
        outfit("pyromancer", [("HEAD", ["Pyromancer hood"]), ("BODY", ["Pyromancer garb"]),
                              ("LEGS", ["Pyromancer robe"]), ("BOOTS", ["Pyromancer boots"])]),
    ]),
])

SMITHING = tab("smithing", "Smithing", "Hammer", sections=[
    section("gear", "Gear", "rows", [
        first("hammer", ["Imcando hammer (off-hand)", "Imcando hammer", "Hammer"]),
        first("goldsmith", ["Goldsmith gauntlets"]),
        first("bf_gloves", ["Smiths gloves (i)", "Ice gloves"]),
        first("coal_bag", ["Coal bag"]),
        first("forging", ["Ring of forging"]),
        first("mould", ["Double ammo mould", "Ammo mould"]),
        cape("Smithing cape"),
    ]),
    section("outfit", "Outfit", "rows", [
        outfit("smiths", [("BODY", ["Smiths tunic"]), ("LEGS", ["Smiths trousers"]),
                          ("GLOVES", ["Smiths gloves (i)", "Smiths gloves"]), ("BOOTS", ["Smiths boots"])]),
    ]),
])

CRAFTING = tab("crafting", "Crafting", "Chisel", sections=[
    section("gear", "Tools", "rows", [
        first("chisel", ["Chisel"]),
        first("needle", ["Needle"]),
        first("thread", ["Thread"]),
        first("costume_needle", ["Costume needle"]),
        first("pipe", ["Glassblowing pipe"]),
        all_("moulds", ["Ring mould", "Necklace mould", "Amulet mould", "Bracelet mould", "Tiara mould",
                        "Holy mould", "Unholy mould", "Sickle mould", "Bolt mould"]),
        cape("Crafting cape"),
    ]),
])

FLETCHING = tab("fletching", "Fletching", "Knife", sections=[
    section("gear", "Tools", "rows", [
        first("knife", ["Fletching knife", "Knife"]),
        first("chisel", ["Chisel"]),
        cape("Fletching cape"),
    ]),
])

HERBLORE = tab("herblore", "Herblore", "Pestle and mortar", sections=[
    section("gear", "Tools", "rows", [
        first("pestle", ["Pestle and mortar"]),
        first("herb_sack", ["Silklined herb sack", "Herb sack"]),
        first("amulet", ["Alchemist's amulet", "Amulet of chemistry"]),
        first("goggles", ["Prescription goggles"]),
        first("reagent", ["Reagent pouch"]),
        cape("Herblore cape"),
    ]),
    section("outfit", "Outfit", "rows", [
        outfit("alchemist", [("BODY", ["Alchemist labcoat"]), ("LEGS", ["Alchemist pants"]),
                             ("GLOVES", ["Alchemist gloves"])]),
    ]),
])

CONSTRUCTION = tab("construction", "Construction", "Saw", sections=[
    section("gear", "Tools", "rows", [
        *SAWS,
        first("hammer", ["Imcando hammer", "Hammer"]),
        first("plank_sack", ["Plank sack"]),
        cape("Construct. cape"),
    ]),
    section("outfit", "Outfit", "rows", [
        outfit("carpenter", [("HEAD", ["Carpenter's helmet"]), ("BODY", ["Carpenter's shirt"]),
                             ("LEGS", ["Carpenter's trousers"]), ("BOOTS", ["Carpenter's boots"])]),
    ]),
])

SAILING = tab("sailing", "Sailing", "Rope", sections=[
    section("gear", "Gear", "rows", [
        first("hammer", ["Imcando hammer", "Hammer"]),
        *SAWS,
        cape("Sailing cape"),
    ]),
    section("outfit", "Outfit", "rows", [
        outfit("glider", [("HEAD", ["Crystal glider's hat"]), ("BODY", ["Crystal glider's jacket"]),
                          ("LEGS", ["Crystal glider's trousers"]), ("BOOTS", ["Crystal glider's shoes"])]),
    ]),
])

TABS = [MELEE, RANGED, MAGIC, MOVEMENT, PRAYER, SLAYER, AGILITY, THIEVING, HERBLORE, CRAFTING, FLETCHING, HUNTER,
        MINING, SMITHING, FISHING, COOKING, FIREMAKING, WOODCUTTING, FARMING, RUNECRAFT, CONSTRUCTION, SAILING]


# ---------------------------------------------------------------- unique uses
# unique_uses.py holds wiki-verified groups of items that are best in slot for a
# particular job (demonbane, defence-drain specials, task protection, boss
# mechanics...). Each group becomes a pick in the fitting section of its tab and
# replaces a hand-written pick with the same key.

def _unique_section(tab, group):
    key = tab["key"]
    if key in ("melee", "ranged", "magic"):
        if group.get("special"):
            return "specials", "Special attacks", "rows"
        if group["slot"] in (None, "WEAPON"):
            return "weapons", "Weapons", "rows"
        if group["slot"] == "AMMO" and key == "ranged":
            return "ammo", "Ammunition", "rows"
        return "situational", "Situational", "alts"
    if key == "movement":
        return group.get("section", "items"), "Teleport items", "rows"
    return group.get("section", "gear"), "Gear", "rows"


def apply_unique(tabs, groups):
    by_key = {t["key"]: t for t in tabs}
    for g in groups:
        tab = by_key[g["tab"]]
        sec_key, title, layout = _unique_section(tab, g)
        maker = first if g["mode"] == "best" else all_
        pick = maker(g["group"], [tuple(i) for i in g["items"]], slot=g["slot"], why=g["label"])
        for s in tab["sections"]:
            s["picks"] = [p for p in s["picks"] if p["key"] != g["group"]]
        section_ = next((s for s in tab["sections"] if s["key"] == sec_key), None)
        if section_ is None:
            section_ = section(sec_key, title, layout, [])
            tab["sections"].append(section_)
        section_["picks"].append(pick)


try:
    from unique_uses import UNIQUE_USES
except ImportError:
    UNIQUE_USES = []
apply_unique(TABS, UNIQUE_USES)

# Ammo class per ranged launcher (for the 'ammo' pick); launchers not listed need no ammo.
LAUNCHER_AMMO = {name: cls for name, cls in RANGED_WEAPONS if cls}
LAUNCHER_AMMO.update({b: "arrow" for b in BOWS if b not in ("Venator bow", "Webweaver bow", "Craw's bow")})
LAUNCHER_AMMO.update({c: "bolt" for c in CROSSBOWS if c != "Hunters' crossbow"})

# ---------------------------------------------------------------- supplies from wiki recipes
# (skill, tab key, [(section title, regex on in-game name[, order])...]). A
# ".*" rule is a catch-all that only takes items this skill uses more than any
# other skill. Items matching EXCLUDE_SUPPLIES and untradeable quest items are
# dropped. An order list sorts by position (grimy herb before its clean herb).
LEATHERS = ["Leather", "Hard leather", "Snakeskin", "Cured yak-hide", "Suqah leather", "Green dragon leather",
            "Blue dragon leather", "Red dragon leather", "Black dragon leather", "Hueycoatl hide"]
SILVER = ["Silver bar", "Opal amulet (u)", "Jade amulet (u)", "Topaz amulet (u)", "Unstrung symbol", "Unstrung emblem"]
GOLD = ["Gold bar", "Gold amulet (u)", "Sapphire amulet (u)", "Emerald amulet (u)", "Ruby amulet (u)",
        "Diamond amulet (u)", "Dragonstone amulet (u)", "Onyx amulet (u)", "Zenyte amulet (u)"]
GEMS = ["Opal", "Jade", "Red topaz", "Sapphire", "Emerald", "Ruby", "Diamond", "Dragonstone", "Onyx", "Zenyte"]
HERBS = ["Guam leaf", "Marrentill", "Tarromin", "Harralander", "Ranarr weed", "Toadflax", "Irit leaf", "Avantoe",
         "Kwuarm", "Huasca", "Snapdragon", "Cadantine", "Lantadyme", "Dwarf weed", "Torstol"]

SUPPLY_RULES = [
    ("Herblore", "herblore", [
        ("Grimy herbs", r"(?i)^Grimy (?:%s)$" % "|".join(HERBS), HERBS),
        ("Clean herbs", r"(?i)^(?:%s)$" % "|".join(HERBS), HERBS),
        ("Unfinished potions", r"\(unf\)$"),
        ("Vials", r"^(Vial|Vial of water|Vial of blood|Coconut milk)$"),
        ("Potions", r"\(\d\)$|\(\d\) ?$|potion|brew|restore|antidote|antifire|anti-venom|antipoison|mix$"),
        ("Secondaries", r".*"),
    ]),
    ("Crafting", "crafting", [
        ("Uncut gems", r"(?i)^Uncut (?:%s)$" % "|".join(GEMS), GEMS),
        ("Cut gems", r"(?i)^(?:%s)$" % "|".join(GEMS), GEMS),
        ("Leather & hides", r"(?i)^(%s)$" % "|".join(re.escape(x) for x in LEATHERS), LEATHERS),
        ("Glass", r"(?i)^(Molten glass|Bucket of sand|Soda ash|Seaweed|Giant seaweed|Sandstone \(\d+kg\))$"),
        ("Silver jewellery", r"^(%s)$" % "|".join(re.escape(x) for x in SILVER), SILVER),
        ("Gold jewellery", r"^(%s)$" % "|".join(re.escape(x) for x in GOLD), GOLD),
        ("Battlestaves & orbs", r"(?i)^(Battlestaff|(Air|Water|Earth|Fire) orb|Unpowered orb)$"),
        ("Spinning & weaving", r"(?i)^(Wool|Ball of wool|Flax|Bow string|Jute fibre|Linen yarn|Hemp yarn|Cotton yarn|"
                               r"Clay|Soft clay|Unfired .*)$"),
    ]),
    ("Fletching", "fletching", [
        ("Logs", r"(?i)^(Logs|(?!Sap-treated).* logs)$"),
        ("Bows", r"(?i)\(u\)$|^(Bow string|Crossbow string)$|stock$|limbs$"),
        ("Arrows", r"(?i)^(Arrow shaft|Headless arrow|Feather|.* arrowtips|Broad arrowheads|"
                   r"(Bronze|Iron|Steel|Mithril|Adamant|Rune|Amethyst|Dragon) arrow)$"),
        ("Bolts", r"(?i)bolts? ?\(unf\)$|bolt ?tips$|^Unfinished broad bolts$|"
                  r"^(Bronze|Blurite|Iron|Steel|Mithril|Adamant|Runite|Dragon|Silver|Broad) bolts$"),
        ("Darts & javelins", r"(?i)dart tips?$|javelin tips$|^Javelin shaft$|^(Headless )?atlatl dart|^Atlatl dart"),
    ]),
    ("Smithing", "smithing", [
        ("Ores", r"(?i) ore$|^Coal$"),
        ("Bars", r"(?i) bar$"),
    ]),
    ("Cooking", "cooking", [
        ("Raw food", r"(?i)^Raw (?!.*\(\d\)$)|^Uncooked "),
    ]),
    ("Firemaking", "firemaking", [
        ("Logs", r"(?i)^(Logs|.* logs)$"),
    ]),
    ("Construction", "construction", [
        ("Planks", r"(?i)plank$"),
        ("Nails", r"(?i)nails$"),
        ("Materials", r"(?i)^(Bolt of cloth|Limestone brick|Marble block|Gold leaf|Magic stone|Soft clay|Steel bar|"
                      r"Molten glass|Clockwork|Timber beam)$"),
    ]),
    ("Farming", "farming", [
        ("Seeds", r"(?i)seed$|seeds$|spore$"),
        ("Saplings", r"(?i)sapling$|seedling$"),
    ]),
    ("Runecraft", "runecraft", [
        ("Runes", r"(?i) rune$"),
    ]),
    ("Sailing", "sailing", [
        ("Planks", r"(?i)plank$"),
        ("Parts", r"(?i)parts$"),
        ("Bars & sheets", r"(?i) bar$|sheet$"),
        ("Cloth & nails", r"(?i)^(Bolt of (linen|canvas|cotton)|.* nails|.* yarn|.* fabric roll|Swamp tar)$"),
    ]),
]

# Never add these as supplies (currency, generic junk, quest-only).
EXCLUDE_SUPPLIES = r"(?i)^(Coins|Platinum token|Bucket|Bucket of water|Jug|Jug of water|Bowl|Bowl of water|Pot|" \
                   r"Pot of flour|Empty pot|Hammer|Saw|Knife|Chisel|Needle|Tinderbox|Rope|Vial|Pestle and mortar|" \
                   r"Watering can|Gardening trowel|Spade|Rake|Seed dibber)$"

# Auto picks ignore these (PvP/Wilderness-only or arena loadout gear whose
# bonuses don't apply in normal PvM).
EXCLUDE_AUTO = [
    "re:^(Vesta's|Statius's|Morrigan's|Zuriel's) ",
    "Wilderness champion amulet",
]

# Melee weapons kept out of the damage-model picks (situational picks cover them).
EXCLUDE_MELEE_WEAPON = [
    "Ursine chainmace", "Viggora's chainmace", "re:^(Vesta's|Statius's) ",
    "Dragon hunter lance", "re:^Keris", "Emberlight", "Arclight", "Darklight", "Silverlight",
    "Leaf-bladed battleaxe", "Leaf-bladed sword", "Leaf-bladed spear", "Blisterwood flail", "Ivandis flail",
    "Wolfbane", "Dharok's greataxe", "Crimson kisten",
]

# Damage multipliers for passives the stat model can't see (general targets).
MELEE_WEAPON_MULTIPLIER = {
    "Scythe of Vitur": 1.5,
    "Holy Scythe of Vitur": 1.5,
    "Sanguine Scythe of Vitur": 1.5,
    "Soulreaper axe": 1.2,
    "Dual macuahuitl": 1.05,
}
# Weapons that roll accuracy twice (hit chance 1 - (1 - p)^2).
MELEE_DOUBLE_ACCURACY = ["Osmumten's fang"]


# ---------------------------------------------------------------- cosmetics
# The cosmetics tab is generated from wiki categories and zero-stat clothing
# (see cosmetics_tab in generate_data.py); these rules steer it.
COSMETIC_ICON = "Red partyhat"
COSMETIC_CATEGORIES = ["Event rewards", "Cosmetics", "Ornament kits", "Random event rewards", "Treasure Trails rewards"]
# clue rewards with combat stats only count when they are a cosmetic variant
COSMETIC_VARIANT = r"\((?:g|t|h\d|gold|trimmed)\)$|^Gilded |^3rd Age |^Ring of 3rd age"
# zero-stat items in these slots are clothing; zero-stat rings/amulets are usually functional
COSMETIC_ZERO_STAT_SLOTS = {"head", "body", "legs", "feet", "hands", "cape"}
COSMETIC_EXCLUDE = r"(?i)^(Ghostspeak|Desert |Mining helmet|Bullseye|Lantern|Candle|Diving apparatus|Fishbowl|" \
                   r"Ice gloves|Spiny|Earmuffs|Nose peg|Facemask|Gas mask|Slayer|Black mask|Climbing boots|" \
                   r"Team-\d+ cape|Hunting knife|Masori crafting kit|Granite clamp|.*bracelet.*|.*coffin.*|"                    r".*apparatus|.*\(Last Man Standing\)|.*\(beta\)|.*\(Deadman.*)"
# items of one outfit share a set stem so they stay together on a row
COSMETIC_FAMILIES = [(r"(?i)partyhat$", "Partyhats"), (r"(?i)h(?:allo)?'?ween mask$", "Halloween masks"),
                     (r"(?i)santa hat$", "Santa hats")]
COSMETIC_SLOT_WORDS = {
    "hat", "hood", "helm", "helmet", "full helm", "full helmet", "med helm", "mask", "coif", "cap", "beret",
    "headband", "headdress", "crown", "tiara", "wig", "mitre", "top", "shirt", "jacket", "robe top", "robe", "robes",
    "body", "platebody", "chainbody", "garb", "torso", "tabard", "blouse", "legs", "platelegs", "plateskirt",
    "skirt", "bottom", "bottoms", "robe bottom", "robe bottoms", "robe skirt", "robeskirt", "trousers", "pantaloons",
    "chaps", "tassets", "leggings", "shorts", "boots", "shoes", "feet", "slippers", "sandals", "gloves",
    "gauntlets", "vambraces", "bracers", "cape", "cloak", "kiteshield", "sq shield", "shield", "amulet",
    "necklace", "scarf", "stole", "crozier", "staff", "sword", "wand", "outfit", "set",
}
