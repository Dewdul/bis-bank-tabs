package com.ultimatebanktabs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Arranges a built tab on the 8-wide bank grid.
 *
 * <pre>
 * col:   0      1      2    3    4..7
 * row 0:        HEAD             head alternatives
 * row 1: CAPE   NECK   AMMO      cape / neck / ammo alternatives
 * row 2: WEAPON BODY   SHIELD    body / shield alternatives
 * row 3:        LEGS             legs alternatives
 * row 4: GLOVES BOOTS  RING      gloves / boots / ring alternatives
 * </pre>
 *
 * The best-in-slot set sits in the equipment-screen shape, column 3 is a
 * gutter, and each situational set (void, inquisitor...) gets its own
 * alternatives column so its pieces line up with the slots they replace.
 * Everything else follows in row sections, each starting on a fresh row. A
 * "sets" section (cosmetics) also keeps each outfit on one row when it fits,
 * "goods" sections (what a skill makes) come last, after one blank row,
 * and runs of "columns" sections (herblore) go in bands of eight herbs: a row
 * each of grimy herbs, clean herbs, secondaries, unfinished potions and
 * potions, with every herb in the same column down its band.
 */
class LayoutPlanner
{
	static final int WIDTH = 8;
	private static final int BLOCK_ROWS = 5;
	private static final int FIRST_ALT_COLUMN = 4;

	private static final Map<String, int[]> DIAGRAM = new LinkedHashMap<>();

	static
	{
		DIAGRAM.put("HEAD", new int[]{0, 1});
		DIAGRAM.put("CAPE", new int[]{1, 0});
		DIAGRAM.put("AMULET", new int[]{1, 1});
		DIAGRAM.put("AMMO", new int[]{1, 2});
		DIAGRAM.put("WEAPON", new int[]{2, 0});
		DIAGRAM.put("BODY", new int[]{2, 1});
		DIAGRAM.put("SHIELD", new int[]{2, 2});
		DIAGRAM.put("LEGS", new int[]{3, 1});
		DIAGRAM.put("GLOVES", new int[]{4, 0});
		DIAGRAM.put("BOOTS", new int[]{4, 1});
		DIAGRAM.put("RING", new int[]{4, 2});
	}

	private final TreeMap<Integer, Integer> grid = new TreeMap<>();

	/**
	 * @param gapRows leave an empty row between sections
	 * @return item ids by bank position, -1 for empty slots
	 */
	static int[] plan(TabBuilder.Built tab, boolean gapRows)
	{
		return new LayoutPlanner().layout(tab, gapRows);
	}

	private int[] layout(TabBuilder.Built tab, boolean gapRows)
	{
		List<TabBuilder.Placement> overflow = new ArrayList<>();
		boolean block = false;
		for (TabBuilder.Section s : tab.getSections())
		{
			if ("equipment".equals(s.getLayout()) && !s.getItems().isEmpty())
			{
				block = true;
				for (TabBuilder.Placement p : s.getItems())
				{
					int[] cell = p.getSlot() == null ? null : DIAGRAM.get(p.getSlot());
					if (cell == null || !put(cell[0], cell[1], p.getItemId()))
					{
						// a second owned variant of the same pick (charged + uncharged...)
						overflow.add(p);
					}
				}
			}
		}
		for (TabBuilder.Section s : tab.getSections())
		{
			if ("alts".equals(s.getLayout()) && !s.getItems().isEmpty())
			{
				block = true;
				for (List<TabBuilder.Placement> group : groups(s.getItems()))
				{
					placeGroup(group, overflow);
				}
			}
		}
		List<TabBuilder.Placement> extra = new ArrayList<>(overflow);
		overflow.clear();
		for (TabBuilder.Placement p : extra)
		{
			if (!placeSingle(p))
			{
				overflow.add(p);
			}
		}

		int row = block ? BLOCK_ROWS : 0;
		List<TabBuilder.Section> rowSections = new ArrayList<>();
		if (!overflow.isEmpty())
		{
			rowSections.add(new TabBuilder.Section("overflow", "", "rows", overflow));
		}
		for (TabBuilder.Section s : tab.getSections())
		{
			String l = s.getLayout();
			if (("rows".equals(l) || "sets".equals(l) || "columns".equals(l) || "goods".equals(l)) && !s.getItems().isEmpty())
			{
				rowSections.add(s);
			}
		}
		List<String> columns = activeColumns(rowSections);
		boolean first = !block;
		boolean goodsStarted = false;
		for (int i = 0; i < rowSections.size(); i++)
		{
			TabBuilder.Section s = rowSections.get(i);
			boolean goods = "goods".equals(s.getLayout());
			// finished goods sit at the bottom after a blank row, whatever the gap setting
			if (gapRows && !first || goods && !goodsStarted && !first)
			{
				row++;
			}
			goodsStarted |= goods;
			first = false;
			if ("columns".equals(s.getLayout()))
			{
				int end = i;
				while (end + 1 < rowSections.size() && "columns".equals(rowSections.get(end + 1).getLayout()))
				{
					end++;
				}
				row = placeBands(rowSections.subList(i, end + 1), columns, row, gapRows);
				i = end;
				continue;
			}
			boolean sets = "sets".equals(s.getLayout());
			int col = 0;
			List<List<TabBuilder.Placement>> chunks = sets ? groups(s.getItems()) : Arrays.asList(s.getItems());
			for (List<TabBuilder.Placement> chunk : chunks)
			{
				// an outfit that fits on one row never straddles two
				if (sets && col > 0 && col + chunk.size() > WIDTH && chunk.size() <= WIDTH)
				{
					col = 0;
					row++;
				}
				for (TabBuilder.Placement p : chunk)
				{
					if (col == WIDTH)
					{
						col = 0;
						row++;
					}
					put(row, col++, p.getItemId());
				}
			}
			row++;
		}

		int size = grid.isEmpty() ? 0 : grid.lastKey() + 1;
		int[] layout = new int[size];
		Arrays.fill(layout, -1);
		grid.forEach((pos, id) -> layout[pos] = id);
		return layout;
	}

	/**
	 * Columns shared by every "columns" section (herbs): only those with something
	 * placed, in the data's order, so each herb keeps one column all the way down.
	 */
	private static List<String> activeColumns(List<TabBuilder.Section> sections)
	{
		List<String> order = new ArrayList<>();
		Set<String> used = new HashSet<>();
		for (TabBuilder.Section s : sections)
		{
			if ("columns".equals(s.getLayout()))
			{
				if (order.isEmpty())
				{
					order.addAll(s.getColumns());
				}
				s.getItems().forEach(p -> used.add(p.getGroup()));
			}
		}
		List<String> active = new ArrayList<>();
		for (String c : order)
		{
			if (used.contains(c))
			{
				active.add(c);
			}
		}
		return active;
	}

	/**
	 * Lay a run of columns sections out in bands of {@link #WIDTH} columns: for the
	 * first eight herbs a row per section (grimy, clean, secondary...), then the
	 * same rows for the next eight. Rows with nothing in a band are skipped.
	 * Returns the next free row.
	 */
	private int placeBands(List<TabBuilder.Section> sections, List<String> columns, int row, boolean gapRows)
	{
		int bands = (columns.size() + WIDTH - 1) / WIDTH;
		List<TabBuilder.Placement> extra = new ArrayList<>();
		for (int band = 0; band < bands; band++)
		{
			if (band > 0 && gapRows)
			{
				row++;
			}
			for (TabBuilder.Section s : sections)
			{
				boolean used = false;
				for (TabBuilder.Placement p : s.getItems())
				{
					int idx = columns.indexOf(p.getGroup());
					if (idx / WIDTH != band)
					{
						continue;
					}
					if (put(row, idx % WIDTH, p.getItemId()))
					{
						used = true;
					}
					else
					{
						extra.add(p);
					}
				}
				if (used)
				{
					row++;
				}
			}
		}
		int col = 0;
		for (TabBuilder.Placement p : extra)
		{
			if (col == WIDTH)
			{
				col = 0;
				row++;
			}
			put(row, col++, p.getItemId());
		}
		return extra.isEmpty() ? row : row + 1;
	}

	private static List<List<TabBuilder.Placement>> groups(List<TabBuilder.Placement> items)
	{
		Map<String, List<TabBuilder.Placement>> byGroup = new LinkedHashMap<>();
		for (TabBuilder.Placement p : items)
		{
			byGroup.computeIfAbsent(p.getGroup(), g -> new ArrayList<>()).add(p);
		}
		return new ArrayList<>(byGroup.values());
	}

	/** Keep a set's pieces in one alternatives column when they fit, else place them one by one. */
	private void placeGroup(List<TabBuilder.Placement> group, List<TabBuilder.Placement> overflow)
	{
		if (group.size() > 1)
		{
			int[] rows = new int[group.size()];
			boolean distinct = true;
			for (int i = 0; i < group.size(); i++)
			{
				rows[i] = rowOf(group.get(i));
				for (int j = 0; j < i; j++)
				{
					distinct &= rows[i] >= 0 && rows[i] != rows[j];
				}
			}
			if (distinct && rows[0] >= 0)
			{
				for (int col = FIRST_ALT_COLUMN; col < WIDTH; col++)
				{
					final int c = col;
					if (Arrays.stream(rows).allMatch(r -> free(r, c)))
					{
						for (int i = 0; i < group.size(); i++)
						{
							put(rows[i], col, group.get(i).getItemId());
						}
						return;
					}
				}
			}
		}
		for (TabBuilder.Placement p : group)
		{
			if (!placeSingle(p))
			{
				overflow.add(p);
			}
		}
	}

	private boolean placeSingle(TabBuilder.Placement p)
	{
		int row = rowOf(p);
		if (row < 0)
		{
			return false;
		}
		for (int col = FIRST_ALT_COLUMN; col < WIDTH; col++)
		{
			if (put(row, col, p.getItemId()))
			{
				return true;
			}
		}
		return false;
	}

	private static int rowOf(TabBuilder.Placement p)
	{
		int[] cell = p.getSlot() == null ? null : DIAGRAM.get(p.getSlot());
		return cell == null ? -1 : cell[0];
	}

	private boolean free(int row, int col)
	{
		return !grid.containsKey(row * WIDTH + col);
	}

	private boolean put(int row, int col, int itemId)
	{
		return grid.putIfAbsent(row * WIDTH + col, itemId) == null;
	}
}
