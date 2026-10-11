package com.ultimatebanktabs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import lombok.Value;
import net.runelite.api.EquipmentInventorySlot;

/**
 * Turns a tab definition plus the set of items the player owns into the items
 * the tab should show, section by section. Pure logic: stats come from a
 * lookup function so tests can supply their own.
 */
class TabBuilder
{
	static class Options
	{
		/** extra next-best items per "first"/"auto"/"weapon" pick */
		int backups;
		boolean skillSupplies = true;
		boolean skillGoods = true;
		boolean combatSupplies = true;
		boolean blightedSupplies = true;
		/** how many foods the combat tabs show */
		int foodCount = 6;
		/** items other tabs already show; the cosmetics tab leaves them out */
		Set<Integer> shownElsewhere = Collections.emptySet();
		/** the player's current slayer task: the Slayer tab leads with its setup */
		SlayerTask slayerTask;
	}

	@Value
	static class Placement
	{
		int itemId;
		/** equipment slot name, or null */
		String slot;
		/** the pick (or set) this item came from; alt layout keeps a group together */
		String group;
		/** why the item is in the tab, shown when hovering it in the bank */
		String reason;
		/** which version of its entry this is (0 = the fullest dose of a potion), or -1 */
		int variant;

		Placement(int itemId, String slot, String group, String reason, int variant)
		{
			this.itemId = itemId;
			this.slot = slot;
			this.group = group;
			this.reason = reason;
			this.variant = variant;
		}

		Placement(int itemId, String slot, String group, String reason)
		{
			this(itemId, slot, group, reason, -1);
		}
	}

	@Value
	static class Section
	{
		String key;
		String title;
		String layout;
		List<Placement> items;
		/** "columns" layout: the column of every possible entry, in order (e.g. herbs guam..torstol) */
		List<String> columns;

		Section(String key, String title, String layout, List<Placement> items, List<String> columns)
		{
			this.key = key;
			this.title = title;
			this.layout = layout;
			this.items = items;
			this.columns = columns;
		}

		Section(String key, String title, String layout, List<Placement> items)
		{
			this(key, title, layout, items, Collections.emptyList());
		}
	}

	@Value
	static class Built
	{
		TabData.Tab def;
		List<Section> sections;
		/** best item to use as the tab icon, or -1 */
		int hero;

		/** item id -> why it is in the tab (an item shown twice lists both reasons) */
		Map<Integer, String> reasons()
		{
			Map<Integer, String> reasons = new HashMap<>();
			for (Section s : sections)
			{
				for (Placement p : s.items)
				{
					reasons.merge(p.itemId, p.reason == null ? "" : p.reason,
						(a, b) -> a.isEmpty() || a.contains(b) ? (a.isEmpty() ? b : a) : a + "; " + b);
				}
			}
			return reasons;
		}

		Set<Integer> itemIds()
		{
			Set<Integer> ids = new LinkedHashSet<>();
			for (Section s : sections)
			{
				for (Placement p : s.items)
				{
					ids.add(p.itemId);
				}
			}
			return ids;
		}

		boolean isEmpty()
		{
			return sections.stream().allMatch(s -> s.items.isEmpty());
		}
	}

	private final TabData data;
	private final IntFunction<EquipStats> stats;
	private final Scoring scoring;
	private final Set<Integer> excludeAuto;
	private final Set<Integer> situationalOnly;
	private final Set<Integer> excludeMeleeWeapon;
	private final Map<Integer, String> launcherAmmo = new HashMap<>();
	/** HP one eat of an item heals the player now (0 for what isn't food), or -1 without live numbers */
	private final IntUnaryOperator heals;
	private final Set<Integer> notFood;
	/** ingredients (onions, cheese, potatoes) heal 1-2: not food to bring */
	private static final int MIN_FOOD_HEAL = 3;

	TabBuilder(TabData data, IntFunction<EquipStats> stats)
	{
		this(data, stats, id -> -1);
	}

	TabBuilder(TabData data, IntFunction<EquipStats> stats, IntUnaryOperator heals)
	{
		this.data = data;
		this.stats = stats;
		this.heals = heals;
		this.notFood = new HashSet<>(data.notFood);
		this.excludeAuto = new HashSet<>(data.excludeAuto);
		this.situationalOnly = new HashSet<>(data.situationalOnly);
		this.excludeMeleeWeapon = new HashSet<>(data.excludeMeleeWeapon);
		Map<Integer, Double> multipliers = new HashMap<>();
		data.meleeMultiplier.forEach((id, m) -> multipliers.put(Integer.parseInt(id), m));
		this.scoring = new Scoring(multipliers, new HashSet<>(data.doubleAccuracy));
		data.launcherAmmo.forEach((id, cls) -> launcherAmmo.put(Integer.parseInt(id), cls));
	}

	Built build(TabData.Tab tab, Set<Integer> owned, Options options)
	{
		if (tab.isCosmetic() && !options.shownElsewhere.isEmpty())
		{
			owned = new HashSet<>(owned);
			owned.removeAll(options.shownElsewhere);
		}
		Context ctx = new Context(owned, options);
		List<Section> sections = new ArrayList<>();
		int hero = -1;
		TabData.SlayerTaskData task = "slayer".equals(tab.key) && options.slayerTask != null
			? data.slayerTasks.get(options.slayerTask.getName()) : null;
		if (task != null)
		{
			sections.addAll(slayerTask(task, ctx, options));
		}
		// a skill tab's graceful filler waits until the alternatives are known (they fill empty slots first)
		TabData.Pick filler = null;
		Section fillerSection = null;
		for (TabData.Section def : tab.sections)
		{
			if (def.supplies && !options.skillSupplies
				|| "goods".equals(def.layout) && !options.skillGoods
				|| tab.isGear() && def.key.startsWith("supplies") && !options.combatSupplies
				|| tab.isGear() && def.key.startsWith("blighted") && !(options.combatSupplies && options.blightedSupplies))
			{
				continue;
			}
			List<Placement> items = new ArrayList<>();
			// the weapon goes first (armour follows its attack style) and ammo last (it follows the launcher)
			List<TabData.Pick> picks = new ArrayList<>(def.picks);
			picks.sort(Comparator.comparing(p -> "weapon".equals(p.mode) ? 0 : "ammo".equals(p.mode) ? 2 : 1));
			ctx.sectionTitle = def.title;
			ctx.sectionSlots.clear();
			boolean holdsFiller = false;
			for (TabData.Pick pick : picks)
			{
				if (pick.fill)
				{
					filler = pick;
					holdsFiller = true;
					continue;
				}
				List<Placement> got = resolve(pick, ctx);
				if (hero == -1 && "main".equals(def.key) && "WEAPON".equals(pick.slot) && !got.isEmpty())
				{
					hero = got.get(0).itemId;
				}
				items.addAll(got);
			}
			List<String> columns = def.picks.stream().filter(p -> !p.groups.isEmpty()).findFirst()
				.map(p -> p.groups).orElse(Collections.emptyList());
			Section section = new Section(def.key, def.title, def.layout, items, columns);
			sections.add(section);
			if (holdsFiller)
			{
				fillerSection = section;
			}
		}
		if (filler != null && fillerSection != null)
		{
			fill(filler, fillerSection, sections, ctx);
		}
		if (!tab.iconEntries.isEmpty())
		{
			List<Integer> iconic = firstOwned(tab.iconEntries, ctx, 1);
			hero = !iconic.isEmpty() ? iconic.get(0) : tab.iconStrict ? -1 : hero;
		}
		else if (hero == -1)
		{
			hero = sections.stream().flatMap(s -> s.items.stream()).map(Placement::getItemId).findFirst().orElse(-1);
		}
		if (hero == -1 && !tab.iconStrict && !tab.iconEntries.isEmpty())
		{
			hero = sections.stream().flatMap(s -> s.items.stream()).map(Placement::getItemId).findFirst().orElse(-1);
		}
		return new Built(tab, sections, hero);
	}

	/** bane picks of the combat tabs, by style and task category */
	private static final Map<String, List<String>> BANES = new HashMap<>();

	static
	{
		BANES.put("melee:dragon", Collections.singletonList("dragonbane"));
		BANES.put("melee:demon", Collections.singletonList("demonbane"));
		BANES.put("melee:kalphite", Collections.singletonList("kalphite"));
		BANES.put("melee:vampyre", Arrays.asList("vampyrebane", "vampyre"));
		BANES.put("melee:golem", Collections.singletonList("golembane"));
		BANES.put("melee:leafy", Collections.singletonList("leafbladed"));
		BANES.put("melee:wilderness", Collections.singletonList("wilderness"));
		BANES.put("ranged:dragon", Collections.singletonList("dragonbane_ranged"));
		BANES.put("ranged:demon", Collections.singletonList("demonbane_ranged"));
		BANES.put("ranged:vampyre", Collections.singletonList("vampyre_ranged"));
		BANES.put("ranged:wilderness", Collections.singletonList("wilderness_ranged"));
		BANES.put("magic:dragon", Collections.singletonList("dragonbane_magic"));
		BANES.put("magic:demon", Collections.singletonList("demonbane_magic"));
		BANES.put("magic:wilderness", Collections.singletonList("wilderness_magic"));
	}

	/** how well a style fits the task: one that can't hurt the monster ranks below any that can */
	private static final int UNARMED = 0;
	private static final int CANT_HURT = 1;
	private static final int ARMED = 2;
	/** armed with the task's own weapon: its bane, or the only kind that hurts the monster */
	private static final int TASK_WEAPON = 3;

	/** One recommended style's take on the task. */
	private static class Choice
	{
		final String style;
		final TabData.Tab def;
		final Built built;
		/** the items it was built from (no two-handed weapons when the task needs a shield) */
		final Context in;
		/** the style's bane weapon for the task, or null */
		Placement bane;
		/** for a monster only some means hurt (kurask): the style's means, or null */
		Placement means;
		TabData.TaskItem meansItem;
		int rank;

		Choice(String style, TabData.Tab def, Built built, Context in)
		{
			this.style = style;
			this.def = def;
			this.built = built;
			this.in = in;
		}
	}

	/**
	 * The Slayer tab's lead while on a task: the best setup for it from what the player owns. Each
	 * recommended style is built from its best-in-slot set; the first armed with the task's own weapon wins,
	 * else the first with any weapon that can hurt the monster. Then the slayer helmet, a salve amulet on
	 * undead (only one that beats the helmet: their bonuses don't stack), the bane weapon and the task's own
	 * gear take their slots: a piece the task can't do without always does, one it only suggests fills an
	 * empty slot (not with another style's gear) or goes beside it. Then the task's tools, and its supplies
	 * with that style's potions, food and runes.
	 */
	private List<Section> slayerTask(TabData.SlayerTaskData task, Context ctx, Options options)
	{
		Options styleOptions = new Options();
		styleOptions.backups = 0;
		styleOptions.foodCount = options.foodCount;
		styleOptions.combatSupplies = options.combatSupplies;
		styleOptions.blightedSupplies = false;
		// every Krystilia task is in the Wilderness (its weapons win, so last), whatever the task's own
		// categories say; no other master's is
		List<String> categories = new ArrayList<>(task.categories);
		categories.remove("wilderness");
		if (options.slayerTask.isWilderness())
		{
			categories.add("wilderness");
		}
		boolean superAntifire = data.superAntifire.stream().anyMatch(ctx.owned::contains);
		// a shield the task can't do without (the mirror shield on basilisks) leaves one hand for the weapon
		boolean needsShield = task.slots.stream().anyMatch(i -> "SHIELD".equals(i.slot) && needed(i, superAntifire)
			&& !firstOwned(i.entries, ctx, 1).isEmpty());
		Choice c = chooseStyle(task, categories, needsShield ? oneHanded(ctx.owned) : ctx.owned, options, styleOptions);
		if (needsShield && (c == null || c.rank < ARMED))
		{
			// only two-handed weapons: one of them beats none
			Choice any = chooseStyle(task, categories, ctx.owned, options, styleOptions);
			if (any != null && (c == null || any.rank > c.rank))
			{
				c = any;
			}
		}
		if (c == null)
		{
			return Collections.emptyList();
		}
		String style = c.style;
		Map<String, Placement> main = new LinkedHashMap<>();
		Map<String, List<Placement>> byGroup = new HashMap<>();
		for (Section sec : c.built.getSections())
		{
			for (Placement p : sec.getItems())
			{
				if ("main".equals(sec.getKey()) && p.getSlot() != null)
				{
					main.putIfAbsent(p.getSlot(), p);
				}
				byGroup.computeIfAbsent(p.getGroup(), g -> new ArrayList<>()).add(p);
			}
		}
		String on = "On task: " + task.name;
		Placement styledWeapon = main.get("WEAPON");
		Placement styledHead = main.get("HEAD");
		boolean helmet = takeSlot(main, byGroup.get("slayer_helm"), "HEAD", "Slayer helmet: stronger on every slayer task",
			c.def, "slayer_helm");
		if (categories.contains("undead"))
		{
			Placement there = main.get("AMULET");
			List<Placement> salves = byGroup.get("salve");
			int salve = salves != null && !salves.isEmpty() ? salves.get(0).getItemId()
				: there != null && entryIndex(c.def, "salve", there.getItemId()) >= 0 ? there.getItemId() : -1;
			// best first: (ei), then on melee (e), (i), plain
			int tier = entryIndex(c.def, "salve", salve);
			// its bonus replaces the helmet's, never adds to it: only the (ei), or the (e) on melee, does better
			if (salve != -1 && (!helmet || tier == 0 || tier == 1 && "melee".equals(style)))
			{
				main.put("AMULET", new Placement(salve, "AMULET", "task_AMULET", "Salve amulet: stronger against undead"));
				if (helmet && styledHead != null && entryIndex(c.def, "slayer_helm", styledHead.getItemId()) < 0)
				{
					// the helmet adds nothing over the salve: the best helmet for stats instead
					main.put("HEAD", styledHead);
				}
			}
		}
		if (c.bane != null)
		{
			main.put("WEAPON", c.bane);
		}
		fitAmmo(main, styledWeapon, c.in);
		Placement weapon = main.get("WEAPON");

		List<Placement> beside = new ArrayList<>();
		Set<String> needs = new HashSet<>();
		boolean taskAmmo = false;
		for (TabData.TaskItem item : task.slots)
		{
			if (item.style != null)
			{
				// one style's only means: it takes its slot in that style's set and goes beside any other
				Placement p = item == c.meansItem ? c.means : first(item, ctx);
				if (p == null)
				{
					continue;
				}
				if (item == c.meansItem)
				{
					main.put(item.slot, p);
					needs.add(item.slot);
					taskAmmo |= "AMMO".equals(item.slot);
				}
				else
				{
					beside.add(p);
				}
				continue;
			}
			Placement p = first(item, ctx);
			if (p == null)
			{
				continue;
			}
			Placement there = main.get(item.slot);
			boolean need = needed(item, superAntifire);
			// the slot already holds it, or (when only suggested) another of the task's choices for it, like an
			// auto-picked dragonfire ward: that stays, for the task's reason
			if (there != null && (there.getItemId() == p.getItemId()
				|| !need && item.entries.stream().anyMatch(e -> e.contains(there.getItemId()))))
			{
				main.put(item.slot, new Placement(there.getItemId(), item.slot, "task_" + item.slot, item.reason));
				if (need)
				{
					needs.add(item.slot);
				}
				continue;
			}
			// another style's gear never fills an empty slot (brutal arrows in a melee set's quiver)
			String of = styleOf(p.getItemId());
			if (need || there == null && (of == null || of.equals(style)))
			{
				main.put(item.slot, p);
				taskAmmo |= "AMMO".equals(item.slot);
				if (need)
				{
					needs.add(item.slot);
				}
			}
			else
			{
				beside.add(p);
			}
		}
		if (!taskAmmo)
		{
			fitAmmo(main, weapon, c.in);
		}
		Placement wielded = main.get("WEAPON");
		Placement shield = main.get("SHIELD");
		if (wielded != null && shield != null && !needs.contains("SHIELD") && twoHanded(wielded.getItemId()))
		{
			// both hands on the weapon: the shield waits beside it
			main.remove("SHIELD");
			beside.add(shield);
		}

		List<Section> out = new ArrayList<>();
		List<Placement> block = new ArrayList<>();
		for (Placement p : main.values())
		{
			block.add(new Placement(p.getItemId(), p.getSlot(), "task_" + p.getSlot(),
				p.getReason() != null ? p.getReason() : on));
		}
		List<Placement> alts = new ArrayList<>();
		for (Placement p : beside)
		{
			alts.add(new Placement(p.getItemId(), p.getSlot(), "task_" + p.getSlot(), p.getReason()));
		}
		// the style's prayer gear, drawn as its own equipment screen beside the setup as on the combat tabs
		alts.addAll(byGroup.getOrDefault("prayer_gear", Collections.emptyList()));
		out.add(new Section("ontask_main", "Task setup", "equipment", claim(block, ctx)));
		out.add(new Section("ontask_alts", "Task alternatives", "alts", claim(alts, ctx)));
		List<Placement> tools = new ArrayList<>();
		for (boolean required : new boolean[]{true, false})
		{
			for (TabData.TaskItem item : task.tools)
			{
				if (item.required == required)
				{
					for (int id : firstOwned(item.entries, ctx, 1))
					{
						tools.add(new Placement(id, null, "task_tools", item.reason));
					}
				}
			}
		}
		out.add(new Section("ontask_items", "Task items", "rows", claim(tools, ctx)));
		List<Placement> supplies = new ArrayList<>();
		for (List<Integer> entry : task.supplies)
		{
			for (int id : ownedOf(entry, ctx))
			{
				supplies.add(new Placement(id, null, "task_supplies", "Needed on this task"));
			}
		}
		for (Section sec : c.built.getSections())
		{
			// a spellcasting setup takes its runes (and the pouch): all but the teleport and alching ones
			if (sec.getKey().startsWith("supplies")
				|| sec.getKey().startsWith("runes") && !"runes_utility".equals(sec.getKey()))
			{
				supplies.addAll(sec.getItems());
			}
		}
		out.add(new Section("ontask_supplies", "Task supplies", "rows", claim(supplies, ctx)));
		return out;
	}

	/** Each recommended style built from {@code pool}, ranked; the best (the earliest of equals), or null. */
	private Choice chooseStyle(TabData.SlayerTaskData task, List<String> categories, Set<Integer> pool, Options options,
		Options styleOptions)
	{
		Context in = new Context(pool, options);
		Choice best = null;
		List<String> styles = task.styles.isEmpty() ? Collections.singletonList("melee") : task.styles;
		for (String style : styles)
		{
			TabData.Tab def = data.tabs.stream().filter(t -> t.key.equals(style)).findFirst().orElse(null);
			if (def == null)
			{
				continue;
			}
			Choice c = new Choice(style, def, build(def, pool, styleOptions), in);
			c.bane = bane(def, style, categories, in);
			// the lance, arclight and leaf-bladed weapons stay out of the main weapon pick: a bane counts too
			Placement weapon = c.bane != null ? c.bane : mainItem(c.built, "WEAPON");
			boolean limited = false;
			for (TabData.TaskItem item : task.slots)
			{
				if (item.required && style.equals(item.style))
				{
					limited = true;
					if (c.means == null)
					{
						c.means = means(item, weapon, in);
						c.meansItem = c.means != null ? item : null;
					}
				}
			}
			boolean armed = weapon != null || c.means != null && "WEAPON".equals(c.means.getSlot());
			c.rank = !armed ? UNARMED : limited && c.means == null ? CANT_HURT
				: c.bane != null || c.means != null ? TASK_WEAPON : ARMED;
			if (best == null || c.rank > best.rank)
			{
				best = c;
			}
			if (c.rank == TASK_WEAPON)
			{
				break;
			}
		}
		return best;
	}

	/**
	 * The style's owned bane weapon for these task categories (a later category's wins: the Wilderness
	 * weapon over demonbane), or null. Read from the pick itself: the style's build may show the weapon in
	 * a stab, slash or crush row instead.
	 */
	private static Placement bane(TabData.Tab def, String style, List<String> categories, Context in)
	{
		Placement bane = null;
		for (String category : categories)
		{
			for (String key : BANES.getOrDefault(style + ":" + category, Collections.emptyList()))
			{
				TabData.Pick pick = pick(def, key);
				List<Integer> got = pick == null ? Collections.emptyList() : firstOwned(pick.entries, in, 1);
				if (!got.isEmpty())
				{
					bane = new Placement(got.get(0), "WEAPON", "task_WEAPON", entryReason(pick, got.get(0), in));
					break;
				}
			}
		}
		return bane;
	}

	/** The owned item of a style's only means that works with its weapon (broad bolts need a crossbow), or null. */
	private Placement means(TabData.TaskItem item, Placement weapon, Context in)
	{
		String cls = weapon != null ? launcherAmmo.get(weapon.getItemId()) : null;
		List<List<Integer>> fits = cls != null ? data.ammoClasses.getOrDefault(cls, Collections.emptyList())
			: Collections.emptyList();
		for (List<Integer> entry : item.entries)
		{
			for (int id : ownedOf(entry, in))
			{
				if (!"AMMO".equals(item.slot) || fits.stream().anyMatch(e -> e.contains(id)))
				{
					return new Placement(id, item.slot, "task_" + item.slot, item.reason);
				}
			}
		}
		return null;
	}

	/** A launcher swapped into the set fires its own ammo: the set's ammo fit the weapon it replaced. */
	private void fitAmmo(Map<String, Placement> main, Placement before, Context in)
	{
		Placement weapon = main.get("WEAPON");
		int was = before != null ? before.getItemId() : -1;
		if (weapon == null || weapon.getItemId() == was
			|| !launcherAmmo.containsKey(was) && !launcherAmmo.containsKey(weapon.getItemId()))
		{
			// the same weapon, or neither takes ammo (melee, magic): the slot stays as it is
			return;
		}
		main.remove("AMMO");
		String cls = launcherAmmo.get(weapon.getItemId());
		List<Integer> ammo = cls == null ? Collections.emptyList()
			: firstOwned(data.ammoClasses.getOrDefault(cls, Collections.emptyList()), in, 1);
		if (!ammo.isEmpty())
		{
			main.put("AMMO", new Placement(ammo.get(0), "AMMO", "task_AMMO", "Best ammo for this weapon"));
		}
	}

	/** The task can't be done without it: required, or dragonfire protection without a super antifire. */
	private static boolean needed(TabData.TaskItem item, boolean superAntifire)
	{
		return item.required || item.dragonfire && !superAntifire;
	}

	/** The task item's best owned choice, or null. */
	private static Placement first(TabData.TaskItem item, Context ctx)
	{
		List<Integer> got = firstOwned(item.entries, ctx, 1);
		return got.isEmpty() ? null : new Placement(got.get(0), item.slot, "task_" + item.slot, item.reason);
	}

	/** The combat style an item's attack bonuses serve, or null for one without (armour, shields, blessings). */
	private String styleOf(int id)
	{
		EquipStats s = stats.apply(id);
		if (s == null)
		{
			return null;
		}
		if (s.getRanged() > 0 || s.getRstr() > 0)
		{
			return "ranged";
		}
		if (s.getMdmg() > 0 || s.getMagic() > 0 && s.getMagic() * 4 >= s.bestMelee())
		{
			return "magic";
		}
		return s.bestMelee() > 0 || s.getStr() > 0 ? "melee" : null;
	}

	private boolean twoHanded(int id)
	{
		EquipStats s = stats.apply(id);
		return s != null && s.isTwoHanded();
	}

	private Set<Integer> oneHanded(Set<Integer> owned)
	{
		Set<Integer> out = new HashSet<>();
		for (int id : owned)
		{
			if (!twoHanded(id))
			{
				out.add(id);
			}
		}
		return out;
	}

	private static boolean armedWith(Built b)
	{
		return mainItem(b, "WEAPON") != null;
	}

	/** The item in a slot of the build's best-in-slot set, or null. */
	private static Placement mainItem(Built b, String slot)
	{
		return b.getSections().stream().filter(sec -> "main".equals(sec.getKey()))
			.flatMap(sec -> sec.getItems().stream()).filter(p -> slot.equals(p.getSlot())).findFirst().orElse(null);
	}

	private static TabData.Pick pick(TabData.Tab def, String key)
	{
		return def.sections.stream().flatMap(sec -> sec.picks.stream()).filter(p -> key.equals(p.key))
			.findFirst().orElse(null);
	}

	/** Which entry of the tab's pick holds the item (0 = its best), or -1. */
	private static int entryIndex(TabData.Tab def, String key, int id)
	{
		TabData.Pick pick = pick(def, key);
		if (pick == null || id == -1)
		{
			return -1;
		}
		for (int i = 0; i < pick.entries.size(); i++)
		{
			if (pick.entries.get(i).contains(id))
			{
				return i;
			}
		}
		return -1;
	}

	/**
	 * Put the first of these placements into a slot of the task block. When there are none, the slot may
	 * hold the pick's item already (the best-in-slot pick took it): it stays, for this reason. False if
	 * neither.
	 */
	private static boolean takeSlot(Map<String, Placement> main, List<Placement> from, String slot, String reason,
		TabData.Tab def, String key)
	{
		boolean listed = from != null && !from.isEmpty();
		Placement p = listed ? from.get(0) : main.get(slot);
		if (p == null || !listed && entryIndex(def, key, p.getItemId()) < 0)
		{
			return false;
		}
		main.put(slot, new Placement(p.getItemId(), slot, "task_" + slot, reason));
		return true;
	}

	/** Keep each item once in the tab: the general Slayer sections below skip what the task block shows. */
	private static List<Placement> claim(List<Placement> items, Context ctx)
	{
		List<Placement> out = new ArrayList<>();
		for (Placement p : items)
		{
			if (ctx.placed.add(p.getItemId()))
			{
				out.add(p);
			}
		}
		return out;
	}

	/**
	 * Fill a skill tab's empty gear slots: first with an alternative the player owns for that slot (moved
	 * into the block), then with their lightest gear. Graceful alone never makes a tab.
	 */
	private void fill(TabData.Pick filler, Section main, List<Section> sections, Context ctx)
	{
		Set<String> filled = new HashSet<>();
		main.items.forEach(p -> filled.add(p.getSlot()));
		for (Section alts : sections)
		{
			if (!"alts".equals(alts.layout))
			{
				continue;
			}
			Map<String, Integer> groupSize = new HashMap<>();
			alts.items.forEach(p -> groupSize.merge(p.getGroup(), 1, Integer::sum));
			for (Placement p : new ArrayList<>(alts.items))
			{
				// single items only: a set keeps its own column
				if (p.getSlot() != null && !filled.contains(p.getSlot()) && groupSize.get(p.getGroup()) == 1)
				{
					alts.items.remove(p);
					main.items.add(p);
					filled.add(p.getSlot());
				}
			}
		}
		ctx.sectionTitle = main.title;
		ctx.sectionSlots.clear();
		ctx.sectionSlots.addAll(filled);
		ctx.sectionSlots.remove(null);
		List<Placement> got = resolve(filler, ctx);
		boolean alone = sections.stream().allMatch(s -> s.items.isEmpty());
		if (!alone)
		{
			main.items.addAll(got);
		}
	}

	private static class Context
	{
		final Set<Integer> owned;
		final Options options;
		final Set<Integer> placed = new LinkedHashSet<>();
		/** pick key -> every item the pick settled on, including ones placed earlier */
		final Map<String, List<Integer>> resolved = new HashMap<>();
		String sectionTitle;
		/** equipment slots something in the current section already fills */
		final Set<String> sectionSlots = new HashSet<>();
		/** attack style of the main melee weapon; melee armour accuracy follows it */
		String meleeStyle = "any";

		Context(Set<Integer> owned, Options options)
		{
			this.owned = owned;
			this.options = options;
		}
	}

	private List<Placement> resolve(TabData.Pick pick, Context ctx)
	{
		if (!pick.requires.isEmpty())
		{
			Set<Integer> pool = pick.requiresPlaced ? ctx.placed : ctx.owned;
			if (pick.requires.stream().noneMatch(pool::contains))
			{
				return Collections.emptyList();
			}
		}
		for (String other : pick.unless)
		{
			if (!ctx.resolved.getOrDefault(other, Collections.emptyList()).isEmpty())
			{
				return Collections.emptyList();
			}
		}

		List<Placement> out = new ArrayList<>();
		List<Integer> chosen = new ArrayList<>();
		switch (pick.mode)
		{
			case "first":
			{
				// a pick that shows several on purpose (top foods) doesn't call the extras backups
				boolean food = "food".equals(pick.key);
				int take = food ? ctx.options.foodCount : pick.take;
				List<List<Integer>> got = food ? foods(pick, ctx, take)
					: ownedEntries(pick.entries, ctx, take > 0 ? take : 1 + ctx.options.backups);
				for (int i = 0; i < got.size(); i++)
				{
					boolean best = i == 0 || take > 0;
					chosen.addAll(got.get(i));
					add(out, got.get(i), pick.slot, pick.key, ctx, id -> food ? foodReason(pick, id, ctx)
						: best ? entryReason(pick, id, ctx) : backupOf(entryReason(pick, id, ctx)));
				}
				if (chosen.isEmpty() && pick.fallback != null)
				{
					chosen.addAll(fallbackWeapon(pick.fallback, ctx));
					add(out, chosen, pick.slot, pick.key, ctx, id -> "Best " + pick.fallback + " weapon you own");
				}
				break;
			}
			case "all":
				for (int i = 0; i < pick.entries.size(); i++)
				{
					List<Integer> got = ownedOf(pick.entries.get(i), ctx);
					if (pick.one && got.size() > 1)
					{
						got = got.subList(0, 1);
					}
					String group = i < pick.groups.size() ? pick.groups.get(i) : pick.key;
					String reason = i < pick.reasons.size() && !pick.reasons.get(i).isEmpty() ? pick.reasons.get(i)
						: pick.label != null ? pick.label : ctx.sectionTitle;
					chosen.addAll(got);
					if (pick.repeat)
					{
						for (int id : got)
						{
							ctx.placed.add(id);
							out.add(new Placement(id, pick.slot, group, reason, pick.entries.get(i).indexOf(id)));
						}
					}
					else
					{
						add(out, got, pick.slot, group, ctx, id -> reason);
					}
				}
				break;
			case "auto":
				chosen.addAll(firstOwned(pick.entries, ctx, 1 + ctx.options.backups));
				if (chosen.isEmpty())
				{
					chosen.addAll(bestBy(slotCandidates(pick.slot, ctx),
						id -> Scoring.armour(pick.profile, stats.apply(id), ctx.meleeStyle), 1 + ctx.options.backups));
				}
				add(out, chosen, pick.slot, pick.key, ctx, id -> ranked(chosen, id,
					pick.label != null ? pick.label : "Best " + slotName(pick.slot) + " for " + pick.profile + " stats"));
				break;
			case "weapon":
				chosen.addAll(bestMeleeWeapons(pick.style, ctx));
				if ("any".equals(pick.style) && !chosen.isEmpty())
				{
					ctx.meleeStyle = mainStyle(stats.apply(chosen.get(0)));
				}
				add(out, chosen, "WEAPON", pick.key, ctx, id -> ranked(chosen, id,
					"any".equals(pick.style) ? "Best melee weapon for damage" : "Best " + pick.style + " weapon"));
				break;
			case "ammo":
				chosen.addAll(ammoFor(pick.launcher, ctx));
				add(out, chosen, pick.slot, pick.key, ctx, id -> ranked(chosen, id, "Best ammo for your best weapon"));
				break;
			case "set":
			{
				List<List<Integer>> pieces = new ArrayList<>();
				for (TabData.Piece piece : pick.pieces)
				{
					pieces.add(firstOwned(piece.entries, ctx, 1));
				}
				if (pick.complete && pieces.stream().anyMatch(List::isEmpty))
				{
					break;
				}
				String fallback = pick.label != null ? pick.label : ctx.sectionTitle;
				for (int i = 0; i < pieces.size(); i++)
				{
					TabData.Piece piece = pick.pieces.get(i);
					chosen.addAll(pieces.get(i));
					add(out, pieces.get(i), piece.slot, pick.key, ctx, id -> pieceReason(piece, id, fallback));
				}
				break;
			}
			case "outfit":
				outfit(pick, ctx, out, chosen);
				break;
			case "prayerset":
				prayerSet(pick, ctx, out, chosen);
				break;
			default:
				break;
		}
		ctx.resolved.put(pick.key, chosen);
		return out;
	}

	private static void add(List<Placement> out, Collection<Integer> ids, String slot, String group, Context ctx,
		IntFunction<String> reason)
	{
		for (int id : ids)
		{
			if (ctx.placed.add(id))
			{
				out.add(new Placement(id, slot, group, reason.apply(id)));
				if (slot != null)
				{
					ctx.sectionSlots.add(slot);
				}
			}
		}
	}

	/** The curated reason for the entry holding this item, else the pick label, else the section title. */
	private static String entryReason(TabData.Pick pick, int id, Context ctx)
	{
		for (int i = 0; i < pick.entries.size(); i++)
		{
			if (pick.entries.get(i).contains(id))
			{
				if (i < pick.reasons.size() && pick.reasons.get(i) != null && !pick.reasons.get(i).isEmpty())
				{
					return pick.reasons.get(i);
				}
				break;
			}
		}
		return pick.label != null ? pick.label : ctx.sectionTitle;
	}

	/**
	 * The player's best foods, each with its owned versions: by what one eat heals them now (RuneLite's
	 * item stats, so anglerfish follows their Hitpoints and a food no list names still counts) plus any heal
	 * that follows a few seconds later, then by HP per inventory slot, then the list's order. Without live
	 * numbers the wiki's decide.
	 */
	private List<List<Integer>> foods(TabData.Pick pick, Context ctx, int take)
	{
		List<List<Integer>> foods = new ArrayList<>();
		List<double[]> keys = new ArrayList<>();
		Set<Integer> listed = new HashSet<>();
		for (int i = 0; i < pick.entries.size(); i++)
		{
			listed.addAll(pick.entries.get(i));
			List<Integer> got = ownedOf(pick.entries.get(i), ctx);
			if (got.isEmpty())
			{
				continue;
			}
			// the wiki's [HP per eat, HP a few seconds later, HP per inventory slot]
			List<Double> wiki = i < pick.heals.size() ? pick.heals.get(i) : Collections.emptyList();
			int live = got.stream().mapToInt(heals::applyAsInt).max().orElse(0);
			double eat = (live > 0 ? live : at(wiki, 0)) + at(wiki, 1);
			if (eat < MIN_FOOD_HEAL && (live > 0 || !wiki.isEmpty()))
			{
				continue;
			}
			foods.add(got);
			keys.add(new double[]{eat, Math.max(eat, at(wiki, 2)), i});
		}
		for (int id : new TreeSet<>(ctx.owned))
		{
			// a food the list doesn't know (new content) still counts, by what it heals
			int live = listed.contains(id) || notFood.contains(id) ? 0 : heals.applyAsInt(id);
			if (live >= MIN_FOOD_HEAL)
			{
				foods.add(Collections.singletonList(id));
				keys.add(new double[]{live, live, pick.entries.size() + id});
			}
		}
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < foods.size(); i++)
		{
			order.add(i);
		}
		order.sort(Comparator.<Integer>comparingDouble(i -> -keys.get(i)[0])
			.thenComparingDouble(i -> -keys.get(i)[1]).thenComparingDouble(i -> keys.get(i)[2]));
		return order.stream().limit(take).map(foods::get).collect(Collectors.toList());
	}

	private static double at(List<Double> values, int i)
	{
		return i < values.size() && values.get(i) != null ? values.get(i) : 0;
	}

	/** A listed food's own note; one the list doesn't know says what it heals. */
	private String foodReason(TabData.Pick pick, int id, Context ctx)
	{
		int live = heals.applyAsInt(id);
		boolean listed = pick.entries.stream().anyMatch(e -> e.contains(id));
		return listed || live <= 0 ? entryReason(pick, id, ctx) : "Heals " + live;
	}

	private static String pieceReason(TabData.Piece piece, int id, String fallback)
	{
		for (int i = 0; i < piece.entries.size() && i < piece.reasons.size(); i++)
		{
			if (piece.entries.get(i).contains(id) && !piece.reasons.get(i).isEmpty())
			{
				return piece.reasons.get(i);
			}
		}
		return fallback;
	}

	private static String ranked(List<Integer> chosen, int id, String reason)
	{
		return chosen.indexOf(id) > 0 ? backupOf(reason) : reason;
	}

	private static String backupOf(String reason)
	{
		return reason == null || reason.isEmpty() ? "Backup"
			: "Backup: " + reason.substring(0, 1).toLowerCase() + reason.substring(1);
	}

	static String slotName(String slot)
	{
		switch (slot == null ? "" : slot)
		{
			case "HEAD":
				return "helmet";
			case "CAPE":
				return "cape";
			case "AMULET":
				return "amulet";
			case "AMMO":
				return "ammo slot item";
			case "WEAPON":
				return "weapon";
			case "BODY":
				return "body";
			case "SHIELD":
				return "shield";
			case "LEGS":
				return "legs";
			case "GLOVES":
				return "gloves";
			case "BOOTS":
				return "boots";
			case "RING":
				return "ring";
			default:
				return "item";
		}
	}

	private static List<Integer> ownedOf(List<Integer> entry, Context ctx)
	{
		List<Integer> ids = new ArrayList<>();
		for (int id : entry)
		{
			if (ctx.owned.contains(id))
			{
				ids.add(id);
			}
		}
		return ids;
	}

	/** Owned items of the first {@code count} owned entries, best first. */
	private static List<Integer> firstOwned(List<List<Integer>> entries, Context ctx, int count)
	{
		List<Integer> ids = new ArrayList<>();
		ownedEntries(entries, ctx, count).forEach(ids::addAll);
		return ids;
	}

	/** The owned items of each of the first {@code count} entries that have any. */
	private static List<List<Integer>> ownedEntries(List<List<Integer>> entries, Context ctx, int count)
	{
		List<List<Integer>> out = new ArrayList<>();
		for (List<Integer> entry : entries)
		{
			List<Integer> got = ownedOf(entry, ctx);
			if (!got.isEmpty())
			{
				out.add(got);
				if (out.size() >= count)
				{
					break;
				}
			}
		}
		return out;
	}

	private List<Integer> slotCandidates(String slot, Context ctx)
	{
		int idx = EquipmentInventorySlot.valueOf(slot).getSlotIdx();
		List<Integer> ids = new ArrayList<>();
		for (int id : ctx.owned)
		{
			EquipStats s = stats.apply(id);
			if (s != null && s.getSlot() == idx && !excludeAuto.contains(id) && !situationalOnly.contains(id))
			{
				ids.add(id);
			}
		}
		return ids;
	}

	/** The top {@code count} candidates scoring above zero, ties broken by id for stable layouts. */
	private static List<Integer> bestBy(List<Integer> candidates, ToDoubleFunction<Integer> score, int count)
	{
		Map<Integer, Double> scores = new HashMap<>();
		for (int id : candidates)
		{
			double s = score.applyAsDouble(id);
			if (s > 0)
			{
				scores.put(id, s);
			}
		}
		return scores.keySet().stream()
			.sorted(Comparator.<Integer>comparingDouble(scores::get).reversed().thenComparing(Comparator.naturalOrder()))
			.limit(count)
			.collect(Collectors.toList());
	}

	private List<Integer> bestMeleeWeapons(String style, Context ctx)
	{
		boolean any = "any".equals(style);
		List<Integer> candidates = new ArrayList<>();
		for (int id : ctx.owned)
		{
			EquipStats s = stats.apply(id);
			// a role only considers weapons built for it: the fang's slash is a defensive
			// style nobody trains with, so it never counts as a slash weapon
			if (!excludeMeleeWeapon.contains(id) && !excludeAuto.contains(id) && Scoring.isMeleeWeapon(s)
				&& (any || Scoring.styleBonus(s, style) >= s.bestMelee()))
			{
				candidates.add(id);
			}
		}
		// role picks compare raw single-target damage; only the overall pick
		// credits passives like the scythe's extra hits
		List<Integer> best = bestBy(candidates,
			id -> any ? scoring.meleeWeapon(id, stats.apply(id), style) : scoring.meleeWeaponPlain(id, stats.apply(id), style),
			1 + ctx.options.backups);
		// a role whose best weapon is already in the tab adds nothing
		if (!any && !best.isEmpty() && ctx.placed.contains(best.get(0)))
		{
			return Collections.emptyList();
		}
		return best;
	}

	private List<Integer> fallbackWeapon(String profile, Context ctx)
	{
		List<Integer> candidates = slotCandidates("WEAPON", ctx);
		ToDoubleFunction<Integer> score = Scoring.RANGED.equals(profile)
			? id -> Scoring.rangedWeapon(stats.apply(id))
			: id -> Scoring.magicWeapon(stats.apply(id));
		return bestBy(candidates, score, 1);
	}

	private List<Integer> ammoFor(String launcherPick, Context ctx)
	{
		List<Integer> launchers = ctx.resolved.getOrDefault(launcherPick, Collections.emptyList());
		if (launchers.isEmpty())
		{
			return Collections.emptyList();
		}
		String cls = launcherAmmo.get(launchers.get(0));
		if (cls != null)
		{
			return firstOwned(data.ammoClasses.getOrDefault(cls, Collections.emptyList()), ctx, 1 + ctx.options.backups);
		}
		// crystal bows, blowpipes and the like load their own ammo (a blessing is a situational pick)
		return Collections.emptyList();
	}

	/**
	 * Prayer bonus is situational: for each slot the owned item with the most
	 * prayer (ties to the better one for the tab's style), kept out of the main set.
	 */
	private void prayerSet(TabData.Pick pick, Context ctx, List<Placement> out, List<Integer> chosen)
	{
		for (String slot : pick.slots)
		{
			int idx = EquipmentInventorySlot.valueOf(slot).getSlotIdx();
			List<Integer> candidates = new ArrayList<>();
			for (int id : ctx.owned)
			{
				EquipStats s = stats.apply(id);
				if (s != null && s.getSlot() == idx && s.getPrayer() > 0 && !excludeAuto.contains(id))
				{
					candidates.add(id);
				}
			}
			List<Integer> best = bestBy(candidates, id -> stats.apply(id).getPrayer() * 100.0
				+ Math.max(0, Scoring.armour(pick.profile, stats.apply(id), ctx.meleeStyle)), 1);
			if (best.isEmpty() || ctx.placed.contains(best.get(0)))
			{
				continue;
			}
			int prayer = stats.apply(best.get(0)).getPrayer();
			chosen.addAll(best);
			add(out, best, slot, pick.key, ctx, id -> "Prayer gear (situational): +" + prayer + " prayer");
		}
	}

	private static String mainStyle(EquipStats s)
	{
		if (s == null)
		{
			return "any";
		}
		int best = s.bestMelee();
		return s.getSlash() == best ? "slash" : s.getStab() == best ? "stab" : "crush";
	}

	/**
	 * The most complete variant of an outfit (e.g. one graceful colour), with any
	 * missing slot filled by the best item for the pick's profile.
	 */
	/**
	 * The most complete outfit among the pick's variants (graceful colours, spirit angler or angler): each
	 * slot takes that outfit's piece, and a slot it lacks takes the first owned item of the slot's fallback
	 * ("pieces"). A "complete" outfit (graceful) counts only when worn whole: pieces of any variants mix,
	 * and unless every slot is covered, every slot takes its fallback instead (the lightest item).
	 * A "fill" outfit only takes the slots the section left empty (graceful on the skill tabs).
	 */
	private void outfit(TabData.Pick pick, Context ctx, List<Placement> out, List<Integer> chosen)
	{
		List<TabData.Piece> best = null;
		int bestCount = 0;
		for (List<TabData.Piece> variant : pick.variants)
		{
			int count = 0;
			for (TabData.Piece piece : variant)
			{
				if (!firstOwned(piece.entries, ctx, 1).isEmpty())
				{
					count++;
				}
			}
			if (count > bestCount)
			{
				best = variant;
				bestCount = count;
			}
		}
		List<TabData.Piece> slots = best != null ? best : pick.variants.isEmpty() ? Collections.emptyList() : pick.variants.get(0);
		// slots the skill's own pieces already fill: a filler can't be worn whole then
		Set<String> taken = pick.fill ? new HashSet<>(ctx.sectionSlots) : Collections.emptySet();
		boolean blocked = slots.stream().anyMatch(p -> taken.contains(p.slot));
		boolean whole = best != null && !blocked && (!pick.complete || wornWhole(pick, slots.size(), ctx));
		String label = pick.label != null ? pick.label : "Your most complete outfit";
		for (int i = 0; i < slots.size(); i++)
		{
			TabData.Piece piece = slots.get(i);
			List<Integer> ids = whole ? firstOwned(piece.entries, ctx, 1) : Collections.emptyList();
			if (taken.contains(piece.slot))
			{
				// the skill's piece keeps the slot; your graceful piece (any colour) stands beside it, else the
				// lightest item below (the planner moves a second item for a slot into the alternatives)
				for (int v = -1; ids.isEmpty() && v < pick.variants.size(); v++)
				{
					List<TabData.Piece> variant = v < 0 ? slots : pick.variants.get(v);
					ids = i < variant.size() ? firstOwned(variant.get(i).entries, ctx, 1) : Collections.emptyList();
				}
				if (!ids.isEmpty())
				{
					chosen.addAll(ids);
					add(out, ids, piece.slot, pick.key, ctx, id -> "Your graceful, to swap in when you'd rather run");
					continue;
				}
			}
			for (int v = 0; ids.isEmpty() && whole && pick.complete && v < pick.variants.size(); v++)
			{
				// colours mix: the same slot from another variant
				List<TabData.Piece> other = pick.variants.get(v);
				ids = i < other.size() ? firstOwned(other.get(i).entries, ctx, 1) : Collections.emptyList();
			}
			if (!ids.isEmpty())
			{
				chosen.addAll(ids);
				add(out, ids, piece.slot, pick.key, ctx, id -> label);
				continue;
			}
			TabData.Piece fallback = pick.pieces.stream().filter(p -> piece.slot.equals(p.slot)).findFirst().orElse(null);
			if (fallback != null)
			{
				List<Integer> got = firstOwned(fallback.entries, ctx, 1);
				chosen.addAll(got);
				add(out, got, piece.slot, pick.key, ctx,
					id -> pieceReason(fallback, id, "Lightest weight-reducing " + slotName(piece.slot)));
			}
			else if (pick.profile != null)
			{
				List<Integer> got = bestBy(slotCandidates(piece.slot, ctx),
					id -> Scoring.armour(pick.profile, stats.apply(id)), 1);
				chosen.addAll(got);
				add(out, got, piece.slot, pick.key, ctx, id -> "Lightest weight-reducing " + slotName(piece.slot));
			}
		}
	}

	/** Every slot has a piece from one variant or another (graceful colours mix for the set bonus). */
	private static boolean wornWhole(TabData.Pick pick, int slots, Context ctx)
	{
		for (int i = 0; i < slots; i++)
		{
			final int slot = i;
			if (pick.variants.stream().noneMatch(v -> slot < v.size() && !firstOwned(v.get(slot).entries, ctx, 1).isEmpty()))
			{
				return false;
			}
		}
		return true;
	}
}
