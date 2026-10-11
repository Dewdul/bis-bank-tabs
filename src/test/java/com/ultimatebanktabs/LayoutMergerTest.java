package com.ultimatebanktabs;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
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
