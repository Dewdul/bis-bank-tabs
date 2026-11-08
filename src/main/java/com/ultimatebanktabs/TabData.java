package com.ultimatebanktabs;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The bundled tab definitions (tabs.json, built by tools/generate_data.py).
 * Field names mirror the JSON; see the generator for what each mode means.
 */
class TabData
{
	int version;
	String generated;
	List<Integer> excludeAuto = Collections.emptyList();
	/** never in a best-in-slot set, but allowed in situational picks (god books, Ardougne cloaks...) */
	List<Integer> situationalOnly = Collections.emptyList();
	List<Integer> excludeMeleeWeapon = Collections.emptyList();
	Map<String, Double> meleeMultiplier = Collections.emptyMap();
	List<Integer> doubleAccuracy = Collections.emptyList();
	Map<String, String> launcherAmmo = Collections.emptyMap();
	Map<String, List<List<Integer>>> ammoClasses = Collections.emptyMap();
	List<Tab> tabs = Collections.emptyList();
	/** in-game slayer task name (lower case) -> the setup it wants */
	Map<String, SlayerTaskData> slayerTasks = Collections.emptyMap();
	/** super antifire potions, every dose: full dragonfire immunity */
	List<Integer> superAntifire = Collections.emptyList();
	/** random, harmful, raid-only and blighted food: never shown as food, even when RuneLite counts it */
	List<Integer> notFood = Collections.emptyList();
	/** items kept for their charges (teleport jewellery, recoil rings, binding necklaces...): never offered for alching */
	List<Integer> neverAlch = Collections.emptyList();
	/** gear that spends charges or degrades as you use it -> what keeping it going costs (blood runes...) */
	Map<String, String> charged = Collections.emptyMap();

	/** What a slayer task wants (tools/slayer_tasks.py, from the OSRS Wiki). */
	static class SlayerTaskData
	{
		String name;
		boolean boss;
		/** recommended combat styles, best first */
		List<String> styles = Collections.emptyList();
		/** undead, demon, dragon, kalphite, vampyre, golem, leafy, wilderness... */
		List<String> categories = Collections.emptyList();
		/** gear the task wants in a slot; a required one takes the slot over */
		List<TaskItem> slots = Collections.emptyList();
		/** carried items (bag of salt, rock hammer, light source...) */
		List<TaskItem> tools = Collections.emptyList();
		/** potions the task calls for (antifire, anti-venom...), each with its doses */
		List<List<Integer>> supplies = Collections.emptyList();
	}

	static class TaskItem
	{
		String slot;
		/** ranked, best first */
		List<List<Integer>> entries = Collections.emptyList();
		boolean required;
		String reason;
		/** the one way this style can hurt the monster (leaf-bladed weapon, broad ammo, Magic Dart), or null */
		String style;
		/** dragonfire protection: needed unless the player has a super antifire */
		boolean dragonfire;
	}

	static class Tab
	{
		String key;
		String name;
		String kind;
		int icon;
		/** the icon is the first of these the player owns (most iconic first) */
		List<List<Integer>> iconEntries = Collections.emptyList();
		/** owning none of iconEntries means the default icon rather than the first item shown */
		boolean iconStrict;
		List<Section> sections = Collections.emptyList();

		boolean isGear()
		{
			return "gear".equals(kind);
		}

		/** Built in game from live prices: what is worth alching (TabBuilder.alch). */
		boolean isAlch()
		{
			return "alch".equals(kind);
		}

		/** Leftovers tab: shows only what no other tab already shows. */
		boolean isCosmetic()
		{
			return "cosmetic".equals(kind);
		}
	}

	static class Section
	{
		String key;
		String title;
		/** equipment, alts, rows, sets, columns, goods, bottom or separate (see LayoutPlanner) */
		String layout;
		/** generated from wiki recipes rather than curated */
		boolean supplies;
		/** goods a gathering skill brings in (logs, ores, raw fish, nests): raw material, never alch fodder */
		boolean gathered;
		List<Pick> picks = Collections.emptyList();
	}

	static class Pick
	{
		String key;
		/** first, all, auto, weapon, ammo, set, outfit, prayerset */
		String mode;
		String slot;
		String profile;
		String style;
		String launcher;
		String fallback;
		boolean complete;
		/** "first" picks: how many owned entries to show (0 = just the best); "food" follows the setting */
		int take;
		/** requirements must already be in the tab rather than merely owned */
		boolean requiresPlaced;
		/** "all" picks: an item may show again (a secondary shared by two herb columns) */
		boolean repeat;
		/** "all" picks: show only the first owned version of each entry (the fullest dose) */
		boolean one;
		/** "outfit" picks: only fill the slots the section left empty */
		boolean fill;
		/** "first" picks: with cheaper alternatives on, also the next owned entry, a cheaper tier that saves the best */
		boolean cheaper;
		/** with cheaper alternatives on, shown even when an "unless" pick resolved (a combat potion beside super combats) */
		boolean budgetKeeps;
		/** hover text: one per entry (may be shorter than entries), else the label */
		List<String> reasons = Collections.emptyList();
		/** "all" picks: the outfit/set each entry belongs to, kept together on one row by the "sets" layout */
		List<String> groups = Collections.emptyList();
		String label;
		List<String> unless = Collections.emptyList();
		/** "prayerset" picks: the slots to fill */
		List<String> slots = Collections.emptyList();
		List<Integer> requires = Collections.emptyList();
		List<List<Integer>> entries = Collections.emptyList();
		List<Piece> pieces = Collections.emptyList();
		List<List<Piece>> variants = Collections.emptyList();
		/** "food": per entry the wiki's [HP per eat, HP a few seconds later, HP per inventory slot] */
		List<List<Double>> heals = Collections.emptyList();
	}

	static class Piece
	{
		String slot;
		List<List<Integer>> entries = Collections.emptyList();
		List<String> reasons = Collections.emptyList();
	}

	static TabData load(Gson gson)
	{
		try (InputStream in = TabData.class.getResourceAsStream("tabs.json"))
		{
			if (in == null)
			{
				throw new IllegalStateException("tabs.json missing from the plugin jar");
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				return gson.fromJson(reader, TabData.class);
			}
		}
		catch (IOException e)
		{
			throw new IllegalStateException("could not read tabs.json", e);
		}
	}
}
