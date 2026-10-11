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
 * <li>any other new item goes into its own section's rows, wherever the player
 * put them (its planned column first, so herb columns stay aligned), else a
 * row opened under that section; a new section gets rows of its own in plan
 * order. The equipment block is never used for this.</li>
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
		return next(saved, previous, planned, null, bis, !bis.isEmpty(), null, IntUnaryOperator.identity(),
			new HashMap<>());
	}

	static int[] next(int[] saved, int[] previous, int[] planned, Set<Integer> bis, IntUnaryOperator family,
		Map<Integer, Integer> home)
	{
		return next(saved, previous, planned, null, bis, !bis.isEmpty(), null, family, home);
	}

	static int[] next(int[] saved, int[] previous, int[] planned, String[] rows, Set<Integer> bis, boolean block,
		IntUnaryOperator family, Map<Integer, Integer> home)
	{
		return next(saved, previous, planned, rows, bis, block, null, family, home);
	}

	/**
	 * The layout to save for a tab.
	 *
	 * @param saved    the tab's layout now (with the player's moves), or null if it has none
	 * @param previous the layout the plugin last planned for the tab, or null if unknown
	 * @param planned  the layout the plugin plans now
	 * @param rows     the section each planned row belongs to (null for an empty row); null to treat each
	 *                 run of rows without an empty one between as one section
	 * @param bis      bank positions of the best-in-slot cells in {@code planned}
	 * @param block    whether the tab has the equipment block (rows 0-4), even with only alternatives in it
	 * @param hadBlock whether the previous plan had it, or null if unknown (then read from the previous layout)
	 * @param family   item id -> its variant group (charges, doses, degrade states share one)
	 * @param home     variant group -> the cell an item had when it left the tab; updated in place
	 */
	static int[] next(int[] saved, int[] previous, int[] planned, String[] rows, Set<Integer> bis, boolean block,
		Boolean hadBlock, IntUnaryOperator family, Map<Integer, Integer> home)
	{
		if (saved == null || previous == null
			|| home.isEmpty() && Arrays.equals(trim(saved), trim(previous)))
		{
			// never rearranged: the plan as it is
			return planned;
		}
		return new LayoutMerger.Merge(saved, previous, planned, rows == null ? runs(planned) : rows, bis, block,
			hadBlock != null ? hadBlock : blockShaped(previous), family, home).run();
	}

	/** One merge: the player's layout {@code out} is edited in place. */
	private static final class Merge
	{
		final List<Integer> out = new ArrayList<>();
		int[] previous;
		final int[] planned;
		final String[] rows;
		final Set<Integer> bis;
		final boolean block;
		final boolean hadBlock;
		final int floor;
		final IntUnaryOperator family;
		final Map<Integer, Integer> home;
		/** section -> the rows of {@code out} that hold its items */
		final Map<String, TreeSet<Integer>> sectionRows = new HashMap<>();
		/** planned row -> the rows of {@code out} that hold its items (an outfit's row in a sets section) */
		final Map<Integer, TreeSet<Integer>> plannedRows = new HashMap<>();
		/** herb band and planned column ("#0:3") -> the column of {@code out} that herb has */
		final Map<String, Integer> herbColumns = new HashMap<>();

		Merge(int[] saved, int[] previous, int[] planned, String[] rows, Set<Integer> bis, boolean block,
			boolean hadBlock, IntUnaryOperator family, Map<Integer, Integer> home)
		{
			for (int id : saved)
			{
				out.add(id);
			}
			this.previous = previous;
			this.planned = planned;
			this.rows = rows;
			this.bis = bis;
			this.block = block;
			this.hadBlock = hadBlock;
			this.floor = block ? BLOCK_ROWS * WIDTH : 0;
			this.family = family;
			this.home = home;
		}

		int[] run()
		{
			Set<Integer> now = ids(planned);

			// the tab just got its equipment block: the player's arrangement moves down under it as a whole
			if (block && !hadBlock && out.subList(0, Math.min(floor, out.size())).stream().anyMatch(id -> id != -1))
			{
				insertRows(0, BLOCK_ROWS);
			}

			renameVariants(now);
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

			// the tab lost its equipment block: the arrangement under it moves up as a whole
			if (!block && hadBlock)
			{
				dropEmptyTop();
			}

			List<Integer> displaced = newBestInSlot();
			placeNew();

			Set<Integer> placed = ids(out);
			for (int id : displaced)
			{
				if (!placed.contains(id))
				{
					// tagged in by the player (planned ones were placed above): below the equipment block
					set(free(floor), id);
					placed.add(id);
				}
			}
			return trim(toArray(out));
		}

		/** Another charge, dose or degrade state of an item takes the old one's cell. */
		void renameVariants(Set<Integer> now)
		{
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
		}

		/** A new best in slot takes its cell back; returns what it pushed out. */
		List<Integer> newBestInSlot()
		{
			List<Integer> displaced = new ArrayList<>();
			for (int pos : new TreeSet<>(bis))
			{
				int best = at(planned, pos);
				int old = at(previous, pos);
				// (renameVariants already made a charge or degrade step of the same item equal)
				if (best == -1 || best == old || at(out, pos) == best)
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
				set(pos, best);
				home.remove(family.applyAsInt(best));
				if (there != -1)
				{
					displaced.add(there);
				}
			}
			return displaced;
		}

		/**
		 * Every planned cell whose item the layout lacks (new items, new copies of an item the plan shows
		 * twice, items a new best pushed out): returning items go home first, then the rest by section.
		 */
		void placeNew()
		{
			boolean[] covered = cover();
			indexSections(covered);
			for (int i = 0; i < planned.length; i++)
			{
				int id = planned[i];
				Integer back = id == -1 || covered[i] ? null : home.get(family.applyAsInt(id));
				if (back != null && at(out, back) == -1 && !reserved(back))
				{
					home.remove(family.applyAsInt(id));
					set(back, id);
					covered[i] = true;
					note(i, back);
				}
			}
			for (int i = 0; i < planned.length; i++)
			{
				int id = planned[i];
				if (id == -1 || covered[i])
				{
					continue;
				}
				home.remove(family.applyAsInt(id));
				int pos = place(i);
				set(pos, id);
				note(i, pos);
			}
		}

		/**
		 * Which planned cells the layout already has an item for: each copy in the layout stands for the
		 * planned occurrence of its item nearest to it (an item shown once simply has its one occurrence).
		 */
		boolean[] cover()
		{
			boolean[] covered = new boolean[planned.length];
			Map<Integer, List<Integer>> occurrences = new HashMap<>();
			for (int i = 0; i < planned.length; i++)
			{
				if (planned[i] != -1)
				{
					occurrences.computeIfAbsent(planned[i], k -> new ArrayList<>()).add(i);
				}
			}
			for (int pos = 0; pos < out.size(); pos++)
			{
				List<Integer> occ = occurrences.get(out.get(pos));
				if (occ == null)
				{
					continue;
				}
				int bestOcc = -1;
				for (int o : occ)
				{
					if (!covered[o] && (bestOcc == -1 || distance(o, pos) < distance(bestOcc, pos)))
					{
						bestOcc = o;
					}
				}
				if (bestOcc != -1)
				{
					covered[bestOcc] = true;
					copyOf.put(pos, bestOcc);
				}
			}
			return covered;
		}

		/** layout cell -> the planned occurrence it stands for */
		final Map<Integer, Integer> copyOf = new HashMap<>();

		/** Which rows of the layout each section's items are in (below the equipment block). */
		void indexSections(boolean[] covered)
		{
			copyOf.forEach((pos, occ) -> note(occ, pos));
		}

		void note(int occurrence, int pos)
		{
			String section = section(occurrence);
			if (section != null && pos >= floor && occurrence >= floor)
			{
				sectionRows.computeIfAbsent(section, s -> new TreeSet<>()).add(pos / WIDTH);
				plannedRows.computeIfAbsent(occurrence / WIDTH, r -> new TreeSet<>()).add(pos / WIDTH);
				String herb = herb(occurrence);
				if (herb != null)
				{
					herbColumns.putIfAbsent(herb, pos % WIDTH);
				}
			}
		}

		/** A herb's column key in a band ("#0:3"), or null outside the herb bands. */
		String herb(int occurrence)
		{
			String section = section(occurrence);
			int hash = section == null ? -1 : section.indexOf('#');
			return hash < 0 ? null : section.substring(hash) + ":" + occurrence % WIDTH;
		}

		String section(int occurrence)
		{
			int row = occurrence / WIDTH;
			return row < rows.length ? rows[row] : null;
		}

		/** Where the planned item at {@code i} goes. */
		int place(int i)
		{
			if (i < floor)
			{
				// the equipment block and its alternatives keep their planned cells
				return free(i);
			}
			// a herb keeps one column down its band; anything else tries its planned column first
			String herb = herb(i);
			int col = herb != null && herbColumns.containsKey(herb) ? herbColumns.get(herb) : i % WIDTH;
			String section = section(i);
			TreeSet<Integer> mine = section == null ? null : sectionRows.get(section);
			if (mine != null && !mine.isEmpty())
			{
				// where the rest of its planned row went (an outfit's own row), then its section's other rows
				for (TreeSet<Integer> candidates : Arrays.asList(plannedRows.get(i / WIDTH), mine))
				{
					if (candidates == null)
					{
						continue;
					}
					for (int r : candidates)
					{
						if (at(out, r * WIDTH + col) == -1)
						{
							return r * WIDTH + col;
						}
					}
					for (int r : candidates)
					{
						for (int c = 0; c < WIDTH; c++)
						{
							if (at(out, r * WIDTH + c) == -1)
							{
								return r * WIDTH + c;
							}
						}
					}
				}
				// full: a row under the section, moving everything below down together
				int row = mine.last() + 1;
				insertRows(row, 1);
				return row * WIDTH + col;
			}
			// a section the layout doesn't have yet: rows of its own after the section before it in the plan
			int first = i / WIDTH;
			for (int r = first - 1; r >= floor / WIDTH; r--)
			{
				TreeSet<Integer> prev = rows[r] == null ? null : sectionRows.get(rows[r]);
				if (prev != null && !prev.isEmpty())
				{
					// keep the plan's blank row between them (gap rows, finished goods)
					int gap = rows[first - 1] == null ? 1 : 0;
					int row = prev.last() + 1;
					if (gap == 1 && emptyRow(row) && !blankBelow(first))
					{
						// the blank row already there (above the section after this one) is that gap
						row++;
						gap = 0;
					}
					insertRows(row, 1 + gap);
					return (row + gap) * WIDTH + col;
				}
			}
			// or before the section after it
			for (int r = first + 1; r < rows.length; r++)
			{
				TreeSet<Integer> next = rows[r] == null || rows[r].equals(section) ? null : sectionRows.get(rows[r]);
				if (next != null && !next.isEmpty())
				{
					// the plan's blank rows above it and between it and the next section
					int above = first - 1 >= floor / WIDTH && rows[first - 1] == null ? 1 : 0;
					int below = rows[r - 1] == null ? 1 : 0;
					int row = next.first();
					if (row - 1 >= floor / WIDTH && emptyRow(row - 1))
					{
						// the blank row already above the next section is one of them
						if (above == 0 && below == 1)
						{
							row--;
							below = 0;
						}
						else
						{
							above = 0;
						}
					}
					insertRows(row, above + 1 + below);
					return (row + above) * WIDTH + col;
				}
			}
			return free(i);
		}

		boolean emptyRow(int row)
		{
			for (int c = 0; c < WIDTH; c++)
			{
				if (at(out, row * WIDTH + c) != -1)
				{
					return false;
				}
			}
			return true;
		}

		/** Whether the plan has a blank row right under the section whose first planned row is {@code first}. */
		boolean blankBelow(int first)
		{
			int last = first;
			while (last + 1 < rows.length && rows[last + 1] != null && rows[last + 1].equals(rows[first]))
			{
				last++;
			}
			return last + 1 < rows.length && rows[last + 1] == null;
		}

		/** Leading empty rows go once the whole old block area is empty (the tab lost its block). */
		void dropEmptyTop()
		{
			int empty = 0;
			while ((empty + 1) * WIDTH <= out.size()
				&& out.subList(empty * WIDTH, (empty + 1) * WIDTH).stream().allMatch(id -> id == -1))
			{
				empty++;
			}
			if (empty < BLOCK_ROWS)
			{
				return;
			}
			int shift = empty * WIDTH;
			out.subList(0, shift).clear();
			home.values().removeIf(cell -> cell < shift);
			home.replaceAll((f, cell) -> cell - shift);
		}

		/** Insert empty rows at {@code row}; everything from there moves down, remembered cells too. */
		void insertRows(int row, int count)
		{
			int at = row * WIDTH;
			int shift = count * WIDTH;
			if (at < out.size())
			{
				out.addAll(at, Collections.nCopies(shift, -1));
			}
			home.replaceAll((f, cell) -> cell >= at ? cell + shift : cell);
			List<TreeSet<Integer>> all = new ArrayList<>(sectionRows.values());
			all.addAll(plannedRows.values());
			for (TreeSet<Integer> set : all)
			{
				TreeSet<Integer> moved = new TreeSet<>();
				for (int r : set)
				{
					moved.add(r >= row ? r + count : r);
				}
				set.clear();
				set.addAll(moved);
			}
		}

		boolean reserved(int pos)
		{
			return bis.contains(pos) || block && pos / WIDTH < BLOCK_ROWS && pos % WIDTH < BLOCK_COLUMNS;
		}

		/** The cell if it is free, else the next free cell outside the equipment block. */
		int free(int start)
		{
			if (at(out, start) == -1)
			{
				return start;
			}
			for (int pos = start + 1; ; pos++)
			{
				if (!reserved(pos) && at(out, pos) == -1)
				{
					return pos;
				}
			}
		}

		void set(int pos, int id)
		{
			while (out.size() <= pos)
			{
				out.add(-1);
			}
			out.set(pos, id);
		}
	}

	/**
	 * Whether a layout looks like it has the equipment block: rows 0-4 only fill equipment-screen cells and
	 * the alternatives columns (a plan without the block starts its first section at row 0, column 0).
	 */
	private static boolean blockShaped(int[] layout)
	{
		boolean any = false;
		for (int i = 0; i < Math.min(BLOCK_ROWS * WIDTH, layout.length); i++)
		{
			int r = i / WIDTH;
			int c = i % WIDTH;
			boolean blockCell = c >= BLOCK_COLUMNS || c != 3 && (c == 1 || r != 0 && r != 3);
			if (layout[i] != -1)
			{
				if (!blockCell)
				{
					return false;
				}
				any = true;
			}
		}
		return any;
	}

	/** Without section names, each run of rows with no empty row between counts as one section. */
	private static String[] runs(int[] planned)
	{
		int count = (planned.length + WIDTH - 1) / WIDTH;
		String[] rows = new String[count];
		int run = 0;
		for (int r = 0; r < count; r++)
		{
			boolean empty = true;
			for (int c = 0; c < WIDTH && r * WIDTH + c < planned.length; c++)
			{
				empty &= planned[r * WIDTH + c] == -1;
			}
			if (empty)
			{
				run++;
			}
			else
			{
				rows[r] = "run" + run;
			}
		}
		return rows;
	}

	private static int distance(int a, int b)
	{
		return Math.abs(a / WIDTH - b / WIDTH) * WIDTH + Math.abs(a % WIDTH - b % WIDTH);
	}

	private static int at(int[] layout, int pos)
	{
		return pos >= 0 && pos < layout.length ? layout[pos] : -1;
	}

	private static int at(List<Integer> layout, int pos)
	{
		return pos >= 0 && pos < layout.size() ? layout.get(pos) : -1;
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
