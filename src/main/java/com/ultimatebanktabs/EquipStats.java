package com.ultimatebanktabs;

import lombok.AllArgsConstructor;
import lombok.Value;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemStats;

/**
 * The equipment numbers the scorer needs, decoupled from RuneLite's classes so
 * tab building can be unit tested with hand-made stats.
 */
@Value
@AllArgsConstructor
class EquipStats
{
	/** {@link EquipmentInventorySlot} index, or -1 when not equipable */
	int slot;
	int speed;
	int stab;
	int slash;
	int crush;
	int ranged;
	int magic;
	int str;
	int rstr;
	/** magic damage in percent, e.g. 5.0 for the occult necklace */
	double mdmg;
	int prayer;
	int defence;
	double weight;
	/** a weapon that takes both hands: no shield beside it */
	boolean twoHanded;

	EquipStats(int slot, int speed, int stab, int slash, int crush, int ranged, int magic, int str, int rstr, double mdmg,
		int prayer, int defence, double weight)
	{
		this(slot, speed, stab, slash, crush, ranged, magic, str, rstr, mdmg, prayer, defence, weight, false);
	}

	static EquipStats of(ItemStats stats)
	{
		if (stats == null)
		{
			return null;
		}
		ItemEquipmentStats e = stats.getEquipment();
		if (!stats.isEquipable() || e == null)
		{
			return new EquipStats(-1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, stats.getWeight());
		}
		int defence = e.getDstab() + e.getDslash() + e.getDcrush() + e.getDmagic() + e.getDrange();
		return new EquipStats(e.getSlot(), e.getAspeed(), e.getAstab(), e.getAslash(), e.getAcrush(), e.getArange(),
			e.getAmagic(), e.getStr(), e.getRstr(), e.getMdmg(), e.getPrayer(), defence, stats.getWeight(), e.isTwoHanded());
	}

	boolean isSlot(EquipmentInventorySlot s)
	{
		return slot == s.getSlotIdx();
	}

	int bestMelee()
	{
		return Math.max(stab, Math.max(slash, crush));
	}
}
