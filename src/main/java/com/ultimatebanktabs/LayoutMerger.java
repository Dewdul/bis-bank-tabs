package com.ultimatebanktabs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Keeps the player's own arrangement of a tab when its contents change.
 *
 * <p>A tab the player never rearranged simply takes the newly planned layout.
 * Once they have moved things, their layout is the starting point instead:
 * <ul>
 * <li>a new best-in-slot item takes its cell in the equipment block back,
 * whatever the player put there; whatever it pushed out moves to a free cell</li>
 * <li>any other new item goes where the plan puts it if that cell is free,
 * else the next free cell (never into the equipment block)</li>
 * <li>an item the plugin no longer shows leaves a gap</li>
 * <li>items the player tagged into the tab themselves stay where they are</li>
 * </ul>
 * Disabling and re-enabling the tab's layout starts over from the plan.
 */
final class LayoutMerger
{
	private static final int WIDTH = LayoutPlanner.WIDTH;
	/** rows and columns of the equipment block, incl. the gutter (see LayoutPlanner) */
	private static final int BLOCK_ROWS = 5;
	private static final int BLOCK_COLUMNS = 4;

	private LayoutMerger()
	{
	}

	/**
	 * The layout to save for a tab.
	 *
	 * @param saved    the tab's layout now (with the player's moves), or null if it has none
	 * @param previous the layout the plugin last planned for the tab, or null if unknown
	 * @param planned  the layout the plugin plans now
	 * @param bis      bank positions of the best-in-slot cells in {@code planned}
	 */
	static int[] next(int[] saved, int[] previous, int[] planned, Set<Integer> bis)
	{
		if (saved == null || previous == null || Arrays.equals(trim(saved), trim(previous)))
		{
			// never rearranged: the plan as it is
			return planned;
		}
		return merge(saved, previous, planned, bis);
	}

	static int[] merge(int[] saved, int[] previous, int[] planned, Set<Integer> bis)
	{
		List<Integer> out = new ArrayList<>();
		for (int id : saved)
		{
			out.add(id);
		}
		Set<Integer> before = ids(previous);
		Set<Integer> now = ids(planned);

		// what the plugin stopped showing leaves a gap; what the player tagged in stays
		for (int i = 0; i < out.size(); i++)
		{
			int id = out.get(i);
			if (id != -1 && before.contains(id) && !now.contains(id))
			{
				out.set(i, -1);
			}
		}

		// a new best in slot takes its cell back
		List<Integer> displaced = new ArrayList<>();
		for (int pos : new TreeSet<>(bis))
		{
			int best = at(planned, pos);
			if (best == -1 || best == at(previous, pos) || at(out, pos) == best)
			{
				continue;
			}
			for (int i = 0; i < out.size(); i++)
			{
				if (out.get(i) == best)
				{
					out.set(i, -1);
				}
			}
			int there = at(out, pos);
			set(out, pos, best);
			if (there != -1)
			{
				displaced.add(there);
			}
		}

		// new items, and anything a new best pushed out, where the plan puts them or the next free cell
		boolean block = !bis.isEmpty();
		Set<Integer> placed = ids(out);
		for (int i = 0; i < planned.length; i++)
		{
			int id = planned[i];
			if (id != -1 && !placed.contains(id))
			{
				set(out, free(out, i, block, bis), id);
				placed.add(id);
			}
		}
		for (int id : displaced)
		{
			if (!placed.contains(id))
			{
				// tagged in by the player: below the equipment block
				set(out, free(out, block ? BLOCK_ROWS * WIDTH : 0, block, bis), id);
				placed.add(id);
			}
		}
		return trim(toArray(out));
	}

	/** The planned cell if it is free, else the next free cell outside the equipment block. */
	private static int free(List<Integer> out, int start, boolean block, Set<Integer> bis)
	{
		if (at(out, start) == -1)
		{
			return start;
		}
		for (int pos = start + 1; ; pos++)
		{
			boolean reserved = bis.contains(pos)
				|| block && pos / WIDTH < BLOCK_ROWS && pos % WIDTH < BLOCK_COLUMNS;
			if (!reserved && at(out, pos) == -1)
			{
				return pos;
			}
		}
	}

	private static int at(int[] layout, int pos)
	{
		return pos < layout.length ? layout[pos] : -1;
	}

	private static int at(List<Integer> layout, int pos)
	{
		return pos < layout.size() ? layout.get(pos) : -1;
	}

	private static void set(List<Integer> layout, int pos, int id)
	{
		while (layout.size() <= pos)
		{
			layout.add(-1);
		}
		layout.set(pos, id);
	}

	private static Set<Integer> ids(int[] layout)
	{
		Set<Integer> ids = new HashSet<>();
		for (int id : layout)
		{
			if (id != -1)
			{
				ids.add(id);
			}
		}
		return ids;
	}

	private static Set<Integer> ids(List<Integer> layout)
	{
		Set<Integer> ids = new HashSet<>(layout);
		ids.remove(-1);
		return ids;
	}

	private static int[] toArray(List<Integer> layout)
	{
		return layout.stream().mapToInt(Integer::intValue).toArray();
	}

	/** Without trailing empty cells, so layouts that differ only in padding compare equal. */
	static int[] trim(int[] layout)
	{
		int end = layout.length;
		while (end > 0 && layout[end - 1] == -1)
		{
			end--;
		}
		return end == layout.length ? layout : Arrays.copyOf(layout, end);
	}
}
