package com.ultimatebanktabs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
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
		boolean combatSupplies = true;
		/** how many foods the combat tabs show */
		int foodCount = 4;
		/** items other tabs already show; the cosmetics tab leaves them out */
		Set<Integer> shownElsewhere = Collections.emptySet();
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
	private final Set<Integer> excludeMeleeWeapon;
	private final Map<Integer, String> launcherAmmo = new HashMap<>();

	TabBuilder(TabData data, IntFunction<EquipStats> stats)
	{
		this.data = data;
		this.stats = stats;
		this.excludeAuto = new HashSet<>(data.excludeAuto);
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
		for (TabData.Section def : tab.sections)
		{
			if (def.supplies && !options.skillSupplies
				|| tab.isGear() && "supplies".equals(def.key) && !options.combatSupplies)
			{
				continue;
			}
			List<Placement> items = new ArrayList<>();
			// ammo depends on the launcher picked in the same section, so it goes last
			List<TabData.Pick> picks = new ArrayList<>(def.picks);
			picks.sort(Comparator.comparing(p -> "ammo".equals(p.mode)));
			ctx.sectionTitle = def.title;
			for (TabData.Pick pick : picks)
			{
				List<Placement> got = resolve(pick, ctx);
				if (hero == -1 && "main".equals(def.key) && "WEAPON".equals(pick.slot) && !got.isEmpty())
				{
					hero = got.get(0).itemId;
				}
				items.addAll(got);
			}
			List<String> columns = def.picks.stream().filter(p -> !p.groups.isEmpty()).findFirst()
				.map(p -> p.groups).orElse(Collections.emptyList());
			sections.add(new Section(def.key, def.title, def.layout, items, columns));
		}
		if (hero == -1)
		{
			hero = sections.stream().flatMap(s -> s.items.stream()).map(Placement::getItemId).findFirst().orElse(-1);
		}
		return new Built(tab, sections, hero);
	}

	private static class Context
	{
		final Set<Integer> owned;
		final Options options;
		final Set<Integer> placed = new LinkedHashSet<>();
		/** pick key -> every item the pick settled on, including ones placed earlier */
		final Map<String, List<Integer>> resolved = new HashMap<>();
		String sectionTitle;

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
				int take = "food".equals(pick.key) ? ctx.options.foodCount : pick.take;
				List<List<Integer>> got = ownedEntries(pick.entries, ctx, take > 0 ? take : 1 + ctx.options.backups);
				for (int i = 0; i < got.size(); i++)
				{
					boolean best = i == 0 || take > 0;
					chosen.addAll(got.get(i));
					add(out, got.get(i), pick.slot, pick.key, ctx,
						id -> best ? entryReason(pick, id, ctx) : backupOf(entryReason(pick, id, ctx)));
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
							out.add(new Placement(id, pick.slot, group, reason));
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
					chosen.addAll(bestBy(slotCandidates(pick.slot, ctx), id -> Scoring.armour(pick.profile, stats.apply(id)),
						1 + ctx.options.backups));
				}
				add(out, chosen, pick.slot, pick.key, ctx, id -> ranked(chosen, id,
					pick.label != null ? pick.label : "Best " + slotName(pick.slot) + " for " + pick.profile + " stats"));
				break;
			case "weapon":
				chosen.addAll(bestMeleeWeapons(pick.style, ctx));
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
			if (s != null && s.getSlot() == idx && !excludeAuto.contains(id))
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
		// crystal bows, blowpipes and the like load their own ammo: a prayer blessing goes there
		List<Integer> blessings = new ArrayList<>();
		for (int id : slotCandidates("AMMO", ctx))
		{
			EquipStats s = stats.apply(id);
			if (s.getRanged() == 0 && s.getRstr() == 0)
			{
				blessings.add(id);
			}
		}
		return bestBy(blessings, id -> stats.apply(id).getPrayer(), 1);
	}

	/**
	 * The most complete variant of an outfit (e.g. one graceful colour), with any
	 * missing slot filled by the best item for the pick's profile.
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
		for (TabData.Piece piece : slots)
		{
			List<Integer> ids = best != null ? firstOwned(piece.entries, ctx, 1) : Collections.emptyList();
			if (ids.isEmpty() && pick.profile != null)
			{
				ids = bestBy(slotCandidates(piece.slot, ctx), id -> Scoring.armour(pick.profile, stats.apply(id)), 1);
			}
			boolean fromOutfit = best != null && !firstOwned(piece.entries, ctx, 1).isEmpty();
			String reason = fromOutfit ? (pick.label != null ? pick.label : "Your most complete outfit")
				: "Lightest weight-reducing " + slotName(piece.slot);
			chosen.addAll(ids);
			add(out, ids, piece.slot, pick.key, ctx, id -> reason);
		}
	}
}
