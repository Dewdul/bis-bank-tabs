package com.ultimatebanktabs;

import java.util.List;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * The player's current slayer task, read straight from the game (the same way
 * the core Slayer plugin does), so it works whether or not that plugin is on.
 */
@Value
class SlayerTask
{
	/** the task's in-game name, lower case ("abyssal demons", "vorkath") */
	String name;
	/** Konar's location ("Karuulm Slayer Dungeon"), or null */
	String area;
	/** Krystilia: the task is in the Wilderness */
	boolean wilderness;

	/** the "Boss" task row: the boss is in its own varbit */
	private static final int BOSS_TASK = 98;
	private static final int KRYSTILIA = 7;

	/** Must run on the client thread (DB lookups). Null without a task. */
	static SlayerTask read(Client client)
	{
		if (client.getVarpValue(VarPlayerID.SLAYER_COUNT) <= 0)
		{
			return null;
		}
		int taskId = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
		int row;
		if (taskId == BOSS_TASK)
		{
			List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerTaskSublist.ID,
				DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID, 0, client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID));
			if (rows.isEmpty())
			{
				return null;
			}
			row = (Integer) client.getDBTableField(rows.get(0), DBTableID.SlayerTaskSublist.COL_TASK, 0)[0];
		}
		else
		{
			List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, taskId);
			if (rows.isEmpty())
			{
				return null;
			}
			row = rows.get(0);
		}
		Object[] name = client.getDBTableField(row, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0);
		if (name == null || name.length == 0 || !(name[0] instanceof String))
		{
			return null;
		}
		String area = null;
		int areaId = client.getVarpValue(VarPlayerID.SLAYER_AREA);
		if (areaId > 0)
		{
			List<Integer> areas = client.getDBRowsByValue(DBTableID.SlayerArea.ID, DBTableID.SlayerArea.COL_AREA_ID, 0, areaId);
			if (!areas.isEmpty())
			{
				Object[] a = client.getDBTableField(areas.get(0), DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER, 0);
				area = a != null && a.length > 0 && a[0] instanceof String ? (String) a[0] : null;
			}
		}
		return new SlayerTask(((String) name[0]).toLowerCase(), area,
			client.getVarbitValue(VarbitID.SLAYER_MASTER) == KRYSTILIA);
	}

	/** The game values that change with the task (not with each kill's count). */
	static boolean affects(int varpId, int varbitId)
	{
		return varpId == VarPlayerID.SLAYER_COUNT || varpId == VarPlayerID.SLAYER_TARGET || varpId == VarPlayerID.SLAYER_AREA
			|| varbitId == VarbitID.SLAYER_TARGET_BOSSID || varbitId == VarbitID.SLAYER_MASTER;
	}
}
