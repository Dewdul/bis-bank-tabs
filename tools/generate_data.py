#!/usr/bin/env python3
"""Build tabs.json for the No Brain Bank Tabs RuneLite plugin.

Resolves the hand-curated tab definitions in curated.py (OSRS Wiki page
names) to item ids, adds per-skill supplies from the wiki's recipe data, and
writes src/main/resources/com/ultimatebanktabs/tabs.json. The plugin never
touches the network; run this before each release.

Data (c) OSRS Wiki contributors, CC BY-NC-SA 3.0. RuneLite's
item_variations.json (BSD-2) is used to add ornament/charge variants.

Stdlib only. Usage:
  python tools/generate_data.py            # use cached wiki data if present
  python tools/generate_data.py --refresh  # re-download everything
"""
import json
import re
import subprocess
import sys
import urllib.request
from collections import defaultdict
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import curated  # noqa: E402
import wiki_recipes  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
CACHE = Path(__file__).resolve().parent / "cache"
OUT = ROOT / "src/main/resources/com/ultimatebanktabs/tabs.json"
VARIATIONS_URL = "https://raw.githubusercontent.com/runelite/runelite/master/runelite-client/src/main/resources/item_variations.json"

# Pages that are never real bank items (minigame/arena loadouts, betas, leagues echoes...)
SKIP_PAGE = re.compile(r"Last Man Standing|\(beta\)|unobtainable|\(Deadman|\(deadman\)|\(nz\)|\(bh\)|^Echo (?!boots|crystal)|"
                       r"interface item|animation item|\(The Gauntlet\)|Emir's Arena|\(Leagues|Clue scroll|"
                       r"\(discontinued\)|note$|"
                       r"^(?:Corrupted|Crystal) (?:body|helm|legs|bow|halberd|staff) \((?:basic|attuned|perfected)\)$")

REFRESH = "--refresh" in sys.argv


def cached(name, fetch):
    path = CACHE / name
    if path.exists() and not REFRESH:
        return json.loads(path.read_text(encoding="utf-8"))
    data = fetch()
    CACHE.mkdir(exist_ok=True)
    path.write_text(json.dumps(data), encoding="utf-8")
    return data


def fetch_items():
    return wiki_recipes.fetch_all("infobox_item", ["page_name", "page_name_sub", "item_name", "item_id", "removal_date", "quest",
                                                   "tradeable"])


def fetch_variations():
    req = urllib.request.Request(VARIATIONS_URL, headers={"User-Agent": wiki_recipes.USER_AGENT})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def fetch_recipes():
    path = CACHE / "recipes.json"
    if REFRESH or not path.exists():
        subprocess.run([sys.executable, str(Path(wiki_recipes.__file__))], check=True)
    return json.loads(path.read_text(encoding="utf-8"))


class Wiki:
    def __init__(self, rows, skip=True):
        self.page_ids = defaultdict(list)   # lower page name -> ids
        self.page_name = {}                 # lower -> display page name
        self.id_name = {}                   # id -> in-game item name
        self.id_page = {}                   # id -> lower page name
        self.version_ids = defaultdict(list)  # lower "Page#Version" -> ids
        self.id_version = {}                # id -> lower "Page#Version" (versioned pages only)
        self.quest_only = set()             # lower page names of untradeable quest items
        for r in rows:
            page = r.get("page_name") or ""
            if not page or (skip and (r.get("removal_date") or SKIP_PAGE.search(page))):
                continue
            key = page.lower()
            self.page_name[key] = page
            if r.get("quest") not in (None, "", "No") and "tradeable" not in r:
                self.quest_only.add(key)
            for raw in r.get("item_id") or []:
                try:
                    item_id = int(str(raw).strip())
                except ValueError:
                    continue
                if item_id not in self.page_ids[key]:
                    self.page_ids[key].append(item_id)
                self.id_name.setdefault(item_id, r.get("item_name") or page)
                self.id_page.setdefault(item_id, key)
                sub = (r.get("page_name_sub") or "").lower()
                if "#" in sub:
                    if item_id not in self.version_ids[sub]:
                        self.version_ids[sub].append(item_id)
                    self.id_version.setdefault(item_id, sub)
        self.pages = sorted(self.page_name.values())

    def ids_of_page(self, page):
        return list(self.page_ids.get(page.lower(), []))


class Resolver:
    def __init__(self, wiki, variations):
        self.wiki = wiki
        self.var_group = {}
        for ids in variations.values():
            for i in ids:
                self.var_group[i] = ids
        self.errors = []

    def entry_pages(self, name):
        if name.startswith("re:"):
            rx = re.compile(name[3:])
            # pages without item ids (set pages, minigame items) can't be owned
            pages = [p for p in self.wiki.pages if rx.search(p) and self.wiki.page_ids.get(p.lower())]
            if not pages:
                self.errors.append("regex matched nothing: %s" % name)
            return pages
        if "#" in name:
            # some versions of a page, not every version on it
            if not self.versions(name):
                self.errors.append("unknown version: %s" % name)
                return []
            return [name]
        if name.lower() not in self.wiki.page_ids:
            self.errors.append("unknown page: %s" % name)
            return []
        return [self.wiki.page_name[name.lower()]]

    def versions(self, name):
        """The versions a "Page#Version" entry means: that one ("Mining helmet#Lit"), or with
        "Page#re:<regex>" every version whose name matches ("Abyssal lantern#re:^(?!Unlit)")."""
        page, _, version = name.lower().partition("#")
        if not version.startswith("re:"):
            return [name.lower()] if name.lower() in self.wiki.version_ids else []
        rx = re.compile(version[3:], re.I)
        return [k for k in self.wiki.version_ids if k.partition("#")[0] == page and rx.search(k.partition("#")[2])]

    def base_ids(self, name):
        if "#" in name and not name.startswith("re:"):
            ids = []
            for v in self.versions(name):
                ids += [i for i in self.wiki.version_ids[v] if i not in ids]
            if not ids:
                self.errors.append("unknown version: %s" % name)
            return ids
        ids = []
        for p in self.entry_pages(name):
            ids += [i for i in self.wiki.ids_of_page(p) if i not in ids]
        return ids

    def entries(self, names, split_regex=False):
        """Resolve an ordered list of entry names to id lists, expanding each with
        RuneLite variation ids that don't belong to another entry in the list.
        With split_regex, every page a regex matches becomes its own entry."""
        return [ids for ids, _ in self.entries_with_reasons(names, split_regex)]

    def entries_with_reasons(self, names, split_regex=False):
        """As entries(), for names that may be (name, hover reason) pairs;
        returns (ids, reason or None) per entry."""
        seen, unique = set(), []
        for n in names:
            n, why = n if isinstance(n, tuple) else (n, None)
            if split_regex and n.startswith("re:"):
                expanded = self.entry_pages(n)
            else:
                expanded = [n]
            for e in expanded:
                if e.lower() not in seen:
                    seen.add(e.lower())
                    unique.append((e, why))
        base = [self.base_ids(n) for n, _ in unique]
        claimed = {i for ids in base for i in ids}
        out = []
        for (name, _), ids in zip(unique, base):
            versions = set(self.versions(name)) if "#" in name and not name.startswith("re:") else None
            extra = []
            for i in ids:
                for v in self.var_group.get(i, ()):
                    if v not in claimed and v not in ids and v not in extra and self._same_family(i, v)                             and (versions is None or self.wiki.id_version.get(v) in versions | {None}):
                        extra.append(v)
            out.append(sorted(ids, key=self._version_order) + extra)
        return list(zip(out, [why for _, why in unique]))

    UNCHARGED = re.compile(r"(?i)\((?:uncharged|empty|inactive|broken|u|0)\)$|\bbroken\b| 0$")
    NUMBER = re.compile(r"\((?:t)?(\d+)\)$|\(i(\d+)\)$| (\d+)$|\((\d+) dose\)$")

    def _version_order(self, item_id):
        """Highest first within one item: potions (4)..(1), jewellery (8)..(1),
        Barrows 100..0, numbered versions before the bare name, uncharged last."""
        name = self.wiki.id_name.get(item_id, "")
        if self.UNCHARGED.search(name):
            return (2, 0)
        m = self.NUMBER.search(name)
        if m:
            return (0, -int(next(g for g in m.groups() if g)))
        return (1, 0)

    def _same_family(self, a, b):
        """Only borrow a variation id when it isn't a separately listed wiki item
        with a different page (e.g. salve amulet (e) vs (ei)): unknown ids and ids
        on the same page are fine."""
        pb = self.wiki.id_page.get(b)
        return pb is None or pb == self.wiki.id_page.get(a)

    def flat(self, names):
        return sorted({i for ids in self.entries([n[0] if isinstance(n, tuple) else n for n in names]) for i in ids})


def build_pick(res, pick):
    out = {"key": pick["key"], "mode": pick["mode"]}
    for k in ("slot", "profile", "style", "launcher", "fallback", "complete", "fill", "unless", "take", "groups", "repeat",
              "one", "slots"):
        if pick.get(k):
            out[k] = pick[k]
    if pick.get("why"):
        out["label"] = pick["why"]
    if pick.get("entries"):
        if pick.get("repeat"):
            # the same item may stand in several columns, so resolve each entry on its own
            resolved = [res.entries_with_reasons([n])[0] for n in pick["entries"]]
        else:
            resolved = res.entries_with_reasons(pick["entries"], split_regex=pick["mode"] == "all")
        out["entries"] = [ids for ids, _ in resolved]
        reasons = [why or "" for _, why in resolved]
        while reasons and not reasons[-1]:
            reasons.pop()
        if reasons:
            out["reasons"] = reasons
    if pick.get("requires"):
        out["requires"] = res.flat(pick["requires"])
        if pick.get("placed"):
            out["requiresPlaced"] = True
    if pick.get("pieces"):
        out["pieces"] = []
        for p in pick["pieces"]:
            resolved = res.entries_with_reasons(p["entries"])
            piece = {"slot": p["slot"], "entries": [ids for ids, _ in resolved]}
            if any(why for _, why in resolved):
                piece["reasons"] = [why or "" for _, why in resolved]
            out["pieces"].append(piece)
    if pick.get("variants"):
        out["variants"] = [[{"slot": p["slot"], "entries": res.entries(p["entries"])} for p in v]
                           for v in pick["variants"]]
    return out


def curated_ids(tab):
    """Ids a tab's curated picks already show, so supply sections skip them. A pick
    that shows one version per entry ("one") leaves the other versions to supplies."""
    ids = set()

    def walk(o):
        if isinstance(o, dict) and o.get("one"):
            return
        if isinstance(o, dict):
            for v in o.values():
                walk(v)
        elif isinstance(o, list):
            for v in o:
                if isinstance(v, int):
                    ids.add(v)
                else:
                    walk(v)
    walk(tab["sections"])
    return ids


def material_uses(wiki, recipes):
    """page -> {skill: number of recipes using it as an ingredient}"""
    uses = defaultdict(lambda: defaultdict(int))
    for r in recipes:
        skill = (r.get("skill") or "").lower()
        if not skill:
            continue
        for ing in r.get("ingredients", []):
            page = wiki.id_page.get(ing["itemId"])
            if page:
                uses[page][skill] += 1
    return uses


def supply_sections(wiki, recipes, uses, skill, rules, taken):
    """Group the recipe materials of a skill into titled sections. A rule is
    (title, regex[, order]); regexes match the in-game name. A ".*" rule is a
    catch-all that only takes items this skill uses most. Items are ordered by
    the lowest level that uses them, or by position in the rule's order list."""
    level = {}
    tools = set()
    for r in recipes:
        if (r.get("skill") or "").lower() != skill.lower():
            continue
        for t in r.get("tools", []):
            tools.add(t)
        for ing in r.get("ingredients", []):
            page = wiki.id_page.get(ing["itemId"])
            if page:
                level[page] = min(level.get(page, 999), r.get("level") or 1)
    exclude = re.compile(curated.EXCLUDE_SUPPLIES)
    tool_pages = {wiki.id_page.get(t) for t in tools}
    groups = defaultdict(list)
    for page, lvl in level.items():
        ids = wiki.page_ids[page]
        name = wiki.id_name.get(ids[0], wiki.page_name[page])
        if (exclude.search(name) or exclude.search(wiki.page_name[page]) or page in tool_pages
                or page in wiki.quest_only or any(i in taken for i in ids)):
            continue
        counts = uses[page]
        primary = counts.get(skill.lower(), 0) >= max(counts.values())
        for i, rule in enumerate(rules):
            title, rx = rule[0], rule[1]
            if not re.search(rx, name):
                continue
            if rx == r".*" and not primary:
                break
            if len(rule) > 2:
                base = re.sub(r"^(?:Grimy|Uncut) ", "", name)
                order = [o.lower() for o in rule[2]]
                rank = order.index(base.lower()) if base.lower() in order else len(order)
                key = (rank, 0 if name.startswith("Grimy ") else 1, name.lower())
            else:
                key = (lvl, name.lower())
            groups[title].append((key, ids))
            break
    sections = []
    for rule in rules:
        title = rule[0]
        items = sorted(groups.get(title, []))
        if items:
            key = "supplies_" + re.sub(r"\W+", "_", title.lower()).strip("_")
            sections.append({"key": key, "title": title, "layout": "rows", "supplies": True,
                             "picks": [{"key": key, "mode": "all", "entries": [ids for _, ids in items]}]})
    return sections


STAT_FIELDS = ["stab_attack_bonus", "slash_attack_bonus", "crush_attack_bonus", "range_attack_bonus",
               "magic_attack_bonus", "strength_bonus", "ranged_strength_bonus", "magic_damage_bonus", "prayer_bonus",
               "stab_defence_bonus", "slash_defence_bonus", "crush_defence_bonus", "range_defence_bonus",
               "magic_defence_bonus"]


def fetch_bonuses():
    return wiki_recipes.fetch_all("infobox_bonuses", ["page_name", "page_name_sub", "equipment_slot",
                                                      "weapon_attack_speed"] + STAT_FIELDS)


def fetch_categories():
    out = {}
    for cat in curated.COSMETIC_CATEGORIES:
        titles, cont = [], {}
        while True:
            q = {"action": "query", "list": "categorymembers", "cmtitle": "Category:" + cat, "cmlimit": "500",
                 "cmtype": "page"}
            q.update(cont)
            d = wiki_recipes.api(q)
            titles += [m["title"] for m in d["query"]["categorymembers"]]
            if "continue" not in d:
                break
            cont = d["continue"]
        out[cat] = titles
    return out


def set_stem(name):
    """'Rune platebody (g)' -> 'Rune (g)', 'Antisanta mask' -> 'Antisanta': items of one
    outfit share a stem so the layout keeps them on one row."""
    for rx, family in curated.COSMETIC_FAMILIES:
        if re.search(rx, name):
            return family
    m = re.match(r"^(.*?)(\s*\([^()]*\))?$", name)
    base, qualifier = m.group(1), (m.group(2) or "").strip()
    words = base.split()
    for n in (3, 2, 1):
        if len(words) > n and " ".join(words[-n:]).lower() in curated.COSMETIC_SLOT_WORDS:
            words = words[:-n]
            break
    return (" ".join(words) + (" " + qualifier if qualifier else "")).strip()


def cosmetics_tab(wiki, res, elsewhere):
    """Purely visual items: holiday and event rewards, cosmetic clue rewards
    (trimmed, gilded, heraldic, 3rd age...), random event outfits, graceful
    recolours, other zero-stat clothing, and ornament kits. The plugin also
    drops anything another tab already shows."""
    bonuses = defaultdict(list)
    for b in cached("bonuses.json", fetch_bonuses):
        bonuses[b["page_name"].lower()].append(b)
    cats = cached("categories.json", fetch_categories)

    def zero_stats(page):
        rows = bonuses.get(page.lower())
        return bool(rows) and all(not float(r.get(f) or 0) for r in rows for f in STAT_FIELDS)

    def slot(page):
        rows = bonuses.get(page.lower())
        return rows[0].get("equipment_slot") if rows else None

    skip = re.compile(curated.COSMETIC_EXCLUDE)
    variant = re.compile(curated.COSMETIC_VARIANT)
    taken = set()

    def usable(page):
        key = page.lower()
        if key not in wiki.page_ids or key in taken or skip.search(page) or key in wiki.quest_only:
            return False
        return not any(i in elsewhere for i in wiki.page_ids[key])

    def take(pages):
        out = []
        for p in sorted(set(pages), key=lambda p: (set_stem(p).lower(), p.lower())):
            if usable(p):
                taken.add(p.lower())
                out.append(wiki.page_name[p.lower()])
        return out

    equipable = lambda p: p.lower() in bonuses
    groups = [
        ("events", "Holiday & events",
         [p for c in ("Event rewards", "Cosmetics") for p in cats.get(c, []) if equipable(p)]),
        ("clues", "Treasure Trails",
         [p for p in cats.get("Treasure Trails rewards", []) if equipable(p) and (zero_stats(p) or variant.search(p))]),
        ("random", "Random events", [p for p in cats.get("Random event rewards", []) if equipable(p)]),
        ("graceful", "Graceful recolours", [p for p in wiki.pages if p.startswith("Graceful ") and equipable(p)]),
        ("other", "Other cosmetics",
         [p for p in wiki.pages if equipable(p) and zero_stats(p) and slot(p) in curated.COSMETIC_ZERO_STAT_SLOTS]),
        ("kits", "Ornament kits", [p for p in cats.get("Ornament kits", []) if not equipable(p)]),
    ]
    sections = []
    for key, title, pages in groups:
        if key == "graceful":
            # the movement tab shows one colour; the rest are cosmetic here
            pages_ok = sorted(p for p in pages if p.lower() in wiki.page_ids and not skip.search(p))
            for p in pages_ok:
                taken.add(p.lower())
            names = pages_ok
        else:
            names = take(pages)
        if not names:
            continue
        resolved = res.entries(names)
        sections.append({"key": key, "title": title, "layout": "sets", "picks": [{
            "key": "cosmetic_" + key, "mode": "all", "entries": resolved, "label": title,
            "groups": [set_stem(n) for n in names]}]})
    icon = res.base_ids(curated.COSMETIC_ICON)
    print("  cosmetics: %s" % ", ".join("%s %d" % (s["key"], len(s["picks"][0]["entries"])) for s in sections))
    return {"key": "cosmetics", "name": "Cosmetics", "kind": "cosmetic", "icon": icon[0] if icon else 0,
            "sections": sections}


GEAR_SECTIONS = {"main", "situational", "weapons", "specials", "gear", "outfit", "run", "jewellery", "diary", "items",
                 "protection", "cannon", "tools"}


def combat_gear_ids(tabs):
    """Gear and tools any tab curates (crystal armour, Bow of Faerdhinen, crystal
    axe...): made by skilling recipes but not a skill's finished goods. Ammo,
    potions and runes still count as goods (arrows, potions, runes)."""
    ids = set()
    for t in tabs:
        for s in t["sections"]:
            if s["key"] in GEAR_SECTIONS or s.get("layout") in ("equipment", "alts"):
                ids |= curated_ids({"sections": [s]})
    return ids


def add_goods(wiki, recipes, res, tab, gear):
    """Append the tab's finished goods: recipe products of its skill grouped by the
    supply section their main ingredient comes from (or the curated GOODS_LISTS for
    gathering skills), after any supply sections that are really goods."""
    key = tab["key"]
    taken = curated_ids(tab)
    goods = []
    moved = curated.GOODS_FROM_SUPPLIES.get(key, [])
    for s in [s for s in tab["sections"] if s.get("supplies") and s["layout"] == "rows" and s["title"] in moved]:
        tab["sections"].remove(s)
        goods.append(dict(s, layout="goods", title="Finished goods: " + s["title"].lower()))
    for title, names in curated.GOODS_LISTS.get(key, []):
        entries = [ids for ids in res.entries(names, split_regex=True) if not any(i in taken for i in ids)]
        if entries:
            goods.append(goods_section(key, title, entries))

    section_of = {}
    for s in tab["sections"]:
        for p in s["picks"]:
            for ids in p.get("entries", []):
                for i in ids:
                    section_of.setdefault(wiki.id_page.get(i), s["title"])
    skill = key.lower()
    products = {}
    junk = (re.compile(curated.EXCLUDE_SUPPLIES), re.compile(curated.EXCLUDE_GOODS))
    for r in recipes:
        if (r.get("skill") or "").lower() != skill:
            continue
        page = wiki.id_page.get(r.get("productId") or 0)
        names = (wiki.page_name.get(page, ""), wiki.id_name.get(r.get("productId"), ""))
        if not page or page in wiki.quest_only or any(j.search(n) for j in junk for n in names):
            continue
        ids = wiki.page_ids[page]
        if any(i in taken or i in gear for i in ids):
            continue
        group = "Other"
        for ing in r.get("ingredients", []):
            title = section_of.get(wiki.id_page.get(ing["itemId"]))
            if title and not title.startswith("Finished goods"):
                group = title
                break
        level = r.get("level") or 1
        if page not in products or level < products[page][0]:
            products[page] = (level, group)
    shown = {i for g in goods for p in g["picks"] for ids in p["entries"] for i in ids}
    order = [s["title"] for s in tab["sections"]] + ["Other"]
    by_group = defaultdict(list)
    for page, (level, group) in products.items():
        ids = res.entries([wiki.page_name[page]])[0]
        if not any(i in shown for i in ids):
            by_group[group].append((level, wiki.page_name[page].lower(), ids))
    for group in sorted(by_group, key=lambda g: order.index(g) if g in order else len(order)):
        entries = [ids for _, _, ids in sorted(by_group[group])]
        same = next((g for g in goods if g["title"] == "Finished goods: " + group.lower()), None)
        if same:
            # e.g. potions made from potions join the moved Potions section
            same["picks"][0]["entries"] += entries
        else:
            goods.append(goods_section(key, group, entries))
    tab["sections"] += goods


def goods_section(tab_key, title, entries):
    slug = re.sub(r"\W+", "_", title.lower()).strip("_")
    return {"key": "goods_" + slug, "title": "Finished goods: " + title.lower(), "layout": "goods",
            "picks": [{"key": "goods_" + slug, "mode": "all", "entries": entries}]}


def main():
    rows = cached("items.json", fetch_items)
    variations = cached("item_variations.json", fetch_variations)
    wiki = Wiki(rows)
    res = Resolver(wiki, variations)
    # exclusions must also catch PvP/Deadman/BH variants the main index skips
    every = Resolver(Wiki(rows, skip=False), variations)
    recipes = fetch_recipes()["recipes"]
    print("wiki: %d pages, %d ids; recipes: %d" % (len(wiki.pages), len(wiki.id_name), len(recipes)))

    tabs = []
    for t in curated.TABS:
        icon = res.base_ids(curated.DEFAULT_ICONS.get(t["key"], t["icon"]))
        tab = {"key": t["key"], "name": t["name"], "kind": t["kind"], "icon": icon[0] if icon else 0,
               "sections": [dict({"key": s["key"], "title": s["title"], "layout": s["layout"],
                                  "picks": [build_pick(res, p) for p in s["picks"]]},
                                 **({"supplies": True} if s.get("supplies") else {}))
                            for s in t["sections"]]}
        tabs.append(tab)

    by_key = {t["key"]: t for t in tabs}
    uses = material_uses(wiki, recipes)
    for skill, key, rules in curated.SUPPLY_RULES:
        tab = by_key[key]
        tab["sections"] += supply_sections(wiki, recipes, uses, skill, rules, curated_ids(tab))
    gear = combat_gear_ids(tabs)
    for t in curated.TABS:
        if t["kind"] == "skill":
            add_goods(wiki, recipes, res, by_key[t["key"]], gear)

    elsewhere = set()
    for t in tabs:
        elsewhere |= curated_ids(t)
    tabs.append(cosmetics_tab(wiki, res, elsewhere))
    for t in tabs:
        if t["key"] in curated.TAB_ICONS:
            t["iconEntries"] = res.entries(curated.TAB_ICONS[t["key"]])
            # owning none of them falls back to the default icon, not to whatever comes first
            t["iconStrict"] = t["key"] not in curated.ICON_FALLBACK_TO_FIRST_ITEM

    launcher_ammo = {}
    for name, cls in curated.LAUNCHER_AMMO.items():
        for i in res.flat([name]):
            launcher_ammo[str(i)] = cls
    payload = {
        "version": 1,
        "generated": date.today().isoformat(),
        "source": "OSRS Wiki (oldschool.runescape.wiki), CC BY-NC-SA 3.0",
        "excludeAuto": every.flat(curated.EXCLUDE_AUTO),
        "situationalOnly": every.flat(curated.SITUATIONAL_ONLY),
        "excludeMeleeWeapon": every.flat(curated.EXCLUDE_MELEE_WEAPON),
        "meleeMultiplier": {str(i): m for name, m in curated.MELEE_WEAPON_MULTIPLIER.items() for i in res.flat([name])},
        "doubleAccuracy": res.flat(curated.MELEE_DOUBLE_ACCURACY),
        "launcherAmmo": launcher_ammo,
        "ammoClasses": {cls: res.entries(names) for cls, names in curated.AMMO_CLASSES.items()},
        "tabs": tabs,
    }
    if res.errors:
        print("\n".join(sorted(set(res.errors))))
        sys.exit("%d unresolved names - fix curated.py" % len(set(res.errors)))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, separators=(",", ":")), encoding="utf-8")
    print("wrote %s (%d KB)" % (OUT, OUT.stat().st_size // 1024))
    for t in tabs:
        n = sum(len(p.get("entries", [])) + len(p.get("pieces", [])) for s in t["sections"] for p in s["picks"])
        print("  %-13s %2d sections, %4d entries" % (t["key"], len(t["sections"]), n))


if __name__ == "__main__":
    main()
