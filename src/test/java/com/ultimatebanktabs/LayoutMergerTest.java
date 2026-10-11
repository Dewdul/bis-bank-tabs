package com.ultimatebanktabs;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class LayoutMergerTest
{
	private static final int W = LayoutPlanner.WIDTH;
	private static final int E = -1;
	/** HEAD and BODY cells of the equipment block */
	private static final int HEAD = 1;
	private static final int BODY = 2 * W + 1;
	private static final Set<Integer> BIS = new HashSet<>(Arrays.asList(HEAD, BODY));

	/** A layout with the given item at each position, the rest empty. */
	private static int[] layout(int size, int... posIdPairs)
	{
		int[] out = new int[size];
		Arrays.fill(out, E);
		for (int i = 0; i < posIdPairs.length; i += 2)
		{
			out[posIdPairs[i]] = posIdPairs[i + 1];
		}
		return out;
	}

	private static int pos(int[] layout, int id)
	{
		for (int i = 0; i < layout.length; i++)
		{
			if (layout[i] == id)
			{
				return i;
			}
		}
		return -1;
	}

	private static final int ROW5 = 5 * W;

	@Test
	public void untouchedTabsTakeThePlan()
	{
		int[] previous = layout(ROW5 + 2, HEAD, 10, BODY, 20, ROW5, 30);
		int[] planned = layout(ROW5 + 2, HEAD, 10, BODY, 20, ROW5, 30, ROW5 + 1, 31);
		assertSame(planned, LayoutMerger.next(previous.clone(), previous, planned, BIS));
		assertSame("no saved layout yet", planned, LayoutMerger.next(null, previous, planned, BIS));
		assertSame("nothing known about the last plan", planned, LayoutMerger.next(previous, null, planned, BIS));
		assertSame("padding doesn't count as a change", planned,
			LayoutMerger.next(LayoutMerger.trim(previous), previous, planned, BIS));
	}

	@Test
	public void theirMovesStayWhenSomethingNewArrives()
	{
		int[] previous = layout(ROW5 + 2, HEAD, 10, BODY, 20, ROW5, 30, ROW5 + 1, 31);
		// the player swapped the two potions and moved the helmet to the end of the row
		int[] saved = layout(ROW5 + 3, HEAD, E, BODY, 20, ROW5, 31, ROW5 + 1, 30, ROW5 + 2, 10);
		int[] planned = layout(ROW5 + 3, HEAD, 10, BODY, 20, ROW5, 30, ROW5 + 1, 31, ROW5 + 2, 32);
		int[] next = LayoutMerger.next(saved, previous, planned, BIS);
		assertEquals("their swap stays", 31, next[ROW5]);
		assertEquals(30, next[ROW5 + 1]);
		assertEquals("the same best helmet stays where they put it", ROW5 + 2, pos(next, 10));
		assertEquals("their empty helmet cell stays empty", E, next[HEAD]);
		assertEquals("the new potion takes the next free cell, not their gap in the gear", ROW5 + 3, pos(next, 32));
	}

	@Test
	public void aNewBestInSlotTakesItsCellBack()
	{
		int[] previous = layout(ROW5 + 2, HEAD, 10, BODY, 20, ROW5, 30);
		// the player put a potion in the helmet cell and the helmet at the end
		int[] saved = layout(ROW5 + 2, HEAD, 30, BODY, 20, ROW5, E, ROW5 + 1, 10);
		int[] planned = layout(ROW5 + 2, HEAD, 11, BODY, 20, ROW5, 30);
		int[] next = LayoutMerger.next(saved, previous, planned, BIS);
		assertEquals("the new best helmet goes in the helmet cell", 11, next[HEAD]);
		assertEquals("the potion it pushed out goes back to its planned cell", ROW5, pos(next, 30));
		assertEquals("the old helmet left the tab and leaves a gap", -1, pos(next, 10));
		assertEquals("the body they didn't touch stays", 20, next[BODY]);
	}

	@Test
	public void theNewBestMovesFromWhereverItWas()
	{
		int[] previous = layout(ROW5 + 2, HEAD, 10, BODY, 20, ROW5, 11);
		// a backup helmet (11) the player had moved becomes the best
		int[] saved = layout(ROW5 + 4, HEAD, 10, BODY, 20, ROW5 + 3, 11);
		int[] planned = layout(ROW5 + 2, HEAD, 11, BODY, 20, ROW5, 10);
		int[] next = LayoutMerger.next(saved, previous, planned, BIS);
		assertEquals(11, next[HEAD]);
		assertEquals("only once", 1, Arrays.stream(next).filter(id -> id == 11).count());
		assertEquals("the old best, now a backup, goes to its planned cell", ROW5, pos(next, 10));
	}

	@Test
	public void itemsTheyTaggedInThemselvesStay()
	{
		int[] previous = layout(ROW5 + 1, HEAD, 10, ROW5, 30);
		int[] saved = layout(ROW5 + 4, HEAD, 10, ROW5, 30, ROW5 + 3, 99);
		int[] planned = layout(ROW5 + 1, HEAD, 10);
		int[] next = LayoutMerger.next(saved, previous, planned, BIS);
		assertEquals("their own item stays", 99, next[ROW5 + 3]);
		assertEquals("the potion the plugin dropped leaves a gap", E, next[ROW5]);
	}

	@Test
	public void aNewBestPushingOutTheirOwnItemMovesItBelowTheGear()
	{
		int[] previous = layout(ROW5 + 1, HEAD, 10, ROW5, 30);
		int[] saved = layout(ROW5 + 1, HEAD, 99, ROW5, 30);
		int[] planned = layout(ROW5 + 1, HEAD, 11, ROW5, 30);
		int[] next = LayoutMerger.next(saved, previous, planned, BIS);
		assertEquals(11, next[HEAD]);
		assertEquals("their item moves to the first free cell below the gear", ROW5 + 1, pos(next, 99));
	}

	/** 3853/3855 are two charge states of one item, 4716/4880 two degrade states of another */
	private static int family(int id)
	{
		return id == 3855 ? 3853 : id == 4880 ? 4716 : id;
	}

	private static int[] next(int[] saved, int[] previous, int[] planned, Set<Integer> bis, Map<Integer, Integer> home)
	{
		return LayoutMerger.next(saved, previous, planned, bis, LayoutMergerTest::family, home);
	}

	/** Section names for the rows of a plan: {@code keys} for rows {@code from}, {@code from + 1}... */
	private static String[] rows(int count, int from, String... keys)
	{
		String[] rows = new String[count];
		System.arraycopy(keys, 0, rows, from, keys.length);
		return rows;
	}

	private static int[] next(int[] saved, int[] previous, int[] planned, String[] rows, Set<Integer> bis)
	{
		return LayoutMerger.next(saved, previous, planned, rows, bis, !bis.isEmpty(), LayoutMergerTest::family,
			new HashMap<>());
	}

	@Test
	public void anotherChargeKeepsTheirCell()
	{
		int[] previous = layout(ROW5 + 2, HEAD, 10, ROW5, 3853, ROW5 + 1, 31);
		int[] saved = layout(ROW5 + 8, HEAD, 10, ROW5 + 1, 31, ROW5 + 7, 3853);
		int[] planned = layout(ROW5 + 2, HEAD, 10, ROW5, 3855, ROW5 + 1, 31);
		int[] next = next(saved, previous, planned, BIS, new HashMap<>());
		assertEquals("the used necklace stays where they put it", 3855, next[ROW5 + 7]);
		assertEquals(-1, pos(next, 3853));
		assertEquals(E, next[ROW5]);
	}

	@Test
	public void aDegradingBestInSlotIsNotANewBest()
	{
		int[] previous = layout(ROW5 + 1, HEAD, 4716, ROW5, 30);
		// they put a potion in the helmet cell and the helmet further down
		int[] saved = layout(ROW5 + 4, HEAD, 30, ROW5 + 3, 4716);
		int[] planned = layout(ROW5 + 1, HEAD, 4880, ROW5, 30);
		int[] next = next(saved, previous, planned, BIS, new HashMap<>());
		assertEquals("their potion keeps the helmet cell", 30, next[HEAD]);
		assertEquals("the degraded helmet stays where they put it", 4880, next[ROW5 + 3]);
	}

	@Test
	public void aNewItemGoesBesideItsNeighbourNotIntoTheNextSection()
	{
		int[] previous = new int[ROW5 + W + 2];
		Arrays.fill(previous, E);
		for (int c = 0; c < W; c++)
		{
			previous[ROW5 + c] = 30 + c;
		}
		previous[ROW5 + W] = 40;
		previous[ROW5 + W + 1] = 41;
		int[] saved = previous.clone();
		saved[ROW5] = 31;
		saved[ROW5 + 1] = 30;
		// a ninth potion pushes the food a row down
		int[] planned = new int[ROW5 + 2 * W + 2];
		Arrays.fill(planned, E);
		System.arraycopy(previous, ROW5, planned, ROW5, W);
		planned[ROW5 + W] = 38;
		planned[ROW5 + 2 * W] = 40;
		planned[ROW5 + 2 * W + 1] = 41;
		int[] next = next(saved, previous, planned, rows(8, 5, "potions", "potions", "food"),
			Collections.emptySet());
		assertEquals("their swap stays", 31, next[ROW5]);
		assertEquals("the potion opens a row under the potions", 38, next[ROW5 + W]);
		assertEquals("the food moves down with it", 40, next[ROW5 + 2 * W]);
		assertEquals(41, next[ROW5 + 2 * W + 1]);
	}

	@Test
	public void anItemThatLeavesAndComesBackGoesHome()
	{
		Map<Integer, Integer> home = new HashMap<>();
		int[] plan1 = layout(ROW5 + 2, HEAD, 10, ROW5, 30, ROW5 + 1, 31);
		int[] saved = layout(ROW5 + 6, HEAD, 10, ROW5, 30, ROW5 + 5, 31);
		int[] plan2 = layout(ROW5 + 1, HEAD, 10, ROW5, 30);
		int[] gone = next(saved, plan1, plan2, BIS, home);
		assertEquals(-1, pos(gone, 31));
		int[] back = next(gone, plan2, plan1, BIS, home);
		assertEquals("it returns to the cell they gave it", ROW5 + 5, pos(back, 31));
		assertTrue(home.isEmpty());
	}

	@Test
	public void anItemThePlanShowsTwiceGetsBothCopies()
	{
		int[] previous = layout(ROW5 + 2, ROW5, 60, ROW5 + 1, 61);
		int[] saved = layout(ROW5 + 2, ROW5, 61, ROW5 + 1, 60);
		int[] planned = layout(ROW5 + 4, ROW5, 60, ROW5 + 1, 61, ROW5 + 2, 50, ROW5 + 3, 50);
		int[] next = next(saved, previous, planned, Collections.emptySet(), new HashMap<>());
		assertEquals(2, Arrays.stream(next).filter(id -> id == 50).count());
	}

	@Test
	public void aBetterCopyOfTheSameItemStillTakesTheCell()
	{
		// 4716 (undamaged) and 4880 (degraded) are one variant group; both are owned
		int[] previous = layout(ROW5 + 2, BODY, 4880, 2 * W + 4, 4716, ROW5, 30, ROW5 + 1, 31);
		int[] saved = layout(ROW5 + 2, BODY, 4880, 2 * W + 4, 4716, ROW5, 31, ROW5 + 1, 30);
		// the degraded one is sold: the undamaged one is the best now
		int[] planned = layout(ROW5 + 2, BODY, 4716, ROW5, 30, ROW5 + 1, 31);
		int[] next = next(saved, previous, planned, BIS, new HashMap<>());
		assertEquals(4716, next[BODY]);
		assertEquals("only once", 1, Arrays.stream(next).filter(id -> id == 4716).count());
		assertEquals("their swap stays", 31, next[ROW5]);
	}

	@Test
	public void aReturningItemDoesNotLeaveABlankRow()
	{
		int[] plan = new int[ROW5 + W + 2];
		Arrays.fill(plan, E);
		for (int c = 0; c < W; c++)
		{
			plan[ROW5 + c] = 30 + c;
		}
		plan[ROW5 + W] = 40;
		plan[ROW5 + W + 1] = 41;
		int[] saved = plan.clone();
		for (int c = 0; c < W; c++)
		{
			// they reversed the potion row
			saved[ROW5 + c] = 37 - c;
		}
		int[] without = plan.clone();
		without[ROW5 + 1] = E;
		Map<Integer, Integer> home = new HashMap<>();
		int[] gone = next(saved, plan, without, Collections.emptySet(), home);
		int[] back = next(gone, without, plan, Collections.emptySet(), home);
		assertEquals("31 goes back to its cell", 31, back[ROW5 + 6]);
		assertEquals("the food stays right under the potions", 40, back[ROW5 + W]);
	}

	@Test
	public void newItemsShareTheRowTheyOpen()
	{
		int[] previous = new int[ROW5 + W + 1];
		Arrays.fill(previous, E);
		for (int c = 0; c < W; c++)
		{
			previous[ROW5 + c] = 30 + c;
		}
		previous[ROW5 + W] = 40;
		int[] saved = previous.clone();
		saved[ROW5] = 31;
		saved[ROW5 + 1] = 30;
		// two new potions planned among the old ones
		int[] planned = new int[ROW5 + 2 * W + 1];
		Arrays.fill(planned, E);
		int[] potions = {30, 38, 31, 39, 32, 33, 34, 35, 36, 37};
		System.arraycopy(potions, 0, planned, ROW5, potions.length);
		planned[ROW5 + 2 * W] = 40;
		int[] next = next(saved, previous, planned, rows(8, 5, "potions", "potions", "food"),
			Collections.emptySet());
		int row38 = pos(next, 38) / W;
		assertEquals("both new potions on one new row", row38, pos(next, 39) / W);
		assertEquals("the food right under it", row38 + 1, pos(next, 40) / W);
	}

	@Test
	public void aNewSectionGetsItsOwnRow()
	{
		int[] previous = layout(ROW5 + W + 2, ROW5, 30, ROW5 + 1, 31, ROW5 + W, 40, ROW5 + W + 1, 41);
		int[] saved = layout(ROW5 + W + 2, ROW5, 31, ROW5 + 1, 30, ROW5 + W, 40, ROW5 + W + 1, 41);
		// teleports (50, 51) are a new section on their own row between the potions and the food
		int[] planned = layout(ROW5 + 2 * W + 2, ROW5, 30, ROW5 + 1, 31, ROW5 + W, 50, ROW5 + W + 1, 51,
			ROW5 + 2 * W, 40, ROW5 + 2 * W + 1, 41);
		int[] next = next(saved, previous, planned, rows(8, 5, "potions", "teleports", "food"),
			Collections.emptySet());
		int teleports = pos(next, 50) / W;
		assertTrue("not in the potion row", teleports != ROW5 / W);
		assertEquals(teleports, pos(next, 51) / W);
		assertTrue("not in the food row", teleports != pos(next, 40) / W);
	}

	@Test
	public void anAlternativesOnlyBlockIsProtected()
	{
		int alt = W + 4;
		int[] previous = layout(ROW5 + 1, alt, 70, ROW5, 30);
		int[] saved = layout(ROW5 + 1, alt, 70, ROW5, 31);
		int[] planned = layout(ROW5 + 2, alt, 70, ROW5, 30, ROW5 + 1, 31);
		int[] next = LayoutMerger.next(saved, previous, planned, null, Collections.emptySet(), true, id -> id,
			new HashMap<>());
		assertTrue("the new item stays below the block", pos(next, 30) >= ROW5);
	}

	@Test
	public void aBetterNewFoodJoinsTheFoodRow()
	{
		int[] previous = layout(ROW5 + W + 3, ROW5, 30, ROW5 + 1, 31, ROW5 + 2, 32,
			ROW5 + W, 385, ROW5 + W + 1, 7946, ROW5 + W + 2, 379);
		int[] saved = layout(ROW5 + W + 3, ROW5, 31, ROW5 + 1, 30, ROW5 + 2, 32,
			ROW5 + W, 385, ROW5 + W + 1, 7946, ROW5 + W + 2, 379);
		// manta rays (391) are the new best food: first in the food row, the lobster drops out of the top 3
		int[] planned = layout(ROW5 + W + 3, ROW5, 30, ROW5 + 1, 31, ROW5 + 2, 32,
			ROW5 + W, 391, ROW5 + W + 1, 385, ROW5 + W + 2, 7946);
		int[] next = next(saved, previous, planned, rows(7, 5, "supplies", "food"), BIS);
		assertEquals("the manta joins the food row", 6, pos(next, 391) / W);
		assertEquals("the food row stays put", ROW5 + W, pos(next, 385));
		assertEquals(31, next[ROW5]);
	}

	@Test
	public void aNewFirstHerbKeepsTheGrid()
	{
		// clean herbs over their secondaries, one herb per column; they swapped the first two herbs
		int[] previous = layout(ROW5 + W + 3, ROW5, 101, ROW5 + 1, 102, ROW5 + 2, 103,
			ROW5 + W, 201, ROW5 + W + 1, 202, ROW5 + W + 2, 203);
		int[] saved = layout(ROW5 + W + 3, ROW5, 102, ROW5 + 1, 101, ROW5 + 2, 103,
			ROW5 + W, 202, ROW5 + W + 1, 201, ROW5 + W + 2, 203);
		// a newly owned herb becomes the first column of both rows
		int[] planned = layout(ROW5 + W + 4, ROW5, 100, ROW5 + 1, 101, ROW5 + 2, 102, ROW5 + 3, 103,
			ROW5 + W, 200, ROW5 + W + 1, 201, ROW5 + W + 2, 202, ROW5 + W + 3, 203);
		int[] next = next(saved, previous, planned, rows(7, 5, "herbs_clean#0", "herbs_secondary#0"),
			Collections.emptySet());
		assertEquals("no extra rows", ROW5 + W + 4, next.length);
		assertEquals(5, pos(next, 100) / W);
		assertEquals(6, pos(next, 200) / W);
		assertEquals("herb and secondary in one column", pos(next, 100) % W, pos(next, 200) % W);
	}

	@Test
	public void returningItemsGoHomeBeforeNewOnes()
	{
		Map<Integer, Integer> home = new HashMap<>();
		int[] plan1 = layout(ROW5 + 4, ROW5, 30, ROW5 + 1, 31, ROW5 + 2, 32, ROW5 + 3, 33);
		int[] saved = layout(ROW5 + 4, ROW5, 30, ROW5 + 1, 32, ROW5 + 2, 31, ROW5 + 3, 33);
		int[] plan2 = layout(ROW5 + 3, ROW5, 30, ROW5 + 1, 31, ROW5 + 2, 33);
		int[] gone = next(saved, plan1, plan2, Collections.emptySet(), home);
		int[] plan3 = layout(ROW5 + 5, ROW5, 30, ROW5 + 1, 39, ROW5 + 2, 31, ROW5 + 3, 32, ROW5 + 4, 33);
		int[] back = next(gone, plan2, plan3, Collections.emptySet(), home);
		assertEquals("32 back before 31, as they had it", 32, back[ROW5 + 1]);
		assertEquals(31, back[ROW5 + 2]);
		assertTrue(pos(back, 39) >= ROW5);
	}

	@Test
	public void aTabThatGainsItsBlockMovesTheirArrangementUnderIt()
	{
		int[] previous = layout(W + 2, 0, 30, 1, 31, W, 40, W + 1, 41);
		int[] saved = layout(W + 2, 0, 31, 1, 30, W, 40, W + 1, 41);
		int[] planned = layout(ROW5 + W + 2, HEAD, 10, ROW5, 30, ROW5 + 1, 31, ROW5 + W, 40, ROW5 + W + 1, 41);
		int[] next = next(saved, previous, planned, rows(7, 0, "block", null, null, null, null, "supplies", "food"),
			new HashSet<>(Arrays.asList(HEAD)));
		assertEquals(10, next[HEAD]);
		assertEquals("their swap moved down under the block", 31, next[ROW5]);
		assertEquals(30, next[ROW5 + 1]);
		assertEquals(40, next[ROW5 + W]);
	}

	@Test
	public void aNewSectionDoesNotShareARowOpenedForAnotherSection()
	{
		int[] previous = new int[ROW5 + W + 1];
		Arrays.fill(previous, E);
		for (int c = 0; c < W; c++)
		{
			previous[ROW5 + c] = 30 + c;
		}
		previous[ROW5 + W] = 40;
		int[] saved = previous.clone();
		saved[ROW5] = 31;
		saved[ROW5 + 1] = 30;
		// a ninth potion and a new teleport section in one change
		int[] planned = new int[ROW5 + 3 * W + 1];
		Arrays.fill(planned, E);
		System.arraycopy(previous, ROW5, planned, ROW5, W);
		planned[ROW5 + W] = 38;
		planned[ROW5 + 2 * W] = 50;
		planned[ROW5 + 2 * W + 1] = 51;
		planned[ROW5 + 3 * W] = 40;
		int[] next = next(saved, previous, planned, rows(9, 5, "potions", "potions", "teleports", "food"),
			Collections.emptySet());
		int potionRow = pos(next, 38) / W;
		int teleportRow = pos(next, 50) / W;
		assertTrue("teleports apart from the new potion", potionRow != teleportRow);
		assertEquals(teleportRow, pos(next, 51) / W);
		assertTrue("the food stays last", pos(next, 40) / W > teleportRow);
	}

	@Test
	public void anUpgradeThatDemotesTheOnlyGearItemDoesNotShiftTheTab()
	{
		int weapon = 2 * W;
		int[] previous = layout(ROW5 + 2, weapon, 4151, ROW5, 30, ROW5 + 1, 31);
		int[] saved = layout(ROW5 + 2, weapon, 4151, ROW5, 31, ROW5 + 1, 30);
		// the scythe takes the weapon cell; the whip moves down to a new weapons row
		int[] planned = layout(ROW5 + W + 2, weapon, 22325, ROW5, 4151, ROW5 + W, 30, ROW5 + W + 1, 31);
		String[] rows = rows(7, 2, "block", null, null, "weapons", "supplies");
		int[] next = LayoutMerger.next(saved, previous, planned, rows, new HashSet<>(Arrays.asList(weapon)), true,
			null, id -> id, new HashMap<>());
		assertEquals(22325, next[weapon]);
		assertEquals("the supplies only move down for the new weapons row", 6, pos(next, 31) / W);
		assertEquals("their swap stays", 31, next[ROW5 + W]);
	}

	@Test
	public void aNewHerbKeepsOneColumnDownItsBand()
	{
		int[] previous = layout(ROW5 + W + 2, ROW5, 249, ROW5 + 1, 253, ROW5 + 2, 255, ROW5 + 3, 257,
			ROW5 + W, 91, ROW5 + W + 1, 95);
		int[] saved = layout(ROW5 + W + 2, ROW5, 253, ROW5 + 1, 249, ROW5 + 2, 255, ROW5 + 3, 257,
			ROW5 + W, 91, ROW5 + W + 1, 95);
		// marrentill (clean 251, unfinished 93) slots in as the second herb
		int[] planned = layout(ROW5 + W + 3, ROW5, 249, ROW5 + 1, 251, ROW5 + 2, 253, ROW5 + 3, 255, ROW5 + 4, 257,
			ROW5 + W, 91, ROW5 + W + 1, 93, ROW5 + W + 2, 95);
		int[] next = next(saved, previous, planned, rows(7, 5, "herbs_clean#0", "herbs_unf#0"), Collections.emptySet());
		assertEquals("clean and unfinished in one column", pos(next, 251) % W, pos(next, 93) % W);
	}

	@Test
	public void aNewOutfitPieceJoinsItsOwnOutfitsRow()
	{
		// two outfits in a sets section: A (60..65) on one row, B (70..72) on the next; they swapped two of A
		int[] previous = layout(ROW5 + W + 3, ROW5, 60, ROW5 + 1, 61, ROW5 + 2, 62, ROW5 + 3, 63, ROW5 + 4, 64,
			ROW5 + 5, 65, ROW5 + W, 70, ROW5 + W + 1, 71, ROW5 + W + 2, 72);
		int[] saved = previous.clone();
		saved[ROW5] = 61;
		saved[ROW5 + 1] = 60;
		int[] planned = layout(ROW5 + W + 4, ROW5, 60, ROW5 + 1, 61, ROW5 + 2, 62, ROW5 + 3, 63, ROW5 + 4, 64,
			ROW5 + 5, 65, ROW5 + W, 73, ROW5 + W + 1, 70, ROW5 + W + 2, 71, ROW5 + W + 3, 72);
		int[] next = next(saved, previous, planned, rows(7, 5, "graceful", "graceful"), Collections.emptySet());
		assertEquals("B's new hood goes in B's row", 6, pos(next, 73) / W);
	}

	@Test
	public void anAltAccountRoundTripKeepsTheirArrangement()
	{
		Map<Integer, Integer> home = new HashMap<>();
		Set<Integer> bis = new HashSet<>(Arrays.asList(HEAD));
		// main: gear block, tools, bait, a blank row, raw fish
		int[] main = layout(8 * W + 1, HEAD, 10, ROW5, 307, ROW5 + 1, 309, ROW5 + 2, 303, ROW5 + 3, 301,
			ROW5 + W, 313, ROW5 + W + 1, 314, 8 * W, 317);
		String[] mainRows = rows(9, 0, "block", null, null, null, null, "gear", "bait", null, "goods");
		int[] saved = main.clone();
		// they reversed the tools
		saved[ROW5] = 301;
		saved[ROW5 + 1] = 303;
		saved[ROW5 + 2] = 309;
		saved[ROW5 + 3] = 307;
		// an alt with only the raw fish: no block
		int[] alt = layout(1, 0, 317);
		int[] onAlt = LayoutMerger.next(saved, main, alt, rows(1, 0, "goods"), Collections.emptySet(), false, true,
			id -> id, home);
		int[] back = LayoutMerger.next(onAlt, alt, main, mainRows, bis, true, false, id -> id, home);
		int tools = pos(back, 301);
		assertEquals("their tool order is back", 303, back[tools + 1]);
		assertEquals(309, back[tools + 2]);
		assertEquals(307, back[tools + 3]);
		assertEquals(10, back[HEAD]);
	}

	@Test
	public void twoNewSectionsKeepTheBlankRowBeforeTheBottom()
	{
		int[] previous = layout(7 * W + 1, ROW5, 30, ROW5 + 1, 31, 7 * W, 90);
		int[] saved = layout(7 * W + 1, ROW5, 31, ROW5 + 1, 30, 7 * W, 90);
		// blighted supplies and spell sacks arrive together, above the enchanting row
		int[] planned = layout(10 * W + 1, ROW5, 30, ROW5 + 1, 31, 7 * W, 80, 8 * W, 85, 10 * W, 90);
		int[] next = next(saved, previous, planned,
			rows(11, 5, "food", null, "blighted", "blighted_sacks", null, "enchanting"), Collections.emptySet());
		int sacks = pos(next, 85) / W;
		assertEquals("the sacks right under the blighted row", pos(next, 80) / W + 1, sacks);
		assertEquals("still a blank row before the enchanting row", sacks + 2, pos(next, 90) / W);
	}

	@Test
	public void aNewHerbDoesNotTakeAnotherHerbsColumn()
	{
		// gear row (swapped), then grimy herbs (guam, ranarr) over clean herbs (guam, tarromin, harralander, ranarr)
		int[] previous = layout(7 * W + 4, ROW5, 60, ROW5 + 1, 61, 6 * W, 199, 6 * W + 3, 207,
			7 * W, 249, 7 * W + 1, 253, 7 * W + 2, 255, 7 * W + 3, 257);
		int[] saved = previous.clone();
		saved[ROW5] = 61;
		saved[ROW5 + 1] = 60;
		// marrentill (grimy 201, clean 251) becomes the second herb column
		int[] planned = layout(7 * W + 5, ROW5, 60, ROW5 + 1, 61, 6 * W, 199, 6 * W + 1, 201, 6 * W + 4, 207,
			7 * W, 249, 7 * W + 1, 251, 7 * W + 2, 253, 7 * W + 3, 255, 7 * W + 4, 257);
		int[] next = next(saved, previous, planned, rows(8, 5, "gear", "herbs_grimy#0", "herbs_clean#0"),
			Collections.emptySet());
		assertEquals("grimy and clean marrentill in one column", pos(next, 201) % W, pos(next, 251) % W);
	}

	@Test
	public void noGearBlockMeansNoReservedCells()
	{
		int[] previous = layout(2, 0, 30, 1, 31);
		int[] saved = layout(2, 0, 31, 1, 30);
		int[] planned = layout(3, 0, 30, 1, 31, 2, 32);
		assertArrayEquals(new int[]{31, 30, 32},
			LayoutMerger.next(saved, previous, planned, Collections.emptySet()));
	}
}
