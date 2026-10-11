package com.ultimatebanktabs;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntUnaryOperator;

/**
 * Keeps the player's own arrangement of a tab when its contents change.
 *
 * <p>A tab the player never rearranged simply takes the newly planned layout.
 * Once they have moved things, their layout is the starting point instead:
 * <ul>
 * <li>another charge, dose or degrade state of an item is the same item: it
 * takes the old one's cell</li>
 * <li>a new best-in-slot item takes its cell in the equipment block back,
 * whatever the player put there; whatever it pushed out moves to a free cell</li>
 * <li>an item the plugin no longer shows leaves a gap, and its cell is
 * remembered: when it comes back it goes there again if the cell is free</li>
 * <li>any other new item goes beside its neighbour in the plan, wherever the
 * player put that neighbour, else the next free cell (never into the
 * equipment block)</li>
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

	static int[] next(int[] saved, int[] previous, int[] planned, Set<Integer> bis)
	{
		return next(saved, previous, planned, bis, IntUnaryOperator.identity(), new HashMap<>());
	}

	/**
	 * The layout to save for a tab.
	 *
	 * @param saved    the tab's layout now (with the player's moves), or null if it has none
	 * @param previous the layout the plugin last planned for the tab, or null if unknown
	 * @param planned  the layout the plugin plans now
	 * @param bis      bank positions of the best-in-slot cells in {@code planned}
	 * @param family   item id -> its variant group (charges, doses, degrade states share one)
	 * @param home     variant group -> the cell an item had when it left the tab; updated in place
	 */
	static int[] next(int[] saved, int[] previous, int[] planned, Set<Integer> bis, IntUnaryOperator family,
		Map<Integer, Integer> home)
	{
		if (saved == null || previous == null
			|| home.isEmpty() && Arrays.equals(trim(saved), trim(previous)))
		{
			// never rearranged: the plan as it is
			return planned;
		}
		return merge(saved, previous, planned, bis, family, home);
	}

	private static int[] merge(int[] saved, int[] previous, int[] planned, Set<Integer> bis, IntUnaryOperator family,
		Map<Integer, Integer> home)
	{
		List<Integer> out = new ArrayList<>();
		for (int id : saved)
		{
			out.add(id);
		}
		Set<Integer> now = ids(planned);

		// another charge, dose or degrade state of an item takes the old one's cell
		Set<Integer> mine = ids(out);
		Set<Integer> was = ids(previous);
		Map<Integer, Deque<Integer>> arrived = new HashMap<>();
		for (int id : planned)
		{
			if (id != -1 && !was.contains(id) && !mine.contains(id))
			{
				Deque<Integer> q = arrived.computeIfAbsent(family.applyAsInt(id), f -> new ArrayDeque<>());
				if (!q.contains(id))
				{
					q.add(id);
				}
			}
		}
		Map<Integer, Integer> swap = new HashMap<>();
		for (int id : previous)
		{
			Deque<Integer> q = id == -1 || now.contains(id) || swap.containsKey(id) ? null
				: arrived.get(family.applyAsInt(id));
			if (q != null && !q.isEmpty())
			{
				swap.put(id, q.poll());
			}
		}
		previous = previous.clone();
		for (int i = 0; i < previous.length; i++)
		{
			previous[i] = swap.getOrDefault(previous[i], previous[i]);
		}
		for (int i = 0; i < out.size(); i++)
		{
			out.set(i, swap.getOrDefault(out.get(i), out.get(i)));
		}
		Set<Integer> before = ids(previous);

		// what the plugin stopped showing leaves a gap (remembered); what the player tagged in stays
		for (int i = 0; i < out.size(); i++)
		{
			int id = out.get(i);
			if (id != -1 && before.contains(id) && !now.contains(id))
			{
				home.put(family.applyAsInt(id), i);
				out.set(i, -1);
			}
		}

		// a new best in slot takes its cell back
		List<Integer> displaced = new ArrayList<>();
		for (int pos : new TreeSet<>(bis))
		{
			int best = at(planned, pos);
			int old = at(previous, pos);
			if (best == -1 || best == old || old != -1 && family.applyAsInt(best) == family.applyAsInt(old)
				|| at(out, pos) == best)
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
			home.remove(family.applyAsInt(best));
			if (there != -1)
			{
				displaced.add(there);
			}
		}

		// new items (and new copies of an item the plan now shows twice), and anything a new best pushed out
		boolean block = !bis.isEmpty();
		// (an item the plan shows that the layout lacks would otherwise be dropped in the first empty cell by Bank Tags)
		Map<Integer, Integer> toPlace = new HashMap<>();
		Map<Integer, Integer> have = counts(out);
		counts(planned).forEach((id, want) ->
		{
			int extra = want - have.getOrDefault(id, 0);
			if (extra > 0)
			{
				toPlace.put(id, extra);
			}
		});
		for (int i = 0; i < planned.length; i++)
		{
			int id = planned[i];
			Integer left = id == -1 ? null : toPlace.get(id);
			if (left == null || left <= 0)
			{
				continue;
			}
			int target = translate(out, planned, i, block);
			if (at(out, target) == id)
			{
				// that copy is already where the plan wants it
				continue;
			}
			Integer back = home.remove(family.applyAsInt(id));
			int pos = back != null && at(out, back) == -1 && !reserved(back, block, bis) ? back
				: free(out, target, block, bis);
			set(out, pos, id);
			toPlace.put(id, left - 1);
		}
		Set<Integer> placed = ids(out);
		for (int id : displaced)
		{
			if (!placed.contains(id))
			{
				// tagged in by the player (planned ones were placed above): below the equipment block
				set(out, free(out, block ? BLOCK_ROWS * WIDTH : 0, block, bis), id);
				placed.add(id);
			}
		}
		return trim(toArray(out));
	}

	/**
	 * Where a new item goes: its planned cell shifted by however far the player moved its nearest
	 * neighbour in the plan, else beside that neighbour, else in a new row under it.
	 */
	private static int translate(List<Integer> out, int[] planned, int i, boolean block)
	{
		if (block && i / WIDTH < BLOCK_ROWS)
		{
			// the equipment block and its alternatives never move
			return i;
		}
		int floor = block ? BLOCK_ROWS * WIDTH : 0;
		for (int j = i - 1; j >= floor; j--)
		{
			int at = planned[j] == -1 ? -1 : out.indexOf(planned[j]);
			if (at < floor)
			{
				continue;
			}
			int t = i + (at / WIDTH - j / WIDTH) * WIDTH;
			if (t >= floor && at(out, t) == -1)
			{
				return t;
			}
			for (int c = at + 1; c % WIDTH != 0; c++)
			{
				if (at(out, c) == -1)
				{
					return c;
				}
			}
			// the row is full: open one under it, moving everything below down together
			int below = (at / WIDTH + 1) * WIDTH;
			if (below < out.size())
			{
				out.addAll(below, Collections.nCopies(WIDTH, -1));
			}
			return below + i % WIDTH;
		}
		return i;
	}

	private static boolean reserved(int pos, boolean block, Set<Integer> bis)
	{
		return bis.contains(pos) || block && pos / WIDTH < BLOCK_ROWS && pos % WIDTH < BLOCK_COLUMNS;
	}

	/** The cell if it is free, else the next free cell outside the equipment block. */
	private static int free(List<Integer> out, int start, boolean block, Set<Integer> bis)
	{
		if (at(out, start) == -1)
		{
			return start;
		}
		for (int pos = start + 1; ; pos++)
		{
			if (!reserved(pos, block, bis) && at(out, pos) == -1)
			{
				return pos;
			}
		}
	}

	private static int at(int[] layout, int pos)
	{
		return pos >= 0 && pos < layout.length ? layout[pos] : -1;
	}

	private static int at(List<Integer> layout, int pos)
	{
		return pos >= 0 && pos < layout.size() ? layout.get(pos) : -1;
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

	private static Map<Integer, Integer> counts(int[] layout)
	{
		Map<Integer, Integer> counts = new HashMap<>();
		for (int id : layout)
		{
			if (id != -1)
			{
				counts.merge(id, 1, Integer::sum);
			}
		}
		return counts;
	}

	private static Map<Integer, Integer> counts(List<Integer> layout)
	{
		Map<Integer, Integer> counts = new HashMap<>();
		for (int id : layout)
		{
			if (id != -1)
			{
				counts.merge(id, 1, Integer::sum);
			}
		}
		return counts;
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
