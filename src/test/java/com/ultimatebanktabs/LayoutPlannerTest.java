package com.ultimatebanktabs;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class LayoutPlannerTest
{
	private static final int W = LayoutPlanner.WIDTH;

	private static TabBuilder.Placement p(int id, String slot, String group)
	{
		return new TabBuilder.Placement(id, slot, group, null);
	}

	private static TabBuilder.Section s(String key, String layout, TabBuilder.Placement... items)
	{
		return new TabBuilder.Section(key, key, layout, Arrays.asList(items));
	}

	private static int[] plan(TabBuilder.Section... sections)
	{
		return LayoutPlanner.plan(new TabBuilder.Built(null, Arrays.asList(sections), -1), false);
	}

	private static int at(int[] layout, int row, int col)
	{
		int pos = row * W + col;
		return pos < layout.length ? layout[pos] : -1;
	}

	@Test
	public void equipmentDiagram()
	{
		int[] layout = plan(s("main", "equipment",
			p(1, "HEAD", "head"), p(2, "CAPE", "cape"), p(3, "AMULET", "neck"), p(4, "AMMO", "ammo"),
			p(5, "WEAPON", "weapon"), p(6, "BODY", "body"), p(7, "SHIELD", "shield"), p(8, "LEGS", "legs"),
			p(9, "GLOVES", "hands"), p(10, "BOOTS", "feet"), p(11, "RING", "ring")));
		assertEquals(1, at(layout, 0, 1));
		assertEquals(2, at(layout, 1, 0));
		assertEquals(3, at(layout, 1, 1));
		assertEquals(4, at(layout, 1, 2));
		assertEquals(5, at(layout, 2, 0));
		assertEquals(6, at(layout, 2, 1));
		assertEquals(7, at(layout, 2, 2));
		assertEquals(8, at(layout, 3, 1));
		assertEquals(9, at(layout, 4, 0));
		assertEquals(10, at(layout, 4, 1));
		assertEquals(11, at(layout, 4, 2));
		assertEquals(-1, at(layout, 0, 0));
		assertEquals(-1, at(layout, 2, 3));
	}

	@Test
	public void setsLineUpInOneAlternativesColumn()
	{
		int[] layout = plan(
			s("main", "equipment", p(1, "HEAD", "head"), p(5, "WEAPON", "weapon")),
			s("situational", "alts", p(20, "HEAD", "slayer_helm"), p(21, "AMULET", "salve"),
				p(30, "HEAD", "inquisitor"), p(31, "BODY", "inquisitor"), p(32, "LEGS", "inquisitor")));
		assertEquals(20, at(layout, 0, 4));
		assertEquals(21, at(layout, 1, 4));
		// row 0 col 4 is taken by the slayer helm, so the whole set moves to col 5
		assertEquals(30, at(layout, 0, 5));
		assertEquals(31, at(layout, 2, 5));
		assertEquals(32, at(layout, 3, 5));
	}

	@Test
	public void rowSectionsStartBelowTheBlockOnFreshRows()
	{
		TabBuilder.Placement[] weapons = new TabBuilder.Placement[10];
		for (int i = 0; i < weapons.length; i++)
		{
			weapons[i] = p(100 + i, null, "w");
		}
		int[] layout = plan(
			s("main", "equipment", p(1, "HEAD", "head")),
			s("weapons", "rows", weapons),
			s("supplies", "rows", p(200, null, "boost")));
		assertEquals(100, at(layout, 5, 0));
		assertEquals(107, at(layout, 5, 7));
		assertEquals(108, at(layout, 6, 0));
		assertEquals(109, at(layout, 6, 1));
		assertEquals(-1, at(layout, 6, 2));
		assertEquals(200, at(layout, 7, 0));
	}

	@Test
	public void rowsOnlyTabStartsAtTop()
	{
		int[] layout = plan(s("gear", "rows", p(1, null, "a"), p(2, null, "b")), s("logs", "rows", p(3, null, "c")));
		assertArrayEquals(new int[]{1, 2, -1, -1, -1, -1, -1, -1, 3}, layout);
	}

	@Test
	public void gapRows()
	{
		List<TabBuilder.Section> sections = Arrays.asList(
			s("gear", "rows", p(1, null, "a")), s("logs", "rows", p(3, null, "c")));
		int[] layout = LayoutPlanner.plan(new TabBuilder.Built(null, sections, -1), true);
		assertEquals(3, at(layout, 2, 0));
	}

	@Test
	public void overflowingAlternativesGetTheirOwnRow()
	{
		TabBuilder.Section alts = s("situational", "alts",
			p(20, "HEAD", "a"), p(21, "HEAD", "b"), p(22, "HEAD", "c"), p(23, "HEAD", "d"), p(24, "HEAD", "e"));
		int[] layout = plan(s("main", "equipment", p(1, "HEAD", "head")), alts);
		assertEquals(23, at(layout, 0, 7));
		assertEquals(24, at(layout, 5, 0));
	}

	@Test
	public void emptyTab()
	{
		assertEquals(0, plan(new TabBuilder.Section("x", "x", "rows", Collections.emptyList())).length);
	}
}
