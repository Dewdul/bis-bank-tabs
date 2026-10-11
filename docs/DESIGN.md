# No Brain Bank Tabs: design

Started 2026-10-04. The request was an "ultimate bank plugin" that does three things. It makes tabs with best-in-slot gear for each combat style, for movement, and for every skill. It works entirely from what is in the bank, including placeholders. Its tabs can hold several weapons or armour sets when each is best for something. The layout of each tab is optimised automatically.

## Bank Tags integration

- **Membership is in memory.** Each tab registers a `BankTag` with `TagManager.registerTag`. Bank Tags combines registered tags with a tab of the same name. The plugin never writes `banktags.item_*` keys, so the player's own tags are untouched and the config does not churn on every bank change.
- **Persisted keys are the tab list, icon and layout.** The plugin writes `banktags.tagtabs` (appending new tabs and leaving the player's own order alone), `banktags.icon_<tag>`, and the layout through `LayoutManager.saveLayout`. These are the public APIs exposed to `@PluginDependency(BankTagsPlugin.class)` plugins. `TabManager` is not public, so the tab list is edited through config.
- **The tab list reloads on bank open.** Bank Tags reads `tagtabs` in `TabInterface.init()` on `BANKMAIN_INIT`. The plugin subscribes to that script at priority 1 so it runs first. When the bank container is already known at that point, new tabs appear in the same open. Otherwise a chat line says to reopen the bank.
- **Layouts are written only when the computed layout changes.** The last planned layout is kept per tab in `ultimatebanktabs.planned_<key>`, so a restart can tell the player's moves from the plan. When the plan changes, `LayoutMerger` decides what is saved:
  - **Untouched tab** (saved layout equals the last plan, ignoring trailing gaps): the new plan as it is.
  - **Rearranged tab:**
    - The player's layout is the base.
    - A charge, dose or degrade step is the same item and takes the old id's cell. Variants are matched with `ItemVariationMapping.map`, the mapping Bank Tags itself uses.
    - Items the plugin no longer shows become gaps. Their cell is remembered per tab (`home_<key>`), and an item that comes back goes there if the cell is free.
    - Items the player tagged in themselves stay.
    - A new best-in-slot item (a different variant group) takes its equipment-block cell back, and anything it pushes out is placed again.
    - Other new items go into their own section's rows. `LayoutPlanner.layOut` names the section of every planned row (each Herblore herb row as `herb:<herb>`), and the merge finds those rows wherever the player moved the section's items. It tries the item's planned column first, so herb columns stay aligned, then any free cell, then a new row under the section. A section the layout doesn't have yet gets rows of its own after the section before it in the plan, keeping a gap row if the plan has one.
    - Each copy in the layout stands for its item's nearest planned cell, so an item the plan shows twice (herb secondaries) gets its missing copy in the right place.
    - Returning items go home before any new item is placed.
    - A tab that gains its equipment block moves the player's arrangement down under it as a whole.
    - **Known limits:** layout settings (blank rows, backups) don't re-space a rearranged tab, so Disable then Enable layout resets it. Layouts are global in Bank Tags while ownership is per account, so an alt account rearranges the shared tabs around its own items; home cells bring the main's items back when you switch back.
  - **Reset:** Disable then Enable layout clears the last plan, which restarts the tab from the plan.
  - An open tab that changes is reopened with `BankTagsService.openBankTag`.
- **Tag names avoid collisions.** A name is skipped if it is in `tagtabs` but not owned by this plugin, or if any item carries it as a tag. The fallbacks are "x bis", "x (auto)" and "x (auto N)". The chosen name is stored in `ultimatebanktabs.tagName_<key>`.
- **Empty tabs are hidden, not deleted.** A tab that is on but has nothing for this account leaves `tagtabs` but keeps its name, layout and plan, so it comes back as it was.
- **Remove-tag is hidden on the plugin's tabs.** It can't change what they hold, and Bank Tags would move the item to the first empty cell for good.
- **Tag names compare standardized**, like Bank Tags does. A tab renamed with capitals still works with Change icon and Disable/Enable layout.
- **Deleting a tab turns it off.** If the player removes or renames a plugin tab, the `tagtabs` change arrives while the plugin is not writing. The tab's toggle is then set to off, so the plugin does not fight the player.
- **It replaces the player's tabs, with consent.** On the first start, if `tagtabs` holds tabs the plugin didn't make, a dialog lists them and asks. *Replace my tabs* removes them from `tagtabs` with their icons and layouts. Item tags stay, so `tag:` searches still work. The removed tabs are saved as JSON in `ultimatebanktabs.replacedTabs`, and their names are reserved so new plugin tabs can't take them. *Turn plugin off* (or closing the dialog) disables the plugin. Nothing is built until `replaceAccepted` is set. That flag is global, as Bank Tags config is shared by all accounts.
  - **Restoring is on demand, not on shutdown.** A Hub update stops and restarts the plugin, so restoring on shutdown would hand the tabs back and ask again after every update. Instead the self-resetting "Bring back replaced tabs" setting appends the saved tabs to `tagtabs`.
  - **The Hub install warning.** The plugin-hub file should also carry `warning=` text (see Hub submission below). RuneLite then shows it in a confirm dialog before installing.
- **Conflicting plugins.** Each bank open warns once per session in chat about enabled Auto Bank Sorter, Bank Assistant, Bank Sorter or Sorted Bank. Matching uses `ExternalPluginManager.getInternalName`, or the descriptor name as a fallback. The first-run dialog lists them too.
- **Skill icons.** Bank Tags draws tab icons as items only: the icon is the `+1` child after each background, from child 4 of `Bankmain.ITEMS_CONTAINER`. On `BeforeRender`, `SkillIcons` swaps our tabs' icons for a sprite: the `Staticons` skill icons, Attack for melee, and `OrbIcon.RUN` for movement. Each sprite is centred at its native size. This is reapplied every frame because `layoutTabs` resets Y on scroll and `rebuildTabs` recreates the widgets. The best item is still written to `icon_<tag>`, so tabs drawn without the plugin show an item. A Change icon pick wins over the sprite.
- **Turning the plugin off can remove its tabs.** "Remove tabs when turned off" (default on) takes the plugin's entries out of `tagtabs` and drops their icons on `shutDown`. The tag names, layouts and plans are kept: Bank Tags ignores the layout of an unlisted tag, so the tabs come back as the player arranged them.
- **Bank Tags writes back its own copy.** `TabManager` loads the tab list and icons when the bank opens, and every save (tab drag, new tab, Change icon) writes that in-memory copy back. Tab-list edits are therefore diffed against a snapshot taken at `BANKMAIN_INIT`, not against the config's old value, so a tab the plugin added since the bank opened isn't read as deleted. An icon write-back equal to one this plugin wrote earlier is stale, not a Change icon pick.
- **Remove-tag is stripped on the `Examine` (or potion storage `Withdraw-All-but-1`) entry's event.** Bank Tags inserts it with `createMenuEntry`, which posts no event of its own.

## Slayer task

- **Reading the task.** `SlayerTask.read` does what the core Slayer plugin does, so it works with that plugin off. It reads `VarPlayerID.SLAYER_COUNT` (no task at 0) and `SLAYER_TARGET`. Boss tasks (target 98) take the boss from `VarbitID.SLAYER_TARGET_BOSSID` through DB table 116. Names come from DB table 113 (`COL_NAME_UPPERCASE`), Konar's area from table 115, and Krystilia (master 7) means a Wilderness task. `VarbitChanged` on those ids re-reads the task on the next client-thread turn, and only a different task (not a kill) rebuilds.
- **Data.** `tools/slayer_tasks.py` is wiki-researched and adversarially verified. It holds 155 tasks plus aliases, keyed by the in-game name. Each has recommended styles, bane categories, slot gear (required or suggested), carried tools and potion families. The generator writes it to `tabs.json` as `slayerTasks`.
- **The block.**
  - **Style.** `TabBuilder.slayerTask` builds the combat tab of each recommended style and ranks them:
    - Best: armed with the task's own weapon, either its bane or the only kind that hurts the monster. Kurask and turoth tag a leaf-bladed weapon, broad ammo that fits the launcher, or a Magic Dart staff with the style each serves.
    - Next: armed. Below that: armed but unable to hurt the monster.
    - Ties go to the earlier style. A bane weapon arms a style even though banes stay out of the main weapon pick.
  - **Shields.** When the task needs a shield, two-handed weapons are left out, unless they are all the player has. A shield is needed when the task requires it, or when it is dragonfire protection and the player has no super antifire. With a two-handed weapon, a shield that isn't needed goes beside the block.
  - **Helmet and salve.** The slayer helmet goes in its slot, or keeps the cell when the best-in-slot pick already put it there. On undead, the salve goes on only if it beats the helmet, because their bonuses don't stack: the (ei), or the (e) on melee. The helmet then gives way to the best helmet for stats.
  - **Weapon and ammo.** The bane comes from the style tab's pick, so a stab, slash or crush row can't hide it. Its category order holds, and on Krystilia tasks every task counts as Wilderness. A launcher swapped in takes ammo that fits it.
  - **Prayer gear.** The style's prayer set is drawn as its own equipment screen beside the setup, the same as on the combat tabs.
  - **Task slot gear.** A needed piece takes the slot. A suggested one keeps a slot that already holds one of its choices (with the task's reason), or fills an empty slot when it isn't another style's gear. Otherwise it goes beside.
  - **Below the block:**
    - the task's tools, required first;
    - its potions, then that style's supplies, food and combat runes;
    - the general Slayer sections, without repeating anything the block shows.
  - **Layout.** A block that comes and goes with the task leaves the player's arrangement where it was (`LayoutMerger`).

## Food

- **Live heals.** The `food` pick ranks the player's foods by the HP one eat heals them now. That number comes from RuneLite's `ItemStatChangesService` (`@PluginDependency(ItemStatPlugin.class)`), which holds the data behind the Item Stats tooltips. It counts plain food (`FoodBase`) and food combos with no stat drain, so a jug of wine, brews and random foods are out. Anglerfish follow the player's Hitpoints level, and a food missing from `tabs.json` still counts.
- **Wiki fallback.** The wiki list (`curated.FOOD`) gives each food's page versions, its hover note, any heal that follows a few seconds later, and the HP per inventory slot used to break ties. Its own heal numbers rank the foods only when RuneLite has none (in tests, or a food RuneLite doesn't know).
- **How many.** Six by default (the "Food items" setting, 1-8). RuneLite saves defaults, so a stored 4 is moved to 6 once (`sixFoods`).

## Ownership

- **Sources.** Owned items come from the bank container (items and placeholders, canonicalised), plus potion storage. Potion storage is read the way core's `PotionStorage` does it, with one id per stored potion because the layout matches any dose to the stored potion. Worn and carried items are added when "Count worn & carried items" is on (default on).
- **Snapshot.** The bank and potion sets are saved per RS profile (`owned` = `bankcsv|potioncsv`). Tabs can then be rebuilt at login, before the bank opens.

## Picking items

`tabs.json` is generated by `tools/generate_data.py` from `tools/curated.py` and wiki data. Names are wiki page names, so one name covers every version of an item: charges, doses, charged and uncharged. RuneLite's `item_variations.json` adds ornament and variant ids. A variant is never borrowed if it belongs to another listed entry; for example, salve (e) and salve (ei) stay apart. An entry can also name some versions of a page: `"Mining helmet#Lit"` is one wiki version, and `"Abyssal lantern#re:^(?!Unlit)"` is every version whose name matches the regex. Light sources and lanterns use this, so an unlit helmet or an empty lantern is never shown as a light source.

Pick modes (`TabBuilder`):

- **first**: the highest-ranked owned entry, with all of its owned ids.
- **all**: every owned entry, in order.
- **auto**: the best owned item for an equipment slot by stat profile (`Scoring.armour`):
  - melee: `3*str + avg(stab, slash, crush) + 0.3*prayer`
  - ranged: `3*rstr + ranged + 0.3*prayer`
  - magic: `4*mdmg% + magic + 0.3*prayer`
  - Every profile adds `0.02*defence`.
  - The weights come from damage elasticity: max hit scales linearly with strength, while hit chance saturates. Defence lets low-level accounts get rune armour.
- **weapon**: the best melee weapon by a damage model. The model assumes 99s with piety and super combat, other gear worth +100 strength and +120 attack, and a target with a defence roll of 159×164.
  - The main pick credits curated multipliers (scythe ×1.5, soulreaper ×1.2) and double accuracy (fang).
  - The stab, slash and crush picks use plain damage, and only consider weapons whose best bonus is that style. The fang's defensive slash never counts as a slash weapon.
  - A role whose best weapon is already in the tab adds nothing.
- **ammo**: the best owned ammunition for the class of the launcher the weapon pick chose. Bowfa and blowpipes take a prayer blessing instead. Ammo picks resolve after the other picks in their section.
- **set**: a group of slot pieces, shown when any piece is owned, or only when all are (Void). `requires` gates it on owning a related item; `requiresPlaced` gates it on that item being in the tab (ammo).
- **outfit**: graceful. The colour with the most owned pieces wins, and any gap falls back to the lightest weight-reducing item for that slot.
- **unless**: skip a pick when another pick resolved. For example, there is no separate super strength potion when there is a super combat potion, and no earmuffs when there is a slayer helmet.

Excluded from auto and weapon picks: ancient warrior equipment (PvP) and the Wilderness champion amulet. Wilderness, dragonbane, demonbane, kalphite, vampyre and leaf-bladed weapons are also kept out of the main and role weapon picks, but each has its own situational pick.

## Skill supplies

Each skill's supplies are generated from the wiki recipe bucket, using the same fetch code as Material Checklist:

- Ingredient pages are grouped by named regex rules, for example Herbs, Unfinished potions, Logs and Ores.
- Items are ordered by the lowest level that uses them. Herbs follow a fixed order, with grimy before clean.
- A `.*` catch-all rule (Herblore secondaries only) takes an item only if Herblore uses it most.
- Untradeable quest items and generic junk (coins, buckets, tools) are dropped.

Catch-alls were removed for the other skills because they pulled in costume-room armour, godsword shards and similar items.

## Layout (`LayoutPlanner`)

- **Equipment block (rows 0–4).**
  - Columns 0–2 hold the best-in-slot set in the shape of the equipment screen.
  - Column 3 is a gutter.
  - Columns 4–7 hold situational items in the row of the slot they replace. A set (Inquisitor's, Void...) takes one column so its pieces line up vertically.
  - Overflow, and extra owned variants of a main pick, go to an "other gear" row.
- **Row sections** follow from row 5 (or row 0 for tabs without gear). Each section starts a fresh row, eight items to a row, with an optional blank row between sections.

## Known gaps

- Ranged and magic weapon rankings are hand-ordered. Weapons released after the data was made fall back to a stat score only for accounts with no curated weapon. Regenerate `tabs.json` after big releases.
- There are no tank or defensive sets yet.
- The Sailing tab is thin. Wiki data for Sailing gear is still sparse, so it mostly holds supplies from recipes.

## Hub overlap

The closest existing hub plugin is **Auto Bank Sorter** (`bank-skill-sorting`, about 8k installs). It makes 22 skill tabs with sectioned layouts, a melee/range/mage tab of all gear by slot, and a task-aware slayer tab.

This plugin differs in several ways:

- It shows best-in-slot items only, not all gear.
- It uses an equipment-screen block with aligned alternatives.
- Melee picks come from a damage model, with role, situational and special attack picks.
- Ammunition follows the launchers in the tab.
- Graceful picks the most complete colour.
- It keeps no item tags in config.

Other related hub plugins are Bank Assistant, BiS Gear, GearForge, Wiki Gear Setups and Bank Tag Generation.

## Hub submission

The plugin-hub file (`plugins/no-brain-bank-tabs`) should include an install warning, for example:

```
warning=No Brain Bank Tabs replaces your bank tag tabs with its own. It asks before removing your current tabs, and keeps a backup you can bring back from its settings.
```
