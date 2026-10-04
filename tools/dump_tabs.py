#!/usr/bin/env python3
"""Print tabs.json with item names, for reviewing generated data.

Usage: python tools/dump_tabs.py [tab-key ...]
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
data = json.loads((ROOT / "src/main/resources/com/ultimatebanktabs/tabs.json").read_text(encoding="utf-8"))
names = {}
for r in json.loads((ROOT / "tools/cache/items.json").read_text(encoding="utf-8")):
    for i in r.get("item_id") or []:
        try:
            names.setdefault(int(i), r.get("item_name") or r["page_name"])
        except ValueError:
            pass


def label(ids):
    return names.get(ids[0], str(ids[0])) + ("" if len(ids) == 1 else " +%d" % (len(ids) - 1))


only = set(sys.argv[1:])
for tab in data["tabs"]:
    if only and tab["key"] not in only:
        continue
    print("=" * 20, tab["name"], "(icon %s)" % names.get(tab["icon"]))
    for s in tab["sections"]:
        print("--", s["title"], "[%s]" % s["layout"])
        for p in s["picks"]:
            head = "  %s %s%s" % (p["mode"], p["key"], (" " + p["slot"]) if p.get("slot") else "")
            if p.get("entries"):
                print(head + ": " + ", ".join(label(e) for e in p["entries"]))
            elif p.get("pieces"):
                print(head + ": " + " | ".join(pc["slot"] + "=" + ", ".join(label(e) for e in pc["entries"])
                                               for pc in p["pieces"]))
            else:
                print(head)
