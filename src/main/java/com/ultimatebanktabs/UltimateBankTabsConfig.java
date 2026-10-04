package com.ultimatebanktabs;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(UltimateBankTabsConfig.GROUP)
public interface UltimateBankTabsConfig extends Config
{
	String GROUP = "ultimatebanktabs";
	/** config key prefix of the per-tab toggles below: "tab" + tab key */
	String TAB_PREFIX = "tab_";

	@ConfigSection(name = "Contents", description = "What goes into the tabs", position = 0)
	String contents = "contents";

	@ConfigSection(name = "Combat & movement tabs", description = "Which combat and movement tabs to create", position = 1)
	String gearTabs = "gearTabs";

	@ConfigSection(name = "Skill tabs", description = "Which skill tabs to create", position = 2)
	String skillTabs = "skillTabs";

	@ConfigItem(keyName = "countCarried", name = "Count worn & carried items", position = 0, section = contents,
		description = "Also count what you are wearing and carrying, so gear you have on without a bank placeholder"
			+ " still gets its slot in the tabs")
	default boolean countCarried()
	{
		return true;
	}

	@ConfigItem(keyName = "backups", name = "Backups per slot", position = 1, section = contents,
		description = "Also show the next best items you own for each slot and pick (0 shows only the best)")
	@Range(max = 3)
	default int backups()
	{
		return 0;
	}

	@ConfigItem(keyName = "combatSupplies", name = "Potions & food in combat tabs", position = 2, section = contents,
		description = "Add your best boosting potions, restores, brews and food to the melee, ranged and magic tabs")
	default boolean combatSupplies()
	{
		return true;
	}

	@ConfigItem(keyName = "skillSupplies", name = "Supplies in skill tabs", position = 3, section = contents,
		description = "Add the materials you own for each skill (herbs, logs, bars, seeds...) below its tools")
	default boolean skillSupplies()
	{
		return true;
	}

	@ConfigItem(keyName = "gapRows", name = "Blank row between sections", position = 4, section = contents,
		description = "Leave an empty row between groups in each tab")
	default boolean gapRows()
	{
		return false;
	}

	@ConfigItem(keyName = "dynamicIcons", name = "Best item as tab icon", position = 5, section = contents,
		description = "Use your best item for each tab's icon (your best weapon, axe, pickaxe...)")
	default boolean dynamicIcons()
	{
		return true;
	}

	@ConfigItem(keyName = "removeOnStop", name = "Remove tabs when turned off", position = 6, section = contents,
		description = "Delete the tabs this plugin made when the plugin is turned off. They come back when it is"
			+ " turned on again")
	default boolean removeOnStop()
	{
		return true;
	}

	// ---------------------------------------------------------------- combat & movement

	@ConfigItem(keyName = TAB_PREFIX + "melee", name = "Melee", position = 10, section = gearTabs, description = "Best-in-slot melee gear")
	default boolean melee()
	{
		return true;
	}

	@ConfigItem(keyName = TAB_PREFIX + "ranged", name = "Ranged", position = 11, section = gearTabs, description = "Best-in-slot ranged gear")
	default boolean ranged()
	{
		return true;
	}

	@ConfigItem(keyName = TAB_PREFIX + "magic", name = "Magic", position = 12, section = gearTabs, description = "Best-in-slot magic gear and runes")
	default boolean magic()
	{
		return true;
	}

	@ConfigItem(keyName = TAB_PREFIX + "movement", name = "Movement", position = 13, section = gearTabs, description = "Graceful, run energy, teleports and teleport runes")
	default boolean movement()
	{
		return true;
	}

	// ---------------------------------------------------------------- skills

	@ConfigItem(keyName = TAB_PREFIX + "prayer", name = "Prayer", position = 20, section = skillTabs, description = "Prayer tab")
	default boolean prayer()
	{
		return true;
	}

	@ConfigItem(keyName = TAB_PREFIX + "slayer", name = "Slayer", position = 21, section = skillTabs, description = "Slayer tab")
	default boolean slayer()
	{
		return true;
	}

	@ConfigItem(keyName = TAB_PREFIX + "agility", name = "Agility", position = 22, section = skillTabs, description = "Agility tab")
	default boolean agility()
	{
		return true;
	}

	@ConfigItem(keyName = TAB_PREFIX + "thieving", name = "Thieving", position = 23, section = skillTabs, description = "Thieving tab")
	default boolean thieving()
	{
		return true;
	}

	@ConfigItem(keyName = TAB_PREFIX + "herblore", name = "Herblore", position = 24, section = skillTabs, description = "Herblore tab")
	default boolean herblore()
	{
		return true;
	}

	@ConfigItem(keyName = TAB_PREFIX + "crafting", name = "Crafting", position = 25, section = skillTabs, description = "Crafting tab")
	default boolean crafting()
	{
		return true;
	}

	@ConfigItem(keyName = TAB_PREFIX + "fletching", name = "Fletching", position = 26, section = skillTabs, description = "Fletching tab")
	default boolean fletching()
	{
		return true;
	}

	@ConfigItem(keyName = TAB_PREFIX + "hunter", name = "Hunter", position = 27, section = skillTabs, description = "Hunter tab")
	default boolean hunter()
	{
		return true;
	}

	@ConfigItem(keyName = TAB_PREFIX + "mining", name = "Mining", position = 28, section = skillTabs, description = "Mining tab")
	default boolean mining()
	{
		return true;
	}

	@ConfigItem(keyName = TAB_PREFIX + "smithing", name = "Smithing", position = 29, section = skillTabs, description = "Smithing tab")
	default boolean smithing()
	{
		return true;
	}

	@ConfigItem(keyName = TAB_PREFIX + "fishing", name = "Fishing", position = 30, section = skillTabs, description = "Fishing tab")
	default boolean fishing()
	{
		return true;
	}

	@ConfigItem(keyName = TAB_PREFIX + "cooking", name = "Cooking", position = 31, section = skillTabs, description = "Cooking tab")
	default boolean cooking()
	{
		return true;
	}

	@ConfigItem(keyName = TAB_PREFIX + "firemaking", name = "Firemaking", position = 32, section = skillTabs, description = "Firemaking tab")
	default boolean firemaking()
	{
		return true;
	}

	@ConfigItem(keyName = TAB_PREFIX + "woodcutting", name = "Woodcutting", position = 33, section = skillTabs, description = "Woodcutting tab")
	default boolean woodcutting()
	{
		return true;
	}

	@ConfigItem(keyName = TAB_PREFIX + "farming", name = "Farming", position = 34, section = skillTabs, description = "Farming tab")
	default boolean farming()
	{
		return true;
	}

	@ConfigItem(keyName = TAB_PREFIX + "runecraft", name = "Runecraft", position = 35, section = skillTabs, description = "Runecraft tab")
	default boolean runecraft()
	{
		return true;
	}

	@ConfigItem(keyName = TAB_PREFIX + "construction", name = "Construction", position = 36, section = skillTabs, description = "Construction tab")
	default boolean construction()
	{
		return true;
	}

	@ConfigItem(keyName = TAB_PREFIX + "sailing", name = "Sailing", position = 37, section = skillTabs, description = "Sailing tab")
	default boolean sailing()
	{
		return true;
	}
}
