# Ultimate Bank Tabs

Ultimate Bank Tabs builds bank tag tabs from what you own. Each combat style gets a tab with your best-in-slot gear. There is also a tab for getting around and one for every skill. Each tab is laid out for you and updates as your bank changes.

## Tabs

- **Melee, Ranged, Magic.** Your best item for every slot sits in the shape of the equipment screen. Situational gear goes beside it, lined up with the slot it replaces. For melee that means a slayer helmet, a salve amulet, Inquisitor's, Void, and so on. Below that are your best weapon for each job:
  - Melee: stab, slash and crush, plus dragonbane, demonbane, kalphite and other slayer weapons.
  - Ranged: dragonbane, demonbane, blowpipe and multi-target weapons.
  - Magic: an ancient spellbook staff and a standard spellbook staff.

  Then come your special attack weapons, the right ammunition for the launchers in the tab, and your best potions and food.
- **Movement.** Your most complete graceful set, stamina potions, teleport jewellery, diary rewards and other teleport items. It also has every teleport tablet and scroll, and the runes for teleport spells.
- **Skills.** Prayer, Slayer, Agility, Thieving, Herblore, Crafting, Fletching, Hunter, Mining, Smithing, Fishing, Cooking, Firemaking, Woodcutting, Farming, Runecraft, Construction and Sailing. Each tab shows your best tools and skilling outfit, then the supplies you own for that skill, grouped and in level order. For example, Herblore shows each herb grimy then clean, then unfinished potions, potions and secondaries. At the bottom, after a blank row, each skill tab shows its **finished goods**: what the skill makes, grouped by what it is made from (potions, jewellery, d'hide, bows and arrows, smithed items, cooked food, runes and so on), plus logs, ores, gems, raw fish and hunter catches for the gathering skills.

Only items you own appear. That means your bank, including placeholders, potion storage, and (optionally) what you are wearing and carrying. An item can appear in several tabs. Tabs with nothing in them are not created.

## How "best" is decided

- **Armour, jewellery, capes and blessings** are ranked by their equipment bonuses for the tab's style. Strength bonus counts most, then accuracy, then prayer, with defence as a tie-breaker. New items rank themselves.
- **Melee weapons** are ranked by a small damage model: attack speed, strength and accuracy against a sturdy target. It credits passives such as the scythe's extra hits and the fang's double accuracy roll. The stab, slash and crush picks only consider weapons built for that style.
- **Ranged and magic weapons, ammunition, situational gear, special attacks, teleports and skilling tools** come from hand-ranked lists, best first. Your highest-ranked owned item wins.
- Player-versus-player gear and Wilderness-only weapons never take a main slot. Wilderness weapons get their own situational spot.

## Settings

- Turn each tab on or off.
- **Backups per slot**: also show the next best one to three items for each pick.
- **Count worn & carried items** (on by default): gear you are wearing without a bank placeholder still gets its slot.
- Potions and food in combat tabs, supplies and finished goods in skill tabs, and a blank row between sections.
- **Tab icons**: skill icons (Attack for melee, Run for movement, the cosmetics tab keeps an item), or your best item for each tab.
- **Remove tabs when turned off**: the plugin's tabs disappear when you turn it off and come back when you turn it on.
- **Bring back replaced tabs**: puts back the tabs you had before the plugin replaced them.

## Notes

- The tabs are normal Bank Tags tabs (the core **Bank Tags** plugin must be on). Their contents are worked out from your bank each time, so the plugin never writes tags onto your items.
- **The plugin replaces your bank tabs.** The first time it starts it asks before removing the tabs you already have, and saying no turns it back off. Your items keep their tags, so `tag:` searches still work, and the removed tabs are backed up: *Bring back replaced tabs* in the settings puts them back.
- Plugins that fight over the same tabs (Auto Bank Sorter, Bank Assistant, Bank Sorter, Sorted Bank) get a warning in chat when you open the bank.
- The tab menu options work on the plugin's tabs too:
  - **Rename** keeps the tab working under its new name.
  - **Change icon** keeps your pick. Change the *Tab icons* setting to give icons back to the plugin.
  - **Disable layout** keeps the tab in plain bank order until you choose *Enable layout*.
  - **Delete** switches that tab off. Turn it back on in the settings.
- You can drag items around in a tab. Your arrangement stays until the tab's contents change.
- A brand-new tab shows up the next time you open the bank.

## Data

Item lists come from the [Old School RuneScape Wiki](https://oldschool.runescape.wiki) under [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/). This includes equipment pages, recipes for skill supplies, and item versions. `tools/generate_data.py` rebuilds `tabs.json` from the wiki, and the curated rankings live in `tools/curated.py`. The plugin makes no network requests.
