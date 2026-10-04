package com.ultimatebanktabs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Everything else follows in row sections, each starting on a fresh row.
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
		List<List<TabBuilder.Placement>> rowSections = new ArrayList<>();
		if (!overflow.isEmpty())
		{
			rowSections.add(overflow);
		}
		for (TabBuilder.Section s : tab.getSections())
		{
			if ("rows".equals(s.getLayout()) && !s.getItems().isEmpty())
			{
				rowSections.add(s.getItems());
			}
		}
		boolean first = !block;
		for (List<TabBuilder.Placement> items : rowSections)
		{
			if (gapRows && !first)
			{
				row++;
			}
			first = false;
			int col = 0;
			for (TabBuilder.Placement p : items)
			{
				if (col == WIDTH)
				{
					col = 0;
					row++;
				}
				put(row, col++, p.getItemId());
			}
			row++;
		}

		int size = grid.isEmpty() ? 0 : grid.lastKey() + 1;
		int[] layout = new int[size];
		Arrays.fill(layout, -1);
		grid.forEach((pos, id) -> layout[pos] = id);
		return layout;
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
