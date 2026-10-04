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

	@ConfigSection(name = "Skill & other tabs", description = "Which skill tabs, and the cosmetics tab, to create", position = 2)
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

	@ConfigItem(keyName = "foodCount", name = "Food items", position = 3, section = contents,
		description = "How many of your best foods the melee, ranged and magic tabs show")
	@Range(min = 1, max = 6)
	default int foodCount()
	{
		return 4;
	}

	@ConfigItem(keyName = "skillSupplies", name = "Supplies in skill tabs", position = 4, section = contents,
		description = "Add the materials you own for each skill (herbs, logs, bars, seeds...) below its tools")
	default boolean skillSupplies()
	{
		return true;
	}

	@ConfigItem(keyName = "gapRows", name = "Blank row between sections", position = 5, section = contents,
		description = "Leave an empty row between groups in each tab")
	default boolean gapRows()
	{
		return false;
	}

	@ConfigItem(keyName = "dynamicIcons", name = "Best item as tab icon", position = 6, section = contents,
		description = "Use your best item for each tab's icon (your best weapon, food, bones, herb...). An icon you"
			+ " pick with a tab's Change icon option is kept; switch this off and on to hand icons back to the plugin")
	default boolean dynamicIcons()
	{
		return true;
	}

	@ConfigItem(keyName = "showReasons", name = "Show why on hover", position = 7, section = contents,
		description = "Hovering an item in one of these tabs shows why it is there, e.g. \"Demonbane: +60% vs demons\"")
	default boolean showReasons()
	{
		return true;
	}

	@ConfigItem(keyName = "removeOnStop", name = "Remove tabs when turned off", position = 8, section = contents,
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

	@ConfigItem(keyName = TAB_PREFIX + "cosmetics", name = "Cosmetics", position = 38, section = skillTabs,
		description = "Purely visual items no other tab shows: holiday items, trimmed and gilded clue rewards, spare"
			+ " graceful colours, ornament kits...")
	default boolean cosmetics()
	{
		return true;
	}
}
