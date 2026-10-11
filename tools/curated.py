"""Hand-curated tab definitions for No Brain Bank Tabs.

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


def section(key, title, layout, picks, supplies=False):
    """supplies: hidden when the player turns off "Supplies in skill tabs"."""
    out = dict(key=key, title=title, layout=layout, picks=picks)
    if supplies:
        out["supplies"] = True
    return out


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

ANTIPOISON = [("Extended anti-venom+", "Strongest, longest-lasting venom and poison protection"),
              ("Anti-venom+", "Cures venom and poison, then protects against both"),
              ("Anti-venom", "Cures venom and poison, then protects against venom"),
              ("Antidote++", "Cures poison, long poison immunity"), ("Antidote+", "Cures poison, poison immunity"),
              ("Superantipoison", "Cures poison, short poison immunity"), ("Antipoison", "Cures poison")]
ANTIFIRE = [("Extended super antifire", "Full dragonfire immunity for 6 minutes"),
            ("Super antifire potion", "Full dragonfire immunity for 3 minutes"),
            ("Extended antifire", "Partial dragonfire protection for 12 minutes"),
            ("Antifire potion", "Partial dragonfire protection for 6 minutes")]
RESTORE = [("Super restore", "Restores prayer and stats drained by attacks"), ("Prayer potion", "Restores prayer"),
           ("Prayer regeneration potion", "Restores prayer gradually over time")]

ELEMENTAL_RUNES = ["Fire rune", "Water rune", "Air rune", "Earth rune"]
# by Runecraft level
COMBO_RUNES = ["Mist rune", "Dust rune", "Mud rune", "Smoke rune", "Steam rune", "Lava rune"]
RUNE_POUCH = ["Divine rune pouch", "Rune pouch"]


def combat_supplies(boosts, restores=(), brews=()):
    """Every potion together (boosts, restores, brews, antipoison, antifire), then the food on a row of its own.
    restores and brews take tab-specific unique_uses.py markers (Surge potion, Armadyl brew) so they sit by
    their family."""
    return [
        section("supplies", "Supplies", "rows", [
            *boosts,
            all_("restore", RESTORE),
            *restores,
            first("brew", ["Saradomin brew"]),
            *brews,
            all_("antipoison", ANTIPOISON),
            all_("antifire", ANTIFIRE),
        ]),
        section("supplies_food", "Food", "rows", [
            # how many foods comes from the "Food items" setting
            first("food", FOOD, take=4),
            first("karambwan", [("Cooked karambwan", "Combo food: eat in the same tick as other food (heals 18)")]),
        ]),
    ]


# Blighted supplies only work in the Wilderness and PvP areas (Ferox Enclave, PvP worlds, Castle Wars, Clan Wars...),
# so they get their own section. The bind, snare and wave sacks were merged into the entangle and surge sacks.
BLIGHTED_SACKS = [
    ("Blighted ancient ice sack", "Wilderness/PvP only: runes for one Ice spell (Ancient Magicks)"),
    ("Blighted surge sack", "Wilderness/PvP only: runes for one Surge or Wave spell"),
    ("Blighted entangle sack", "Wilderness/PvP only: runes for one Bind, Snare or Entangle"),
    ("Blighted teleport spell sack", "Wilderness only: runes for one Tele Block or Teleport to Target"),
    ("Blighted vengeance sack", "Wilderness/PvP only: runes for one Vengeance (Lunar)"),
]


def blighted_supplies(sacks=False):
    """The blighted versions of the tab's supplies, a blank row below them ("separate"). The magic tab's
    spell sacks follow on a row of their own: back-to-back "separate" sections share one block."""
    return [
        section("blighted", "Blighted supplies", "separate", [
            first("blighted_boost", [("Blighted overload", "Deadman only: boosts Attack, Strength, Ranged and Magic")]),
            first("blighted_restore",
                  [("Blighted super restore", "Wilderness/PvP only: restores prayer and drained stats")]),
            all_("blighted_food", [("Blighted anglerfish", "Wilderness/PvP only: heals 22 and can overheal"),
                                   ("Blighted manta ray", "Wilderness/PvP only: heals 22")]),
            first("blighted_karambwan", [("Blighted karambwan", "Wilderness/PvP only: combo food (heals 18)")]),
        ]),
    ] + ([section("blighted_sacks", "Blighted spell sacks", "separate", [all_("blighted_sacks", BLIGHTED_SACKS)])]
         if sacks else [])


def prayer_gear(profile):
    """Prayer bonus is situational: the highest-prayer item for every armour slot
    (blessings, god books, Ardougne cloak...), ties going to the better item for
    the tab's style, lined up beside the best-in-slot set."""
    return dict(key="prayer_gear", mode="prayerset", profile=profile, entries=[],
                slots=["HEAD", "CAPE", "AMULET", "AMMO", "BODY", "SHIELD", "LEGS", "GLOVES", "BOOTS", "RING"])


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
    section("situational", "Situational", "alts", situational_common(False) + [prayer_gear("melee")] + [
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
        # unique_uses.py spec groups take these places: damage, then defence drains, then support
        all_("spec_claws", []), all_("spec_big_hit", []), all_("spec_crush", []), all_("spec_crush_cheap", []),
        all_("spec_halberd", []), all_("spec_instant", []), all_("spec_cheap", []), all_("spec_finisher", []),
        all_("spec_aoe", []),
        all_("spec_def_drain_pct", []), all_("spec_def_drain_dmg", []), all_("spec_demon_drain", []),
        all_("spec_freeze", []), all_("spec_bleed", []), all_("spec_sustain", []), all_("spec_strength", []),
        all_("spec_team_accuracy", []), all_("spectral", []),
        # anything left over, one weapon family at a time
        all_("specs", ["Dragon claws", "Burning claws", "Voidwaker", "Dragon warhammer", "Elder maul", "Granite maul",
                       "Bandos godsword", "Armadyl godsword", "Ancient godsword", "Saradomin godsword",
                       "Zamorak godsword", "Crystal halberd", "Dragon halberd", "Dragon dagger", "Abyssal dagger",
                       "Bone dagger", "Dragon longsword", "Dragon mace", "Dragon scimitar", "Barrelchest anchor",
                       "Crimson kisten", "Saradomin's blessed sword", "Saradomin sword"]),
    ]),
    *combat_supplies([
        first("boost", ["Divine super combat potion", "Super combat potion", "Combat potion"]),
        first("boost_att", ["Divine super attack potion", "Super attack", "Attack potion"], unless=["boost"]),
        first("boost_str", ["Divine super strength potion", "Super strength", "Strength potion"],
              unless=["boost"]),
    ], restores=[all_("spec_restore", [])]),  # Surge potion (unique_uses.py)
    *blighted_supplies(),
])

# ---------------------------------------------------------------- ranged

BOWS = ["Twisted bow", "Dark bow", "Corrupted dark bow", "Magic shortbow (i)", "Magic shortbow", "Magic longbow",
        "Magic comp bow", "Yew shortbow", "Yew longbow", "Yew comp bow", "Maple shortbow", "Maple longbow",
        "Willow shortbow", "Willow longbow", "Oak shortbow", "Oak longbow", "Shortbow", "Longbow", "Seercull",
        "3rd Age bow", "Scorching bow", "Venator bow", "Webweaver bow", "Craw's bow"]
CROSSBOWS = ["Zaryte crossbow", "Armadyl crossbow", "Dragon hunter crossbow", "Dragon crossbow", "Rune crossbow",
             "Karil's crossbow", "Adamant crossbow", "Mithril crossbow", "Steel crossbow", "Iron crossbow",
             "Bronze crossbow", "Blurite crossbow", "Dorgeshuun crossbow", "Hunters' crossbow"]

# Enchant Crossbow Bolt order; each gem's dragon bolts first. Hover text is the bolt's effect.
_BOLT_EFFECTS = [("Opal", "Lucky Lightning: extra damage, best against low defence"),
                 ("Jade", "Earth's Fury: can knock the target down"),
                 ("Pearl", "Sea Curse: extra damage, more against fiery monsters"),
                 ("Topaz", "Down to Earth: lowers the target's Magic"),
                 ("Sapphire", "Clear Mind: drains the target's Prayer"),
                 ("Emerald", "Magical Poison: poisons the target"),
                 ("Ruby", "Blood Forfeit: 20% of the target's hitpoints (best on high-HP bosses)"),
                 ("Diamond", "Armour Piercing: ignores defence, great on tanky targets"),
                 ("Dragonstone", "Dragon's Breath: dragonfire damage (not on dragons)"),
                 ("Onyx", "Life Leech: heals you (not on undead)")]
ENCHANTED_BOLTS = [(name, effect) for gem, effect in _BOLT_EFFECTS
                   for name in ("%s dragon bolts (e)" % gem, "%s bolts (e)" % gem)]

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
    section("situational", "Situational", "alts", situational_common(True) + [prayer_gear("ranged")] + [
        set_("crystal", [("HEAD", ["Crystal helm"]), ("BODY", ["Crystal body"]), ("LEGS", ["Crystal legs"])],
             requires=["re:^Bow of Faerdhinen", "Crystal bow"]),
        set_("void", [("HEAD", ["Void ranger helm"]), ("BODY", VOID_TOP), ("LEGS", VOID_ROBE),
                      ("GLOVES", ["Void knight gloves"])], complete=True),
        set_("eclipse_moon", [("HEAD", ["Eclipse Moon helm"]), ("BODY", ["Eclipse Moon chestplate"]),
                              ("LEGS", ["Eclipse Moon tassets"])], requires=["Eclipse atlatl"]),
    ]),
    section("weapons", "Weapons", "rows", [
        # keys match unique_uses.py groups so they replace these picks in place
        first("dragonbane_ranged", ["Dragon hunter crossbow"]),
        first("demonbane_ranged", ["Scorching bow"]),
        first("blowpipe", ["Blazing blowpipe", "Toxic blowpipe"]),
        first("multi_bounce", ["Venator bow"]),
        first("chinchompas", ["Black chinchompa", "Red chinchompa", "Chinchompa"]),
        first("wilderness_ranged", ["Webweaver bow", "Craw's bow"]),
        first("karil", ["Karil's crossbow"], requires=["Karil's coif"]),
    ]),
    section("specials", "Special attacks", "rows", [
        # unique_uses.py spec groups take these places, one weapon family at a time (ballista (or) by the
        # ballistae, the bows by their purpose group): crossbows, ballistae, bows, thrown, then drains
        all_("spec_bolt", []), all_("dcb_spec", []), all_("ballista_spec", []), all_("spec_ballista", []),
        all_("spec_ranged_multi", []), all_("msb_spec", []), all_("spec_ranged_guaranteed", []),
        all_("powershot", []), all_("spec_ranged_cheap", []), all_("spec_tick_skip", []), all_("spec_chain", []),
        all_("spec_ranged_heal", []), all_("spec_ranged_drain", []), all_("spec_magic_drain", []),
        # the broad research list, then the hand-written catch-all, for anything still unplaced
        all_("ranged_specs", []),
        all_("specs", ["Zaryte crossbow", "Armadyl crossbow", "Dorgeshuun crossbow", "Dragon thrownaxe",
                       "Rune thrownaxe", "Heavy ballista", "Light ballista", "Dragon knife", "Dark bow",
                       "Corrupted dark bow", "Magic shortbow (i)", "Magic shortbow", "Magic longbow", "Magic comp bow",
                       "Seercull"]),
    ]),
    section("ammo", "Ammunition", "rows", [
        first("arrows", ARROWS, requires=BOWS, placed=True),
        # every useful bolt you own: enchanted bolts in enchant order (dragon first), broad, plain, silver
        all_("bolts_enchanted", ENCHANTED_BOLTS, requires=CROSSBOWS, placed=True),
        all_("bolts_sapphire", [], requires=CROSSBOWS, placed=True),  # unique_uses.py, already covered above
        all_("broad_bolts", [("Amethyst broad bolts", "Broad bolts: needed for turoths and kurasks (more accurate)"),
                             ("Broad bolts", "Broad bolts: needed for turoths and kurasks")],
             requires=CROSSBOWS, placed=True),
        all_("bolts", ["Dragon bolts", "Runite bolts", "Adamant bolts", "Mithril bolts", "Steel bolts",
                       "Iron bolts", "Bronze bolts"], requires=CROSSBOWS, placed=True),
        all_("silver_bolts", [("Silver bolts", "Silver bolts: damage vampyres")], requires=CROSSBOWS, placed=True),
        first("antler_bolts", ["Sunlight antler bolts", "Moonlight antler bolts"],
              requires=["Hunters' sunlight crossbow"], placed=True),
        first("javelins", JAVELINS, requires=["Heavy ballista", "Light ballista"], placed=True),
        first("darts", DARTS, requires=["Toxic blowpipe", "Blazing blowpipe"], placed=True),
        first("atlatl_darts", ["Atlatl dart"], requires=["Eclipse atlatl"], placed=True),
    ]),
    *combat_supplies([
        first("boost", ["Divine bastion potion", "Divine ranging potion", "Bastion potion", "Ranging potion"]),
    ], restores=[all_("spec_restore", [])], brews=[all_("armadyl_brew", [])]),  # Surge potion, Armadyl brew
    *blighted_supplies(),
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

# Unenchanted jewellery for the Enchant spells, by spell: each gem's ring, necklace, amulet and
# bracelet stay together (two gems to a row) at the bottom of the magic tab.
ENCHANT_GEMS = [("Opal", "Lvl-1", 7), ("Sapphire", "Lvl-1", 7), ("Jade", "Lvl-2", 27), ("Emerald", "Lvl-2", 27),
                ("Topaz", "Lvl-3", 49), ("Ruby", "Lvl-3", 49), ("Diamond", "Lvl-4", 57),
                ("Dragonstone", "Lvl-5", 68), ("Onyx", "Lvl-6", 87), ("Zenyte", "Lvl-7", 93)]


# Enchant Crossbow Bolt levels; each gem's bolts and dragon bolts stay together.
ENCHANT_BOLTS = [("Opal", 4), ("Sapphire", 7), ("Jade", 14), ("Pearl", 24), ("Emerald", 27), ("Topaz", 29),
                 ("Ruby", 49), ("Diamond", 57), ("Dragonstone", 68), ("Onyx", 87)]


def enchanting():
    jewellery, jewellery_groups = [], []
    for gem, spell, level in ENCHANT_GEMS:
        for piece in ("ring", "necklace", "amulet", "bracelet"):
            name = "Dragon necklace" if (gem, piece) == ("Dragonstone", "necklace") else "%s %s" % (gem, piece)
            jewellery.append((name, "Unenchanted: %s Enchant (Magic %d)" % (spell, level)))
            jewellery_groups.append(gem)
    bolts, bolt_groups = [], []
    for gem, level in ENCHANT_BOLTS:
        for name in ("%s bolts" % gem, "%s dragon bolts" % gem):
            bolts.append((name, "Unenchanted: Enchant Crossbow Bolt (Magic %d)" % level))
            bolt_groups.append(gem)
    return [
        section("enchanting", "Unenchanted jewellery", "bottom",
                [dict(all_("enchant", jewellery), groups=jewellery_groups)]),
        section("enchant_bolts", "Unenchanted bolts", "bottom",
                [dict(all_("enchant_bolts", bolts), groups=bolt_groups)]),
        section("orbs", "Orbs to charge", "bottom",
                [all_("orbs", [("Unpowered orb", "Charge Orb at an elemental obelisk (Magic 56-66)")])]),
    ]


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
    section("situational", "Situational", "alts", situational_common(True) + [prayer_gear("magic")] + [
        set_("virtus", [("HEAD", ["Virtus mask"]), ("BODY", ["Virtus robe top"]), ("LEGS", ["Virtus robe bottom"])]),
        set_("void", [("HEAD", ["Void mage helm"]), ("BODY", VOID_TOP), ("LEGS", VOID_ROBE),
                      ("GLOVES", ["Void knight gloves"])], complete=True),
        set_("blue_moon", [("HEAD", ["Blue Moon helm"]), ("BODY", ["Blue Moon chestplate"]),
                           ("LEGS", ["Blue Moon tassets"])], requires=["Blue Moon spear"]),
        all_("tomes", ["Tome of Fire", "Tome of Water", "Tome of Earth"], slot="SHIELD"),
    ]),
    section("weapons", "Weapons", "rows", [
        # keys match unique_uses.py groups so they replace these picks in place
        first("ancients", ANCIENT_STAVES),
        first("standard", STANDARD_STAVES),
        first("demonbane_magic", ["Purging staff"]),
        first("dragonbane_magic", ["Dragon hunter wand"]),
        first("wilderness_magic", ["Accursed sceptre", "Thammaron's sceptre"]),
        first("blue_moon_spear", ["Blue Moon spear"]),
        first("ahrim", ["Ahrim's staff"], requires=["Ahrim's hood"]),
    ]),
    section("specials", "Special attacks", "rows", [
        # unique_uses.py spec groups take these places; the broader lists come after so their reasons win
        all_("spec_magic_burst", []), all_("spec_magic_def_drain", []), all_("spec_prayer_restore", []),
        all_("power_of_death", []), all_("magic_specs", []),
        all_("specs", ["Volatile Nightmare staff", "Eldritch Nightmare staff", "Accursed sceptre"]),
    ]),
    # every rune, a row per kind: elemental (and the pouch), then utility with Law under Fire, then the combat
    # runes, then the combination runes, each by Runecraft level
    section("runes", "Elemental runes", "rows", [
        all_("runes", ELEMENTAL_RUNES + ["Sunfire rune"]),
        first("pouch", RUNE_POUCH),
    ]),
    section("runes_utility", "Utility runes", "rows", [
        all_("runes_utility", ["Law rune", "Cosmic rune", "Astral rune", "Nature rune", "Soul rune"]),
    ]),
    section("runes_combat", "Combat runes", "rows", [
        all_("runes_combat", ["Mind rune", "Body rune", "Chaos rune", "Death rune", "Blood rune", "Wrath rune"]),
    ]),
    section("runes_combo", "Combination runes", "rows", [
        all_("runes_combo", COMBO_RUNES + ["Aether rune"]),
    ]),
    *combat_supplies([
        first("boost", ["Divine magic potion", "Magic potion", "Divine battlemage potion", "Battlemage potion",
                        "Forgotten brew", "Ancient brew"]),
        first("heart", ["Saturated heart", "Imbued heart"]),
    ], restores=[all_("spec_restore", [])]),  # Surge potion (unique_uses.py)
    *blighted_supplies(sacks=True),
    *enchanting(),
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


# Weight-reducing gear beside the graceful outfit (worn weights from the wiki).
def weight_reducing():
    return [
        first("light_boots", [("Boots of lightness", "Weight-reducing boots: -4.5 kg when worn")]),
        first("light_gloves", [("Penance gloves", "Weight-reducing gloves: -4.5 kg when worn")]),
        first("light_cape", [("Spottier cape", "Weight-reducing cape: -4.5 kg when worn"),
                             ("Spotted cape", "Weight-reducing cape: -2.3 kg when worn")]),
    ]


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
        *weight_reducing(),
        all_("graceful_cape_sub", []),  # Agility cape (unique_uses.py): counts as a graceful cape
        first("energy", ENERGY),
        first("endurance", ["Ring of endurance"]),
    ]),
    # teleport jewellery like the Crafting tab: silver (jade, red topaz), then gold in gem order
    # (sapphire, emerald, ruby, dragonstone: ring, necklace, bracelet, amulet), then everything else
    section("jewellery_silver", "Silver teleport jewellery", "rows", [
        all_("jewellery_silver", ["Ring of returning", "Necklace of passage", "Burning amulet"]),
    ]),
    section("jewellery", "Gold teleport jewellery", "rows", [
        all_("jewellery", ["Games necklace", "Ring of dueling", "Digsite pendant"]),
        first("wealth", ["Ring of wealth (i)", "Ring of wealth"]),
        all_("jewellery_dragonstone", ["Skills necklace", "Combat bracelet"]),
        first("glory", ["Amulet of eternal glory", "Amulet of glory (t)", "Amulet of glory"]),
        first("slayer_ring", ["Slayer ring (eternal)", "Slayer ring"]),
    ]),
    section("jewellery_other", "Other teleport jewellery", "rows", [
        all_("jewellery2", ["Ring of the elements", "Ring of shadows", "Giantsoul amulet", "Pendant of Ates",
                            "Amulet of the Eye", "Camulet", "Xeric's talisman", "Drakan's medallion"]),
        all_("teleport_jewellery_new", []),  # Sailors' amulet, Cowbell amulet (unique_uses.py)
    ]),
    section("diary", "Diary rewards", "rows", [
        # in-game Achievement Diary order; Ghommal's hilt (combat achievements) last
        first("ardy", tiers("Ardougne cloak")),
        first("desert", tiers("Desert amulet")),
        first("fremennik", tiers("Fremennik sea boots")),
        first("kandarin", tiers("Kandarin headgear")),
        first("karamja", tiers("Karamja gloves")),
        first("kourend", tiers("Rada's blessing")),
        first("explorer", tiers("Explorer's ring")),
        first("morytania", tiers("Morytania legs")),
        first("western", tiers("Western banner")),
        first("wilderness", tiers("Wilderness sword")),
        first("ghommal", tiers("Ghommal's hilt", 6)),
    ]),
    section("items", "Teleport items", "rows", [
        first("fairy", ["Lunar staff", "Dramen staff"]),
        first("ectophial", ["Ectophial"]),
        # keys match unique_uses.py groups so every version of an item stays together
        first("seed_pod", ["Royal seed pod", "Grand seed pod"]),
        first("lyre", ["Enchanted lyre(i)", "Enchanted lyre"]),
        all_("items", ["Icy basalt", "Stony basalt", "Pharaoh's sceptre"]),
        first("skull", ["Skull sceptre (i)", "Skull sceptre"]),
        first("chronicle", ["Chronicle"]),
        first("memoirs", ["Book of the Dead", "Kharedst's memoirs"]),
        first("crystal", ["Eternal teleport crystal", "Teleport crystal"]),
        first("quetzal", ["Perfected quetzal whistle(i)", "Perfected quetzal whistle", "Enhanced quetzal whistle",
                          "Basic quetzal whistle"]),
        first("max_cape", ["Max cape"]),
        first("mythical", ["Mythical max cape", "Mythical cape"]),
        all_("capes", ["Achievement diary cape", "Quest point cape", "Music cape", "Construct. cape",
                       "Crafting cape", "Farming cape", "Fishing cape", "Hunter cape", "Strength cape"]),
        all_("skill_cape_teleports", []),  # Sailing cape (unique_uses.py)
        all_("emergency_escape", []),  # Escape crystal / Ring of life, Defence cape (unique_uses.py)
        all_("boat_transport", []),  # Summon boat, Boat bottle (unique_uses.py)
    ]),
    # one section per spellbook, each by Magic level (wiki Magic tablet)
    section("tablets", "Standard tablets", "rows", [
        all_("tablets", ["Varrock teleport (tablet)", "Lumbridge teleport (tablet)", "Falador teleport (tablet)",
                         "Camelot teleport (tablet)", "Kourend castle teleport (tablet)", "Ardougne teleport (tablet)",
                         "Civitas illa fortis teleport (tablet)", "Watchtower teleport (tablet)",
                         "Teleport to boat (tablet)"]),
    ]),
    # Teleport to house, then the tablets redirected from it, by house-portal Construction level
    section("house_tablets", "House tablets", "rows", [
        all_("house_tablets", ["Teleport to house (tablet)", "Rimmington teleport", "Taverley teleport",
                               "Pollnivneach teleport", "Hosidius teleport", "Rellekka teleport", "Aldarin teleport",
                               "Brimhaven teleport", "Yanille teleport", "Prifddinas teleport",
                               "Trollheim teleport (tablet)"]),
    ]),
    section("ancient_tablets", "Ancient tablets", "rows", [
        all_("ancient_tablets", ["Paddewwa teleport (tablet)", "Senntisten teleport (tablet)",
                                 "Kharyrll teleport (tablet)", "Lassar teleport (tablet)", "Dareeyak teleport (tablet)",
                                 "Carrallanger teleport (tablet)", "Annakarl teleport (tablet)",
                                 "Ghorrock teleport (tablet)"]),
    ]),
    section("lunar_tablets", "Lunar tablets", "rows", [
        all_("lunar_tablets", ["Moonclan teleport (tablet)", "Ourania teleport (tablet)",
                               "Waterbirth teleport (tablet)", "Barbarian teleport (tablet)",
                               "Khazard teleport (tablet)", "Fishing guild teleport (tablet)",
                               "Catherby teleport (tablet)", "Ice plateau teleport (tablet)"]),
    ]),
    section("arceuus_tablets", "Arceuus tablets", "rows", [
        all_("arceuus_tablets", ["Arceuus library teleport (tablet)", "Draynor manor teleport (tablet)",
                                 "Battlefront teleport (tablet)", "Mind altar teleport (tablet)",
                                 "Salve graveyard teleport (tablet)", "Fenkenstrain's castle teleport (tablet)",
                                 "West ardougne teleport (tablet)", "Harmony island teleport (tablet)",
                                 "Cemetery teleport (tablet)", "Barrows teleport (tablet)",
                                 "Ape atoll teleport (tablet)"]),
    ]),
    # the master scroll book, then its scrolls in the book's own order
    section("scrolls", "Teleport scrolls", "rows", [
        all_("scrolls", ["Master scroll book", "Nardah teleport", "Digsite teleport", "Feldip hills teleport",
                         "Lunar isle teleport", "Mort'ton teleport", "Pest control teleport", "Piscatoris teleport",
                         "Tai bwo wannai teleport", "Iorwerth camp teleport", "Mos le'harmless teleport",
                         "Lumberyard teleport", "Zul-andra teleport", "Key master teleport", "Revenant cave teleport",
                         "Watson teleport", "Guthixian temple teleport", "Spider cave teleport",
                         "Colossal wyrm teleport scroll", "Chasm teleport scroll", "Ardeaglais teleport"]),
    ]),
    section("other_tablets", "Other single-use teleports", "rows", [
        # Mokhaiotl waystone, Calcified moth, Hallowed crystal shard, Dorgesh-kaan sphere (unique_uses.py)
        all_("consumable_teleports", []),
        # whatever else the tablet pattern finds (Wilderness crabs, Volcanic mine...); listed ones are skipped
        all_("other_tablets", TELEPORT_TABLETS),
    ]),
    section("runes", "Teleport runes", "rows", [
        first("pouch", RUNE_POUCH),
        # the basic teleport runes in the order players keep them: law, fire, water, air, earth;
        # then combination runes by Runecraft level, then the other catalytic runes by tier
        all_("runes", ["Law rune", "Fire rune", "Water rune", "Air rune", "Earth rune", "Mist rune", "Dust rune",
                       "Mud rune", "Smoke rune", "Steam rune", "Lava rune", "Astral rune", "Blood rune",
                       "Soul rune"]),
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
        # by Prayer experience, lowest first (tier order, like every other tab)
        all_("bones", ["Bones", "Wolf bones", "Monkey bones", "Bat bones", "Big bones", "Jogre bones",
                       "Wyrmling bones", "Zogre bones", "Shaikahan bones", "Babydragon bones", "Wyrm bones",
                       "Strykewyrm bones", "Dragon bones", "Wyvern bones", "Drake bones", "Fayrg bones",
                       "Lava dragon bones", "Raurg bones", "Frost dragon bones", "Hydra bones", "Dagannoth bones",
                       "Ourg bones", "Superior dragon bones"]),
    ]),
    section("blessed", "Blessed bones", "rows", [
        # by blessed bone shards, lowest first; the regex keeps any blessed bones added later
        all_("blessed", ["Blessed bones", "Blessed bat bones", "Blessed big bones", "Blessed zogre bones",
                         "Blessed babywyrm bones", "Blessed babydragon bones", "Blessed strykewyrm bones",
                         "Blessed wyrm bones", "Sun-kissed bones", "Blessed dragon bones", "Blessed wyvern bones",
                         "Blessed drake bones", "Blessed fayrg bones", "Blessed lava dragon bones",
                         "Blessed raurg bones", "Blessed frost dragon bones", "Blessed hydra bones",
                         "Dagannoth bones (blessed)", "Blessed ourg bones", "Blessed superior dragon bones",
                         "re:^Blessed [\\w' -]*bones$"]),
    ]),
    section("ashes", "Ashes", "rows", [
        # by Prayer experience, lowest first
        all_("ashes", ["Fiendish ashes", "Vile ashes", "Malicious ashes", "Abyssal ashes", "Infernal ashes"]),
    ]),
    section("heads", "Ensouled heads", "rows", [
        # wiki Prayer XP order, which also groups them by reanimation spell; the regex keeps new heads
        all_("heads", ["Ensouled goblin head", "Ensouled monkey head", "Ensouled imp head", "Ensouled minotaur head",
                       "Ensouled scorpion head", "Ensouled bear head", "Ensouled unicorn head", "Ensouled dog head",
                       "Ensouled chaos druid head", "Ensouled giant head", "Ensouled ogre head", "Ensouled elf head",
                       "Ensouled troll head", "Ensouled horror head", "Ensouled kalphite head",
                       "Ensouled dagannoth head", "Ensouled bloodveld head", "Ensouled tzhaar head",
                       "Ensouled demon head", "Ensouled hellhound head", "Ensouled aviansie head",
                       "Ensouled abyssal head", "Ensouled dragon head", "re:^Ensouled [\\w' -]+ head$"]),
    ]),
])

SLAYER = tab("slayer", "Slayer", "Enchanted gem", sections=[
    # empty all_(key, []) picks mark where a unique_uses.py group with that key goes
    section("gear", "Slayer gear", "rows", [
        first("helm", IMBUED_SLAYER_HELMS + SLAYER_HELMS + ["Black mask (i)", "Black mask"]),
        first("gem", ["Eternal gem", "Enchanted gem"]),
        first("ring", ["Slayer ring (eternal)", "Slayer ring"]),
        # silver bracelets in gem order: opal, then red topaz
        all_("bracelets", ["Expeditious bracelet", "Bracelet of slaughter"]),
        cape("Slayer cape"),
        all_("slayer_boost", []),
        # loot storage
        first("herb_sack", ["Silklined herb sack", "Herb sack"]),
        first("gem_bag", ["Gem bag"]),
        all_("seed_box", []),
        first("looting_bag", ["Looting bag"]),
        all_("soul_bearer", []),
        all_("chugging_barrel", []),
        # automatic bones, ashes and ectoplasm
        first("bonecrusher", ["Bonecrusher necklace", "Bonecrusher"]),
        first("sanctifier", ["Ash sanctifier"]),
        all_("ectoplasmator", []),
    ]),
    # worn protection, head to feet
    section("protection", "Task protection", "rows", [
        first("earmuffs", ["Earmuffs"], unless=["helm"]),
        first("nose_peg", ["Nose peg"], unless=["helm"]),
        first("face_mask", ["Facemask", "Gas mask"], unless=["helm"]),
        first("spiny", ["Spiny helmet"], unless=["helm"]),
        first("goggles", ["Reinforced goggles"], unless=["helm"]),
        all_("venom_immunity", []),
        first("witchwood", ["Witchwood icon"]),
        first("mirror", ["V's shield", "Mirror shield"]),
        all_("wyvern_shield", []),
        all_("gryphons", []),
        first("lantern", [("Bug lantern#Lit", "Lit and wielded: kills harpie bug swarms")]),
        all_("shayzien", []),
        all_("desert_heat", []),
        all_("revenants", []),
        first("slayer_gloves", ["Slayer gloves"]),
        first("insulated", ["Insulated boots"]),
        first("wyvern_boots", ["Boots of brimstone", "Boots of stone", "Granite boots"]),
    ]),
    # carried tools: light, finishers, lures and access, task weapons, consumables, boss helpers
    section("task_tools", "Task tools", "rows", [
        all_("light_source", []),
        first("gargoyle_finisher", ["Rock thrownhammer", "Granite hammer", "Rock hammer"]),
        first("rockslug_finisher", ["Bag of salt"]),
        first("ice", ["Ice cooler"]),
        first("fungicide", ["Fungicide spray"]),
        first("fungicide_refill", ["Fungicide"]),
        first("explosive", ["Fishing explosive"]),
        all_("molanisk", []),
        all_("warped_creatures", []),
        first("rope", ["Rope"]),
        all_("spade", []),
        first("leafbladed", ["Leaf-bladed battleaxe", "Leaf-bladed sword", "Leaf-bladed spear"]),
        all_("broad", []),  # Seeking broad arrows (unique_uses.py)
        first("broad_ammo", ["Amethyst broad bolts", "Broad bolts", "Broad arrows"]),
        all_("magic_dart", []),
        all_("vampyre_ring", []),
        # task consumables
        all_("goading", []),
        all_("bleed_cure", []),
        # boss helpers
        all_("giant_mole", []),
        all_("dks_solo", []),
        all_("gwd_key", []),
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
        # bait by the level it's used at, then the feathers, then Spirit flakes (an enhancer, not bait)
        all_("bait", ["Fishing bait", "Fish offcuts", "Raw karambwanji", "Sandworms", "Dark fishing bait",
                      "Feather", "Stripy feather", "Spirit flakes"]),
    ]),
])

HUNTER = tab("hunter", "Hunter", "Box trap", sections=[
    section("gear", "Traps & tools", "rows", [
        first("box_trap", ["Box trap"]),
        first("bird_snare", ["Bird snare"]),
        first("rabbit_snare", ["Rabbit snare"]),
        first("net", ["Magic butterfly net", "Butterfly net"]),
        first("impling_jar", ["Impling jar"]),
        first("butterfly_jar", ["Butterfly jar"]),
        first("noose", ["Noose wand"]),
        first("teasing", ["Teasing stick"]),
        first("rope", ["Rope"]),
        first("small_net", ["Small fishing net"]),
        first("knife", ["Knife"]),
        first("tinderbox", ["Tinderbox"]),
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
        *weight_reducing(),
        cape("Agility cape"),
        first("energy", ENERGY),
        first("endurance", ["Ring of endurance"]),
        first("pie", ["Summer pie"]),
        first("agility_potion", ["Agility potion"]),
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
    # altar order; the regex keeps any tiara or talisman added later
    section("tiaras", "Tiaras", "rows", [
        all_("tiaras", ["Elemental tiara", "Catalytic tiara", "Air tiara", "Mind tiara", "Water tiara",
                        "Earth tiara", "Fire tiara", "Body tiara", "Cosmic tiara", "Chaos tiara", "Astral tiara",
                        "Nature tiara", "Law tiara", "Death tiara", "Blood tiara", "Wrath tiara",
                        "re:^(?!Fancy|Gold)\\w+ tiara$"]),
    ]),
    section("talismans", "Talismans", "rows", [
        all_("talismans", ["Elemental talisman", "Catalytic talisman", "Air talisman", "Mind talisman",
                           "Water talisman", "Earth talisman", "Fire talisman", "Body talisman", "Cosmic talisman",
                           "Chaos talisman", "Nature talisman", "Law talisman", "Death talisman", "Blood talisman",
                           "Soul talisman", "Wrath talisman", "re:^(?!Ancient|Monkey)\\w+ talisman$"]),
    ]),
    section("essence", "Essence", "rows", [
        all_("essence", ["Rune essence", "Pure essence", "Daeyalt essence"]),
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
        # gold moulds, then silver, each by the level of what they make
        all_("moulds", ["Ring mould", "Necklace mould", "Bracelet mould", "Amulet mould", "Holy mould",
                        "Unholy mould", "Sickle mould", "Bolt mould", "Tiara mould"]),
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

# Herb chains in bands of 8 herbs: rows of grimy herbs, clean herbs, their main secondaries,
# unfinished potions and main potions, each herb in the same column of every row of its band. (clean herb, unfinished potion, secondary, potion) - the potion most
# players make from each herb; other secondaries and potions follow in the supply sections.
HERB_CHAINS = [
    ("Guam leaf", "Guam potion (unf)", "Eye of newt", "Attack potion"),
    ("Marrentill", "Marrentill potion (unf)", "Unicorn horn dust", "Antipoison"),
    ("Tarromin", "Tarromin potion (unf)", "Limpwurt root", "Strength potion"),
    ("Harralander", "Harralander potion (unf)", "Red spiders' eggs", "Restore potion"),
    ("Ranarr weed", "Ranarr potion (unf)", "Snape grass", "Prayer potion"),
    ("Toadflax", "Toadflax potion (unf)", "Crushed nest", "Saradomin brew"),
    ("Irit leaf", "Irit potion (unf)", "Eye of newt", "Super attack"),
    ("Avantoe", "Avantoe potion (unf)", "Mort myre fungus", "Super energy"),
    ("Kwuarm", "Kwuarm potion (unf)", "Limpwurt root", "Super strength"),
    ("Huasca", "Huasca potion (unf)", "Aldarium", "Prayer regeneration potion"),
    ("Snapdragon", "Snapdragon potion (unf)", "Red spiders' eggs", "Super restore"),
    ("Cadantine", "Cadantine potion (unf)", "White berries", "Super defence"),
    ("Lantadyme", "Lantadyme potion (unf)", "Dragon scale dust", "Antifire potion"),
    ("Dwarf weed", "Dwarf weed potion (unf)", "Wine of Zamorak", "Ranging potion"),
    ("Torstol", "Torstol potion (unf)", "Jangerberries", "Zamorak brew"),
]


def herb_columns():
    keys = [h for h, _, _, _ in HERB_CHAINS]

    def row(key, title, items, one=False):
        # repeat: a secondary shared by two herbs (eye of newt...) shows under both
        return section(key, title, "columns", [dict(all_(key, items), groups=keys, repeat=True, one=one)],
                       supplies=True)

    return [
        row("herbs_grimy", "Grimy herbs",
            [("Grimy " + h[0].lower() + h[1:], "Clean it for %s (%s)" % (pot.lower(), sec.lower()))
             for h, _, sec, pot in HERB_CHAINS]),
        row("herbs_clean", "Clean herbs",
            [(h, "Herb for %s, with %s" % (pot.lower(), sec.lower())) for h, _, sec, pot in HERB_CHAINS]),
        row("herbs_secondary", "Secondaries",
            [(sec, "Secondary for %s (with %s)" % (pot.lower(), h.lower())) for h, _, sec, pot in HERB_CHAINS]),
        row("herbs_unf", "Unfinished potions",
            [(unf, "Add %s to make %s" % (sec.lower(), pot.lower())) for _, unf, sec, pot in HERB_CHAINS]),
        # one: only your fullest dose sits in the column; other doses follow in Potions
        row("herbs_potion", "Potions",
            [(pot, "%s: %s + %s" % (pot, h.lower(), sec.lower())) for h, _, sec, pot in HERB_CHAINS], one=True),
    ]


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
    *herb_columns(),
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

# research groups that are consumables, not weapons, even though they have no slot
UNIQUE_SUPPLIES = ("spec_restore", "armadyl_brew")


def _unique_section(tab, group):
    key = tab["key"]
    if key in ("melee", "ranged", "magic"):
        if group["group"] in UNIQUE_SUPPLIES:
            return "supplies", "Supplies", "rows"
        if group.get("special"):
            return "specials", "Special attacks", "rows"
        if group.get("pieces"):
            return "situational", "Situational", "alts"
        if group["slot"] in (None, "WEAPON"):
            return "weapons", "Weapons", "rows"
        if group["slot"] == "AMMO" and key == "ranged":
            return "ammo", "Ammunition", "rows"
        return "situational", "Situational", "alts"
    if key == "movement":
        return group.get("section", "items"), "Teleport items", "rows"
    return group.get("section", "gear"), "Gear", "rows"


def _unique_pick(g):
    if g.get("pieces"):
        pick = set_(g["group"], [(pc["slot"], [tuple(i) for i in pc["items"]]) for pc in g["pieces"]], why=g["label"])
    else:
        maker = first if g["mode"] == "best" else all_
        pick = maker(g["group"], [tuple(i) for i in g["items"]], slot=g["slot"], why=g["label"])
    return pick


def apply_unique(tabs, groups):
    by_key = {t["key"]: t for t in tabs}
    for g in groups:
        tab = by_key[g["tab"]]
        pick = _unique_pick(g)
        replaced = False
        for s in tab["sections"]:
            for i, old in enumerate(s["picks"]):
                if old["key"] == g["group"] and not replaced:
                    # keep the hand-written conditions (unless / requires) of the pick it replaces
                    for k in ("unless", "requires", "placed", "take"):
                        if k in old and k not in pick:
                            pick[k] = old[k]
                    s["picks"][i] = pick
                    replaced = True
        if replaced:
            continue
        sec_key, title, layout = _unique_section(tab, g)
        section_ = next((s for s in tab["sections"] if s["key"] == sec_key), None)
        if section_ is None:
            section_ = section(sec_key, title, layout, [])
            tab["sections"].append(section_)
        section_["picks"].append(pick)
    # the catch-all special attack list goes after the researched specials so their reasons win
    for tab in tabs:
        for s in tab["sections"]:
            generic = [p for p in s["picks"] if p["key"] == "specs"]
            s["picks"] = [p for p in s["picks"] if p["key"] != "specs"] + generic


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
        # coral is the Sailing herb: coral + vial of water makes the coral unfinished potions
        ("Corals", r"(?i)^(?:Elkhorn|Pillar|Umbral) coral$", ["Elkhorn coral", "Pillar coral", "Umbral coral"]),
        # herb (unf) in herb order, then cadantine blood, coral, coconut milk, then other half-made potions;
        # Grym (Gauntlet) and Rejuvenation (Wintertodt) can't be banked
        ("Unfinished potions",
         r"\(unf\)$|^(?:Unfinished potion|Snakeweed mixture|Herb tea mix|Mixture - step [12]\(\d\))$",
         ["Guam potion (unf)", "Marrentill potion (unf)", "Tarromin potion (unf)", "Harralander potion (unf)",
          "Ranarr potion (unf)", "Toadflax potion (unf)", "Irit potion (unf)", "Avantoe potion (unf)",
          "Kwuarm potion (unf)", "Huasca potion (unf)", "Snapdragon potion (unf)", "Cadantine potion (unf)",
          "Lantadyme potion (unf)", "Dwarf weed potion (unf)", "Torstol potion (unf)",
          "Cadantine blood potion (unf)", "Elkhorn potion (unf)", "Pillar potion (unf)", "Umbral potion (unf)",
          "Antidote+ (unf)", "Antidote++ (unf)", "Weapon poison+ (unf)", "Weapon poison++ (unf)",
          "Unfinished potion", "Snakeweed mixture", "Herb tea mix", "Guthix balance (unf)",
          "Mixture - step 1(3)", "Mixture - step 2(3)", "Grym potion (unf)", "Rejuvenation potion (unf)"]),
        ("Vials", r"^(Vial|Vial of water|Vial of blood|Coconut milk)$"),
        # by the Herblore level that makes them; names are the first version's in-game name, so "(3)"
        ("Potions", r"\(\d\)$|\(\d\) ?$|potion|brew|restore|antidote|antifire|anti-venom|antipoison",
         ["Attack potion(3)", "Antipoison(3)", "Relicym's balm(3)", "Strength potion(3)", "Serum 207 (3)",
          "Restore potion(3)", "Energy potion(3)", "Defence potion(3)", "Agility potion(3)", "Combat potion(3)",
          "Prayer potion(3)", "Super attack(3)", "Superantipoison(3)", "Fishing potion(3)", "Super energy(3)",
          "Hunter potion(3)", "Super strength(3)", "Super restore(3)", "Super defence(3)", "Antidote+(3)",
          "Antifire potion(3)", "Ranging potion(3)", "Magic potion(3)", "Stamina potion(3)", "Zamorak brew(3)",
          "Antidote++(3)", "Bastion potion(3)", "Battlemage potion(3)", "Extended antifire(3)", "Ancient brew(3)",
          "Anti-venom(3)", "Super combat potion(3)", "Super antifire potion(3)", "Anti-venom+(3)",
          "Extended super antifire(3)", "Olive oil(3)", "Elder potion (3)", "Kodai potion (3)",
          "Twisted potion (3)"]),
        # Mastering Mixology: the three pastes, then the mixes by level
        ("Mixology", r"^(?:Mox paste|Aga paste|Lye paste|Mammoth-might mix|Alco-augmentator|Liplack liquor|"
                     r"Mystic mana amalgam|Marley's moonlight|Azure aura mix|Aqualux amalgam|Megalite liquid|"
                     r"Anti-leech lotion|Mixalot)$",
         ["Mox paste", "Aga paste", "Lye paste", "Mammoth-might mix", "Alco-augmentator", "Liplack liquor",
          "Mystic mana amalgam", "Marley's moonlight", "Azure aura mix", "Aqualux amalgam", "Megalite liquid",
          "Anti-leech lotion", "Mixalot"]),
        ("Imp repellent", r"^(?:Anchovy oil|Marigolds|Rosemary|Nasturtiums|"
                          r"(?:Red|Orange|Yellow|Blue|Purple|White|Black|Mixed|Assorted) flowers)$",
         ["Anchovy oil", "Marigolds", "Rosemary", "Nasturtiums", "Red flowers", "Orange flowers", "Yellow flowers",
          "Blue flowers", "Purple flowers", "White flowers", "Black flowers", "Mixed flowers", "Assorted flowers"]),
        # the catch-all must stay last: rules after ".*" never receive items
        ("Secondaries", r".*"),
    ]),
    # rules without an order list stay 2-tuples (supply_sections checks len(rule) > 2)
    ("Crafting", "crafting", [
        ("Uncut gems", r"(?i)^Uncut (?:%s)$" % "|".join(GEMS), GEMS),
        ("Cut gems", r"(?i)^(?:%s)$" % "|".join(GEMS), GEMS),
        ("Leather & hides", r"(?i)^(%s)$" % "|".join(re.escape(x) for x in LEATHERS), LEATHERS),
        # inputs to output: sand, soda ash and the seaweeds, then molten glass
        ("Glass", r"(?i)^(Molten glass|Bucket of sand|Soda ash|Seaweed|Giant seaweed|Swamp weed|Sandstone \(\d+kg\))$",
         ["Bucket of sand", "Soda ash", "Seaweed", "Giant seaweed", "Swamp weed", "Molten glass"]),
        ("Silver jewellery", r"^(%s)$" % "|".join(re.escape(x) for x in SILVER), SILVER),
        ("Gold jewellery", r"^(%s)$" % "|".join(re.escape(x) for x in GOLD), GOLD),
        ("Battlestaves & orbs", r"(?i)^(Battlestaff|(Air|Water|Earth|Fire) orb|Unpowered orb)$"),
        # each yarn sits in the same column as the fibre it is spun from; jute is woven, not spun
        ("Fibres", r"(?i)^(Wool|Flax|Hemp|Cotton boll|Jute fibre)$", ["Wool", "Flax", "Hemp", "Cotton boll", "Jute fibre"]),
        ("Yarn", r"(?i)^(Ball of wool|Linen yarn|Hemp yarn|Cotton yarn)$",
         ["Ball of wool", "Linen yarn", "Hemp yarn", "Cotton yarn"]),
        ("Pottery", r"(?i)^(Clay|Soft clay|Unfired .*)$",
         ["Clay", "Soft clay", "Unfired pot", "Unfired cup", "Unfired pie dish", "Unfired bowl",
          "Unfired plant pot", "Unfired pot lid"]),
    ]),
    ("Fletching", "fletching", [
        ("Logs", r"(?i)^(Logs|(?!Sap-treated).* logs)$",
         ["Logs", "Oak logs", "Willow logs", "Teak logs", "Maple logs", "Mahogany logs", "Arctic pine logs", "Yew logs",
          "Camphor logs", "Magic logs", "Ironwood logs", "Redwood logs", "Rosewood logs", "Achey tree logs",
          "Scrapey tree logs"]),
        ("Shortbows", r"(?i)^(Bow string|.*shortbow \(u\))$",
         ["Bow string", "Shortbow (u)", "Oak shortbow (u)", "Willow shortbow (u)", "Maple shortbow (u)",
          "Yew shortbow (u)", "Magic shortbow (u)"]),
        ("Longbows", r"(?i)^.*longbow \(u\)$",
         ["Longbow (u)", "Oak longbow (u)", "Willow longbow (u)", "Maple longbow (u)", "Yew longbow (u)",
          "Magic longbow (u)"]),
        ("Crossbow stocks", r"(?i)stock$",
         ["Wooden stock", "Oak stock", "Willow stock", "Teak stock", "Maple stock", "Mahogany stock", "Yew stock",
          "Magic stock"]),
        ("Crossbow limbs", r"(?i)limbs$",
         ["Bronze limbs", "Blurite limbs", "Iron limbs", "Steel limbs", "Mithril limbs", "Adamantite limbs",
          "Runite limbs", "Dragon limbs", "Ballista limbs"]),
        ("Crossbows", r"(?i)^(Crossbow string|.*crossbow \(u\))$",
         ["Crossbow string", "Bronze crossbow (u)", "Blurite crossbow (u)", "Iron crossbow (u)", "Steel crossbow (u)",
          "Mithril crossbow (u)", "Adamant crossbow (u)", "Runite crossbow (u)", "Dragon crossbow (u)"]),
        ("Arrow shafts", r"(?i)^(Arrow shaft|Feather|Headless arrow)$",
         ["Arrow shaft", "Feather", "Headless arrow"]),
        ("Arrowtips", r"(?i)^(.* arrowtips|Broad arrowheads)$",
         ["Bronze arrowtips", "Iron arrowtips", "Steel arrowtips", "Mithril arrowtips", "Adamant arrowtips",
          "Rune arrowtips", "Amethyst arrowtips", "Dragon arrowtips", "Broad arrowheads", "Wolfbone arrowtips"]),
        ("Arrows", r"(?i)^(Bronze|Iron|Steel|Mithril|Adamant|Rune|Amethyst|Dragon) arrow$",
         ["Bronze arrow", "Iron arrow", "Steel arrow", "Mithril arrow", "Adamant arrow", "Rune arrow", "Amethyst arrow",
          "Dragon arrow"]),
        ("Unfinished bolts", r"(?i)bolts? ?\(unf\)$|^Unfinished broad bolts$",
         ["Bronze bolts (unf)", "Blurite bolts (unf)", "Iron bolts (unf)", "Steel bolts (unf)", "Mithril bolts (unf)",
          "Adamant bolts(unf)", "Runite bolts (unf)", "Dragon bolts (unf)", "Unfinished broad bolts",
          "Silver bolts (unf)"]),
        ("Bolts", r"(?i)^(Bronze|Blurite|Iron|Steel|Mithril|Adamant|Runite|Dragon|Silver|Broad) bolts$",
         ["Bronze bolts", "Blurite bolts", "Iron bolts", "Steel bolts", "Mithril bolts", "Adamant bolts",
          "Runite bolts", "Dragon bolts", "Broad bolts", "Silver bolts"]),
        # the Crafting gem order, then the non-gem tips by level
        ("Bolt tips", r"(?i)bolt ?tips$",
         ["Opal bolt tips", "Jade bolt tips", "Topaz bolt tips", "Sapphire bolt tips", "Emerald bolt tips",
          "Ruby bolt tips", "Diamond bolt tips", "Dragonstone bolt tips", "Onyx bolt tips", "Pearl bolt tips",
          "Barb bolttips", "Amethyst bolt tips"]),
        ("Dart tips", r"(?i)^(?!Atlatl ).*dart tips?$",
         ["Bronze dart tip", "Iron dart tip", "Steel dart tip", "Mithril dart tip", "Adamant dart tip", "Rune dart tip",
          "Amethyst dart tip", "Dragon dart tip"]),
        ("Javelins", r"(?i)javelin tips$|^Javelin shaft$",
         ["Javelin shaft", "Bronze javelin tips", "Iron javelin tips", "Steel javelin tips", "Mithril javelin tips",
          "Adamant javelin tips", "Rune javelin tips", "Amethyst javelin tips", "Dragon javelin tips"]),
        ("Atlatl darts", r"(?i)^(?:Headless )?atlatl dart",
         ["Atlatl dart shaft", "Headless atlatl dart", "Atlatl dart tips"]),
    ]),
    ("Smithing", "smithing", [
        # metal tier (Mining and smelting level), so ores line up with the bars
        ("Ores", r"(?i) ore$|^Coal$",
         ["Copper ore", "Tin ore", "Blurite ore", "Iron ore", "Silver ore", "Lead ore", "Coal", "Gold ore",
          "Lovakite ore", "Mithril ore", "Adamantite ore", "Nickel ore", "Runite ore"]),
        ("Bars", r"(?i) bar$"),
    ]),
    ("Cooking", "cooking", [
        # by Cooking level; then crab meat, Camdozaal offering fish, and minigame-only fish
        ("Raw fish", r"(?i)^Raw (?:shrimps|sardine|anchovies|herring|mackerel|trout|cod|pike|salmon|slimy eel|tuna|"
                     r"karambwan|rainbow fish|cave eel|lobster|bass|swordfish|swordtip squid|monkfish|giant krill|"
                     r"jumbo squid|haddock|yellowfin|shark|sea turtle|halibut|anglerfish|bluefin|dark crab|manta ray|"
                     r"marlin|red crab meat|blue crab meat|rainbow crab meat|guppy|cavefish|tetra|catfish|"
                     r"harpoonfish|paddlefish)$",
         ["Raw shrimps", "Raw sardine", "Raw anchovies", "Raw herring", "Raw mackerel", "Raw trout", "Raw cod",
          "Raw pike", "Raw salmon", "Raw slimy eel", "Raw tuna", "Raw karambwan", "Raw rainbow fish",
          "Raw cave eel", "Raw lobster", "Raw bass", "Raw swordfish", "Raw swordtip squid", "Raw monkfish",
          "Raw giant krill", "Raw jumbo squid", "Raw haddock", "Raw yellowfin", "Raw shark", "Raw sea turtle",
          "Raw halibut", "Raw anglerfish", "Raw bluefin", "Raw dark crab", "Raw manta ray", "Raw marlin",
          "Raw red crab meat", "Raw blue crab meat", "Raw rainbow crab meat",
          "Raw guppy", "Raw cavefish", "Raw tetra", "Raw catfish", "Raw harpoonfish", "Raw paddlefish"]),
        # plain meats, then skewer/ogre meats, then hunter meats by Cooking level
        ("Raw meat", r"(?i)^Raw (?:beef|rat meat|bear meat|yak meat|boar meat|impaler meat|ugthanki meat|chicken|"
                     r"rabbit|mystery meat|bird meat|t-bone steak|beast meat|chompy|jubbly|oomlie|wild kebbit|"
                     r"larupia|barb-tailed kebbit|graahk|kyatt|pyre fox|sunlight antelope|dashing kebbit|"
                     r"moonlight antelope)$",
         ["Raw beef", "Raw rat meat", "Raw bear meat", "Raw yak meat", "Raw boar meat", "Raw impaler meat",
          "Raw ugthanki meat", "Raw chicken", "Raw rabbit", "Raw mystery meat", "Raw bird meat",
          "Raw t-bone steak", "Raw beast meat", "Raw chompy", "Raw jubbly", "Raw oomlie",
          "Raw wild kebbit", "Raw larupia", "Raw barb-tailed kebbit", "Raw graahk", "Raw kyatt", "Raw pyre fox",
          "Raw sunlight antelope", "Raw dashing kebbit", "Raw moonlight antelope"]),
        # pies sort by the level that bakes them
        ("Pies", r"(?i)^(?:Raw|Uncooked) .*pie$"),
        # everything else the old "Raw food" rule took
        ("Other dishes", r"(?i)^Raw (?!.*\(\d\)$)|^Uncooked ",
         ["Uncooked egg", "Uncooked stew", "Uncooked pizza", "Uncooked cake", "Uncooked curry",
          "Raw crunchies", "Raw batta", "Raw gnomebowl", "Raw swamp paste"]),
    ]),
    ("Firemaking", "firemaking", [
        # every log has a level-1 pyre recipe, so order by the level that burns them
        ("Logs", r"(?i)^(Logs|.* logs)$",
         ["Logs", "Oak logs", "Willow logs", "Teak logs", "Arctic pine logs", "Maple logs", "Mahogany logs", "Yew logs",
          "Camphor logs", "Magic logs", "Ironwood logs", "Redwood logs", "Rosewood logs"]),
    ]),
    ("Construction", "construction", [
        ("Planks", r"(?i)plank$",
         ["Plank", "Oak plank", "Teak plank", "Mahogany plank", "Camphor plank", "Ironwood plank", "Rosewood plank"]),
        ("Nails", r"(?i)nails$",
         ["Bronze nails", "Iron nails", "Steel nails", "Black nails", "Mithril nails", "Adamantite nails", "Rune nails",
          "Dragon nails"]),
        ("Materials", r"(?i)^(Bolt of cloth|Limestone brick|Marble block|Gold leaf|Magic stone|Soft clay|Steel bar|"
                      r"Molten glass|Clockwork|Timber beam)$"),
    ]),
    # one section per patch type, each by Farming level; saplings sit by their seedlings
    ("Farming", "farming", [
        ("Allotment seeds", r"^(?:Potato|Onion|Cabbage|Tomato|Sweetcorn|Strawberry|Watermelon|Snape grass) seed$"),
        ("Flower seeds", r"^(?:Marigold|Rosemary|Nasturtium|Woad|Limpwurt|White lily) seed$"),
        ("Herb seeds", r"^(?:Guam|Marrentill|Tarromin|Harralander|Ranarr|Toadflax|Irit|Avantoe|Kwuarm|Snapdragon|Huasca|"
                       r"Cadantine|Lantadyme|Dwarf weed|Torstol) seed$|^Gout tuber$"),
        ("Hops seeds", r"^(?:Barley|Hammerstone|Asgarnian|Jute|Yanillian|Flax|Krandorian|Wildblood|Hemp|Cotton) seed$"),
        ("Bush seeds", r"^(?:Redberry|Cadavaberry|Dwellberry|Jangerberry|Whiteberry|Poison ivy) seed$"),
        ("Tree seeds", r"^(?:Acorn|Willow seed|Maple seed|Yew seed|Magic seed)$"),
        ("Fruit tree seeds", r"^(?:Apple tree|Banana tree|Orange tree|Curry tree|Pineapple|Papaya tree|Palm tree|"
                             r"Dragonfruit tree) seed$"),
        ("Hardwood seeds", r"^(?:Teak|Mahogany|Camphor|Ironwood|Rosewood) seed$"),
        ("Special tree seeds", r"^(?:Calquat tree seed|Crystal acorn|Spirit seed|Celastrus seed|Redwood tree seed)$"),
        ("Special seeds", r"(?i)seed$|seeds$|spore$|^(?:Elkhorn|Pillar|Umbral) frag$",
         ["Seaweed spore", "Grape seed", "Mushroom spore", "Cactus seed", "Potato cactus seed", "Belladonna seed",
          "Attas seed", "Iasor seed", "Kronos seed", "Elkhorn frag", "Pillar frag", "Umbral frag"]),
        ("Tree saplings", r"^(?:Oak|Willow|Maple|Yew|Magic) (?:sapling|seedling)$"),
        ("Fruit tree saplings", r"^(?:Apple|Banana|Orange|Curry|Pineapple|Papaya|Palm|Dragonfruit) (?:sapling|seedling)$"),
        ("Hardwood saplings", r"^(?:Teak|Mahogany|Camphor|Ironwood|Rosewood) (?:sapling|seedling)$"),
        ("Special tree saplings", r"(?i)sapling$|seedling$"),
    ]),
    ("Runecraft", "runecraft", [
        ("Runes", r"(?i) rune$"),
    ]),
    # wood tier (normal to rosewood) and metal tier (bronze to dragon), so the plank, hull, bar, keel and
    # nail rows line up column by column; raw materials come before what is made from them
    ("Sailing", "sailing", [
        ("Planks", r"(?i)plank$"),
        ("Hull parts", r"(?i)^(?!Large ).* hull parts$",
         ["Wooden hull parts", "Oak hull parts", "Teak hull parts", "Mahogany hull parts", "Camphor hull parts",
          "Ironwood hull parts", "Rosewood hull parts"]),
        ("Large hull parts", r"(?i)^Large .* hull parts$",
         ["Large wooden hull parts", "Large oak hull parts", "Large teak hull parts", "Large mahogany hull parts",
          "Large camphor hull parts", "Large ironwood hull parts", "Large rosewood hull parts"]),
        ("Bars & sheets", r"(?i)^(?!Lead |Cupronickel ).* bar$|sheet$",
         ["Bronze bar", "Iron bar", "Steel bar", "Mithril bar", "Adamantite bar", "Runite bar", "Dragon metal sheet"]),
        ("Keel parts", r"(?i)^(?!Large ).* keel parts$",
         ["Bronze keel parts", "Iron keel parts", "Steel keel parts", "Mithril keel parts", "Adamant keel parts",
          "Rune keel parts", "Dragon keel parts"]),
        ("Large keel parts", r"(?i)^Large .* keel parts$",
         ["Large bronze keel parts", "Large iron keel parts", "Large steel keel parts", "Large mithril keel parts",
          "Large adamant keel parts", "Large rune keel parts", "Large dragon keel parts"]),
        ("Nails", r"(?i) nails$",
         ["Bronze nails", "Iron nails", "Steel nails", "Mithril nails", "Adamantite nails", "Rune nails", "Dragon nails"]),
        ("Tar, lead & cupronickel", r"(?i)^(Swamp tar|Lead bar|Cupronickel bar)$",
         ["Swamp tar", "Lead bar", "Cupronickel bar"]),
        ("Yarn & sail cloth", r"(?i)^(Bolt of (linen|canvas|cotton)|.* yarn)$",
         ["Linen yarn", "Hemp yarn", "Cotton yarn", "Bolt of linen", "Bolt of canvas", "Bolt of cotton"]),
        ("Fabric rolls", r"(?i) fabric roll$", ["Ralph's fabric roll", "Gurtob's fabric roll", "Gwyna's fabric roll"]),
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

# Never in a best-in-slot set, but fine in situational picks such as the prayer gear set:
# god books and blessings win on prayer bonus, and Ardougne cloaks are teleports.
SITUATIONAL_ONLY = [
    "re:^(Holy book|Unholy book|Book of (Balance|Law|War|Darkness)|Damaged book)",
    r"re:^Ardougne (cloak \d|max cape)",
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


# ---------------------------------------------------------------- tab icons
# Each tab's icon is the first of these the player owns (most iconic first); the
# combat tabs use their best weapon instead. Tabs not listed (movement: your graceful) use their first item.
RAW_FISH = ["Raw anglerfish", "Raw dark crab", "Raw manta ray", "Raw sea turtle", "Raw shark", "Raw monkfish",
            "Raw swordfish", "Raw lobster", "Raw bass", "Raw tuna", "Raw salmon", "Raw trout", "Raw shrimps"]
TAB_ICONS = {
    "prayer": ["Superior dragon bones", "Ourg bones", "Dagannoth bones", "Hydra bones", "Frost dragon bones",
               "Raurg bones", "Lava dragon bones", "Fayrg bones", "Drake bones", "Dragon bones", "Wyvern bones",
               "Babydragon bones", "Big bones", "Bones", "Infernal ashes", "Abyssal ashes", "Malicious ashes",
               "Vile ashes", "Fiendish ashes", "Prayer cape"],
    "slayer": IMBUED_SLAYER_HELMS + SLAYER_HELMS + ["Black mask (i)", "Black mask", "Eternal gem", "Enchanted gem"],
    "agility": ["Mark of grace"],
    "thieving": ["Rogue mask", "Thieving cape", "Lockpick", "Dodgy necklace", "Gloves of silence"],
    "herblore": [h for herb in reversed(HERBS) for h in (herb, "Grimy " + herb[0].lower() + herb[1:])]
    + ["Pestle and mortar"],
    "crafting": [g for gem in reversed(GEMS) for g in (gem, "Uncut " + gem.lower())]
    + ["Black dragon leather", "Red dragon leather", "Blue dragon leather", "Green dragon leather", "Needle", "Chisel"],
    "fletching": ["Magic longbow (u)", "Magic shortbow (u)", "Yew longbow (u)", "Yew shortbow (u)",
                  "Maple longbow (u)", "Dragon arrowtips", "Amethyst arrowtips", "Rune arrowtips", "Magic logs",
                  "Yew logs", "Fletching knife", "Knife"],
    "hunter": ["Box trap", "Bird snare", "Magic butterfly net", "Butterfly net", "Hunter cape"],
    "smithing": ["Runite bar", "Adamantite bar", "Mithril bar", "Steel bar", "Iron bar", "Bronze bar", "Runite ore",
                 "Adamantite ore", "Mithril ore", "Coal", "Iron ore", "Imcando hammer", "Hammer"],
    "fishing": ["Lobster pot"],
    "cooking": [f for f, _ in FOOD] + RAW_FISH + ["Cooking gauntlets"],
    "firemaking": ["Redwood logs", "Magic logs", "Yew logs", "Mahogany logs", "Maple logs", "Teak logs",
                   "Willow logs", "Oak logs", "Logs", "Tinderbox"],
    # always a seed: your best tree or herb seed, else any seed you have
    "farming": ["Magic seed", "Yew seed", "Torstol seed", "Snapdragon seed", "Ranarr seed", "Maple seed",
                "Dwarf weed seed", "Lantadyme seed", "Cadantine seed", "Kwuarm seed", "Avantoe seed", "Irit seed",
                "Toadflax seed", "Willow seed", "Acorn", r"re:^(?!.*\().* seed$"],
    "runecraft": ["Rune essence"],
    "construction": ["Mahogany plank", "Teak plank", "Oak plank", "Plank", "Saw", "Hammer"],
    "sailing": ["Gwyna's fabric roll", "Gurtob's fabric roll", "Ralph's fabric roll"],
    "cosmetics": ["Red partyhat", "Blue partyhat", "Purple partyhat", "Green partyhat", "Yellow partyhat",
                  "White partyhat", "Santa hat", "Red halloween mask", "Blue halloween mask", "Green halloween mask"],
}
# what the icon falls back to when the player owns none of the above
DEFAULT_ICONS = {"cooking": "Shark", "herblore": "Ranarr weed", "crafting": "Uncut diamond", "fletching": "Yew longbow (u)",
                 "smithing": "Steel bar", "firemaking": "Logs", "prayer": "Dragon bones", "fishing": "Lobster pot",
                 "construction": "Oak plank", "farming": "Ranarr seed", "runecraft": "Rune essence",
                 "sailing": "Gwyna's fabric roll",
                 "agility": "Mark of grace"}
# tabs whose icon falls back to the first item shown rather than the default icon
ICON_FALLBACK_TO_FIRST_ITEM = {"cosmetics"}


# ---------------------------------------------------------------- finished goods
# Every skill tab ends with the things the skill makes, after a blank row. For
# skills with recipes they come from the wiki recipe data (generate_data.py
# goods_sections), grouped by the supply section they are made from. Gathering
# skills have no recipes, so their goods are listed here, best tier last.
def _raw_fish_order():
    for skill, _, rules in SUPPLY_RULES:
        if skill == "Cooking":
            for rule in rules:
                if rule[0] == "Raw fish":
                    return list(rule[2])
    return []


GOODS_LISTS = {
    "woodcutting": [("Logs", ["Logs", "Oak logs", "Willow logs", "Teak logs", "Maple logs", "Arctic pine logs",
                              "Mahogany logs", "Camphor logs", "Yew logs", "Ironwood logs", "Magic logs",
                              "Redwood logs", "Rosewood logs"]),
                    ("Bird nests", ["Bird nest (seeds)", "Bird nest (ring)", "Bird nest (egg)", "Bird nest (empty)"])],
    "mining": [("Ores", ["Clay", "Copper ore", "Tin ore", "Blurite ore", "Iron ore", "Silver ore", "Lead ore", "Coal",
                         "Gold ore", "Lovakite ore", "Mithril ore", "Adamantite ore", "Nickel ore", "Runite ore",
                         "Amethyst"]),
               ("Gems", ["Uncut opal", "Uncut jade", "Uncut red topaz", "Uncut sapphire", "Uncut emerald",
                         "Uncut ruby", "Uncut diamond"]),
               ("Essence", ["Rune essence", "Pure essence", "Daeyalt essence"]),
               ("Other", [r"re:^Sandstone \(\d+kg\)$", "Granite", "Volcanic ash"])],
    "fishing": [("Raw fish", _raw_fish_order())],
    "hunter": [("Chinchompas", ["Chinchompa", "Red chinchompa", "Black chinchompa"]),
               ("Meat", ["Raw rabbit", "Raw bird meat", "Raw beast meat", "Raw wild kebbit", "Raw barb-tailed kebbit",
                         "Raw dashing kebbit", "Raw larupia", "Raw graahk", "Raw kyatt", "Raw pyre fox",
                         "Raw sunlight antelope", "Raw moonlight antelope"]),
               ("Furs & feathers", [r"re:^(?!Bear|Grey wolf|Wolf)[A-Z][\w' -]* fur$", "Red feather", "Yellow feather",
                                    "Orange feather", "Blue feather", "Stripy feather"]),
               ("Implings", [r"re:^[A-Z]\w* impling jar$"])],
}
# supply sections that are really finished goods (moved to the goods area)
GOODS_FROM_SUPPLIES = {"herblore": ["Potions"]}
# never finished goods: failed products, and raid/minigame-only items you can't bank
EXCLUDE_GOODS = r"(?i)^(Burnt |Corrupted |Crystal paddlefish|Paddlefish|Token|Bridge section|Pipe section|Lumber patch|"                 r"Servery |Guanic bat|Prael bat|Giral bat|Phluxia bat|Kryket bat|Murng bat|Psykk bat|Pysk fish|"                 r"Suphi fish|Leckish fish|Brawk fish|Mycil fish|Roqed fish|Kyren fish|Refined tephra|.*\(Gauntlet\)|"                 r"Blade of Saeldor|Bow of Faerdhinen|Crystal (?:shield|halberd|helm|body|legs|bow|crown)|Godsword|"                 r"Zombie (?:axe|helmet)|Belle's folly|Torva |Elemental (?:helmet|shield)|"                 r"Mind (?:helmet|shield)|Enhanced crystal key|Escape crystal|Guardian essence|Bronze key)"
