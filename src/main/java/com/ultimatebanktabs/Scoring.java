package com.ultimatebanktabs;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Stat-based ranking used where a curated list doesn't decide: armour slots
 * (by style profile), melee weapons (by a small damage model) and the ranged
 * and magic weapon fallbacks for accounts that own nothing curated.
 */
final class Scoring
{
	// A strong but not maxed player: 99s with super combats and piety, against a
	// sturdy generic target. Only the ordering matters, so rough numbers do.
	private static final double EFFECTIVE_STRENGTH = 118 * 1.23 + 11;
	private static final double EFFECTIVE_ATTACK = 118 * 1.2 + 11;
	private static final int OTHER_GEAR_STRENGTH = 100;
	private static final int OTHER_GEAR_ATTACK = 120;
	private static final double DEFENCE_ROLL = (150 + 9) * (100 + 64);

	static final String MELEE = "melee";
	static final String RANGED = "ranged";
	static final String MAGIC = "magic";
	static final String WEIGHT = "weight";

	private final Map<Integer, Double> meleeMultiplier;
	private final Set<Integer> doubleAccuracy;

	Scoring(Map<Integer, Double> meleeMultiplier, Set<Integer> doubleAccuracy)
	{
		this.meleeMultiplier = meleeMultiplier;
		this.doubleAccuracy = doubleAccuracy;
	}

	Scoring()
	{
		this(Collections.emptyMap(), Collections.emptySet());
	}

	static double armour(String profile, EquipStats s)
	{
		return armour(profile, s, "any");
	}

	/**
	 * How good an armour piece is for a style; zero or less means "not useful".
	 * Strength bonuses outweigh accuracy because max hit scales linearly while
	 * hit chance saturates against most targets. Prayer bonus is situational, so
	 * it never counts. Melee accuracy follows the main weapon's attack style
	 * ("stab", "slash", "crush"), or averages the three ("any").
	 */
	static double armour(String profile, EquipStats s, String meleeStyle)
	{
		if (s == null)
		{
			return 0;
		}
		double score;
		switch (profile)
		{
			case MELEE:
				score = 3 * s.getStr() + ("any".equals(meleeStyle)
					? (s.getStab() + s.getSlash() + s.getCrush()) / 3.0 : styleBonus(s, meleeStyle));
				if (rangedScore(s) > score || magicScore(s) > score)
				{
					// made for another style: the Eclipse Moon set's melee strength is for the atlatl, a ranged weapon
					return 0;
				}
				break;
			case RANGED:
				score = rangedScore(s);
				break;
			case MAGIC:
				score = magicScore(s);
				break;
			case WEIGHT:
				return -s.getWeight();
			default:
				return 0;
		}
		// a small defence term ranks pure armour (rune platebody...) for low-level
		// accounts and breaks ties, without outweighing a single strength point
		return score + 0.02 * s.getDefence();
	}

	private static double rangedScore(EquipStats s)
	{
		return 3 * s.getRstr() + s.getRanged();
	}

	private static double magicScore(EquipStats s)
	{
		return 4 * s.getMdmg() + s.getMagic();
	}

	/** The max-hit half of a style's profile: strength, ranged strength or magic damage (%). */
	static double damage(String profile, EquipStats s)
	{
		switch (profile)
		{
			case MELEE:
				return s.getStr();
			case RANGED:
				return s.getRstr();
			case MAGIC:
				return s.getMdmg();
			default:
				return 0;
		}
	}

	/** The accuracy half of a style's profile; melee follows the main weapon's style, or averages all three. */
	static double accuracy(String profile, EquipStats s, String meleeStyle)
	{
		switch (profile)
		{
			case MELEE:
				return "any".equals(meleeStyle) ? (s.getStab() + s.getSlash() + s.getCrush()) / 3.0
					: styleBonus(s, meleeStyle);
			case RANGED:
				return s.getRanged();
			case MAGIC:
				return s.getMagic();
			default:
				return 0;
		}
	}

	static boolean isMeleeWeapon(EquipStats s)
	{
		return s != null && s.getSlot() == net.runelite.api.EquipmentInventorySlot.WEAPON.getSlotIdx()
			&& (s.getStr() > 0 || s.bestMelee() > 0) && s.getRanged() <= 0 && s.getRstr() <= 0;
	}

	/** The attack bonus of one melee style: "stab", "slash" or "crush". */
	static int styleBonus(EquipStats s, String style)
	{
		switch (style)
		{
			case "stab":
				return s.getStab();
			case "slash":
				return s.getSlash();
			case "crush":
				return s.getCrush();
			default:
				return s.bestMelee();
		}
	}

	/**
	 * Rough damage per second of a melee weapon with one attack style
	 * ("stab", "slash", "crush", or "any" for the best of the three), crediting
	 * curated passives such as the scythe's extra hits.
	 */
	double meleeWeapon(int itemId, EquipStats s, String style)
	{
		return meleeWeapon(itemId, s, style, true);
	}

	/** As {@link #meleeWeapon} but without passive multipliers (accuracy rolls still count). */
	double meleeWeaponPlain(int itemId, EquipStats s, String style)
	{
		return meleeWeapon(itemId, s, style, false);
	}

	private double meleeWeapon(int itemId, EquipStats s, String style, boolean passives)
	{
		if (!isMeleeWeapon(s))
		{
			return 0;
		}
		switch (style)
		{
			case "stab":
				return meleeDps(itemId, s, s.getStab(), passives);
			case "slash":
				return meleeDps(itemId, s, s.getSlash(), passives);
			case "crush":
				return meleeDps(itemId, s, s.getCrush(), passives);
			default:
				return Math.max(meleeDps(itemId, s, s.getStab(), passives),
					Math.max(meleeDps(itemId, s, s.getSlash(), passives), meleeDps(itemId, s, s.getCrush(), passives)));
		}
	}

	private double meleeDps(int itemId, EquipStats s, int attackBonus, boolean passives)
	{
		if (attackBonus <= 0)
		{
			return 0;
		}
		double maxHit = (EFFECTIVE_STRENGTH * (OTHER_GEAR_STRENGTH + s.getStr() + 64) + 320) / 640;
		double attackRoll = EFFECTIVE_ATTACK * (OTHER_GEAR_ATTACK + attackBonus + 64);
		double hit = attackRoll > DEFENCE_ROLL
			? 1 - (DEFENCE_ROLL + 2) / (2 * (attackRoll + 1))
			: attackRoll / (2 * (DEFENCE_ROLL + 1));
		if (doubleAccuracy.contains(itemId))
		{
			hit = 1 - (1 - hit) * (1 - hit);
		}
		int speed = s.getSpeed() > 0 ? s.getSpeed() : 4;
		double multiplier = passives ? meleeMultiplier.getOrDefault(itemId, 1.0) : 1.0;
		return hit * maxHit / 2 / (speed * 0.6) * multiplier;
	}

	/** Fallback ranking for a ranged weapon nobody curated. */
	static double rangedWeapon(EquipStats s)
	{
		if (s == null || s.getSlot() != net.runelite.api.EquipmentInventorySlot.WEAPON.getSlotIdx() || s.getRanged() <= 0)
		{
			return 0;
		}
		int speed = s.getSpeed() > 0 ? s.getSpeed() : 5;
		return (s.getRanged() + 64.0) * (s.getRstr() + 64.0) / speed;
	}

	/** Fallback ranking for a magic weapon nobody curated. */
	static double magicWeapon(EquipStats s)
	{
		// a melee weapon's token magic bonus (+1 on every dagger, +5 on the voidwaker) doesn't make it one
		if (s == null || s.getSlot() != net.runelite.api.EquipmentInventorySlot.WEAPON.getSlotIdx() || s.getMagic() <= 0
			|| s.getMagic() * 4 < s.bestMelee())
		{
			return 0;
		}
		return s.getMagic() + 4 * s.getMdmg();
	}
}
