package com.ultimatebanktabs;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

public class TabBuilderTest
{
	private static TabData data;
	private static TabBuilder builder;
	private static Map<String, List<Integer>> ids;
	private static final Map<Integer, EquipStats> STATS = new HashMap<>();

	@BeforeClass
	public static void load() throws Exception
	{
		Gson gson = new Gson();
		data = TabData.load(gson);
		try (Reader r = new InputStreamReader(TabBuilderTest.class.getResourceAsStream("fixture.json"), StandardCharsets.UTF_8))
		{
			Fixture f = gson.fromJson(r, new TypeToken<Fixture>()
			{
			}.getType());
			ids = f.items;
			f.stats.forEach((id, v) -> STATS.put(Integer.parseInt(id), new EquipStats((int) v[0], (int) v[1],
				(int) v[2], (int) v[3], (int) v[4], (int) v[5], (int) v[6], (int) v[7], (int) v[8], v[9], (int) v[10],
				(int) v[11], v[12], v.length > 13 && v[13] > 0)));
		}
		builder = new TabBuilder(data, STATS::get);
	}

	static class Fixture
	{
		Map<String, List<Integer>> items;
		Map<String, double[]> stats;
	}

	static int id(String name)
	{
		List<Integer> list = ids.get(name);
		assertNotNull("not in fixture: " + name, list);
		return list.get(0);
	}

	static Set<Integer> own(String... names)
	{
		return Arrays.stream(names).map(TabBuilderTest::id).collect(Collectors.toSet());
	}

	static TabData.Tab tab(String key)
	{
		return data.tabs.stream().filter(t -> t.key.equals(key)).findFirst().orElseThrow(IllegalStateException::new);
	}

	static TabBuilder.Built build(String tab, Set<Integer> owned)
	{
		return builder.build(tab(tab), owned, new TabBuilder.Options());
	}

	static TabBuilder.Section section(TabBuilder.Built built, String key)
	{
		return built.getSections().stream().filter(s -> s.getKey().equals(key)).findFirst().orElseThrow(IllegalStateException::new);
	}

	static Map<String, Integer> bySlot(TabBuilder.Section s)
	{
		Map<String, Integer> m = new HashMap<>();
		s.getItems().forEach(p -> m.putIfAbsent(p.getSlot(), p.getItemId()));
		return m;
	}

	static Set<Integer> items(TabBuilder.Section s)
	{
		return s.getItems().stream().map(TabBuilder.Placement::getItemId).collect(Collectors.toSet());
	}

	static final String[] MAXED_MELEE = {
		"Torva full helm", "Torva platebody", "Torva platelegs", "Infernal cape", "Amulet of rancour", "Ferocious gloves",
		"Primordial boots", "Ultor ring", "Avernic defender", "Rada's blessing 4", "Scythe of Vitur", "Osmumten's fang",
		"Blade of Saeldor (c)", "Inquisitor's mace", "Abyssal tentacle", "Abyssal whip", "Dragon warhammer",
		"Bandos godsword", "Dragon claws", "Slayer helmet (i)", "Salve amulet(ei)", "Inquisitor's great helm",
		"Inquisitor's hauberk", "Inquisitor's plateskirt", "Neitiznot faceguard", "Bandos chestplate", "Bandos tassets",
		"Fire cape", "Barrows gloves", "Dragon defender", "Dragon hunter lance", "Ursine chainmace",
		"Super combat potion", "Saradomin brew", "Super restore", "Shark", "Anglerfish",
	};

	@Test
	public void maxedMeleeMainSet()
	{
		TabBuilder.Built built = build("melee", own(MAXED_MELEE));
		Map<String, Integer> main = bySlot(section(built, "main"));
		assertEquals(id("Torva full helm"), (int) main.get("HEAD"));
		assertEquals(id("Infernal cape"), (int) main.get("CAPE"));
		assertEquals(id("Amulet of rancour"), (int) main.get("AMULET"));
		assertNull("prayer bonus is situational: no blessing in the main set", main.get("AMMO"));
		assertTrue(items(section(built, "situational")).contains(id("Rada's blessing 4")));
		assertEquals(id("Scythe of Vitur"), (int) main.get("WEAPON"));
		assertEquals(id("Torva platebody"), (int) main.get("BODY"));
		assertEquals(id("Avernic defender"), (int) main.get("SHIELD"));
		assertEquals(id("Torva platelegs"), (int) main.get("LEGS"));
		assertEquals(id("Ferocious gloves"), (int) main.get("GLOVES"));
		assertEquals(id("Primordial boots"), (int) main.get("BOOTS"));
		assertEquals(id("Ultor ring"), (int) main.get("RING"));
		assertEquals(id("Scythe of Vitur"), built.getHero());
	}

	@Test
	public void maxedMeleeWeaponsAndSituational()
	{
		TabBuilder.Built built = build("melee", own(MAXED_MELEE));
		Set<Integer> weapons = items(section(built, "weapons"));
		assertTrue("fang is the stab pick", weapons.contains(id("Osmumten's fang")));
		assertTrue("blade is the slash pick", weapons.contains(id("Blade of Saeldor (c)")));
		assertTrue("inquisitor's mace is the crush pick", weapons.contains(id("Inquisitor's mace")));
		assertTrue(weapons.contains(id("Dragon hunter lance")));
		assertTrue("wilderness weapon kept as situational", weapons.contains(id("Ursine chainmace")));
		assertFalse("outclassed whip is left out", built.itemIds().contains(id("Abyssal whip")));
		assertFalse(built.itemIds().contains(id("Abyssal tentacle")));
		assertFalse("only the best body", built.itemIds().contains(id("Bandos chestplate")));

		Set<Integer> specs = items(section(built, "specials"));
		assertEquals(own("Dragon warhammer", "Bandos godsword", "Dragon claws"), specs);

		Set<Integer> situational = items(section(built, "situational"));
		assertTrue(situational.containsAll(own("Slayer helmet (i)", "Salve amulet(ei)", "Inquisitor's great helm",
			"Inquisitor's hauberk", "Inquisitor's plateskirt")));

		Set<Integer> supplies = items(section(built, "supplies"));
		assertTrue(supplies.containsAll(own("Super combat potion", "Saradomin brew", "Super restore")));
		assertEquals("the food has its own row", own("Anglerfish", "Shark"), items(section(built, "supplies_food")));
		assertEquals("Heals 20", built.reasons().get(id("Shark")));

		TabBuilder.Options oneFood = new TabBuilder.Options();
		oneFood.foodCount = 1;
		Set<Integer> fewer = builder.build(tab("melee"), own(MAXED_MELEE), oneFood).itemIds();
		assertTrue(fewer.contains(id("Anglerfish")));
		assertFalse("food setting caps the count", fewer.contains(id("Shark")));
	}

	@Test
	public void soulreaperTakesSlashFromBlade()
	{
		Set<Integer> owned = own(MAXED_MELEE);
		owned.add(id("Soulreaper axe"));
		Set<Integer> weapons = items(section(build("melee", owned), "weapons"));
		assertTrue(weapons.contains(id("Blade of Saeldor (c)")) || weapons.contains(id("Soulreaper axe")));
	}

	@Test
	public void backupsAddNextBest()
	{
		TabBuilder.Options options = new TabBuilder.Options();
		options.backups = 1;
		TabBuilder.Built built = builder.build(tab("melee"), own(MAXED_MELEE), options);
		assertTrue(built.itemIds().containsAll(own("Fire cape", "Dragon defender", "Barrows gloves")));
	}

	@Test
	public void lowLevelMelee()
	{
		TabBuilder.Built built = build("melee", own("Rune scimitar", "Rune longsword", "Iron scimitar", "Rune full helm",
			"Rune platebody", "Rune platelegs", "Rune kiteshield", "Amulet of strength", "Leather boots", "Climbing boots",
			"Chef's hat", "Shark"));
		Map<String, Integer> main = bySlot(section(built, "main"));
		assertEquals("scimitar beats the slower longsword", id("Rune scimitar"), (int) main.get("WEAPON"));
		assertEquals(id("Rune full helm"), (int) main.get("HEAD"));
		assertEquals(id("Amulet of strength"), (int) main.get("AMULET"));
		assertEquals("climbing boots have strength", id("Climbing boots"), (int) main.get("BOOTS"));
		assertFalse("no-stat hats are never best in slot", built.itemIds().contains(id("Chef's hat")));
		assertFalse(built.itemIds().contains(id("Iron scimitar")));
	}

	@Test
	public void rangedAmmoFollowsLauncher()
	{
		TabBuilder.Built built = build("ranged", own("Twisted bow", "Dragon arrow", "Amethyst arrow",
			"Ruby dragon bolts (e)", "Diamond dragon bolts (e)", "Armadyl crossbow", "Masori mask (f)",
			"Masori body (f)", "Masori chaps (f)", "Zaryte vambraces", "Necklace of anguish", "Dizana's quiver",
			"Pegasian boots", "Venator ring", "Twisted buckler", "Toxic blowpipe", "Dragon dart", "Magic shortbow"));
		Map<String, Integer> main = bySlot(section(built, "main"));
		assertEquals(id("Twisted bow"), (int) main.get("WEAPON"));
		assertEquals(id("Dragon arrow"), (int) main.get("AMMO"));
		assertEquals(id("Masori body (f)"), (int) main.get("BODY"));
		assertEquals(id("Dizana's quiver"), (int) main.get("CAPE"));
		assertFalse("only the best arrows", built.itemIds().contains(id("Amethyst arrow")));
		Set<Integer> ammo = items(section(built, "ammo"));
		assertTrue("bolts because the crossbow is in the tab",
			ammo.containsAll(own("Ruby dragon bolts (e)", "Diamond dragon bolts (e)", "Dragon dart")));
	}

	@Test
	public void boltsNeedACrossbowInTheTab()
	{
		TabBuilder.Built built = build("ranged", own("Twisted bow", "Dragon arrow", "Ruby dragon bolts (e)"));
		assertFalse(built.itemIds().contains(id("Ruby dragon bolts (e)")));
	}

	@Test
	public void crystalBowLeavesTheAmmoSlotToSituationalBlessings()
	{
		TabBuilder.Built built = build("ranged", own("Bow of Faerdhinen (c)", "Dragon arrow", "Holy blessing"));
		Map<String, Integer> main = bySlot(section(built, "main"));
		assertNull(main.get("AMMO"));
		assertTrue(items(section(built, "situational")).contains(id("Holy blessing")));
	}

	@Test
	public void magicMainSetAndRunes()
	{
		TabBuilder.Built built = build("magic", own("Tumeken's shadow", "Ancestral hat", "Ancestral robe top",
			"Ancestral robe bottom", "Occult necklace", "Tormented bracelet", "Eternal boots", "Magus ring",
			"Imbued Saradomin cape", "Elidinis' ward (f)", "Kodai wand", "Harmonised Nightmare staff", "Blood rune",
			"Death rune", "Rune pouch", "Salve amulet(ei)"));
		Map<String, Integer> main = bySlot(section(built, "main"));
		assertEquals(id("Tumeken's shadow"), (int) main.get("WEAPON"));
		assertEquals(id("Ancestral hat"), (int) main.get("HEAD"));
		assertEquals(id("Occult necklace"), (int) main.get("AMULET"));
		assertEquals(id("Elidinis' ward (f)"), (int) main.get("SHIELD"));
		Set<Integer> weapons = items(section(built, "weapons"));
		assertTrue(weapons.containsAll(own("Kodai wand", "Harmonised Nightmare staff")));
		assertTrue(items(section(built, "runes")).contains(id("Rune pouch")));
		assertTrue(items(section(built, "runes_combat")).containsAll(own("Blood rune", "Death rune")));
	}

	@Test
	public void gracefulPicksTheFullestColour()
	{
		TabBuilder.Built built = build("movement", own("Graceful hood", "Graceful cape", "Graceful top", "Graceful legs",
			"Graceful gloves", "Graceful boots", "Graceful hood (Arceuus)", "Graceful top (Arceuus)", "Stamina potion",
			"Ring of dueling", "Amulet of glory", "Varrock teleport (tablet)", "Law rune", "Boots of lightness"));
		Map<String, Integer> main = bySlot(section(built, "main"));
		assertEquals("full graceful wins over lighter boots", id("Graceful boots"), (int) main.get("BOOTS"));
		assertEquals(id("Graceful hood"), (int) main.get("HEAD"));
		assertTrue("the lighter boots stand beside it", items(section(built, "situational"))
			.contains(id("Boots of lightness")));
		assertTrue(items(section(built, "run")).contains(id("Stamina potion")));
		assertFalse(built.itemIds().contains(id("Graceful hood (Arceuus)")));
		assertTrue(built.itemIds().containsAll(own("Ring of dueling", "Amulet of glory", "Varrock teleport (tablet)",
			"Law rune")));
	}

	@Test
	public void withoutFullGracefulEachSlotTakesItsLightestItem()
	{
		// no graceful gloves: the set bonus is out of reach
		Set<Integer> owned = own("Graceful hood", "Graceful cape", "Graceful top", "Graceful legs", "Graceful boots",
			"Penance gloves", "Boots of lightness", "Spottier cape");
		for (String tab : new String[]{"movement", "agility"})
		{
			Map<String, Integer> main = bySlot(section(build(tab, owned), "main"));
			assertEquals(tab, id("Boots of lightness"), (int) main.get("BOOTS"));
			assertEquals(tab, id("Penance gloves"), (int) main.get("GLOVES"));
			assertEquals(tab, id("Spottier cape"), (int) main.get("CAPE"));
			assertEquals(tab, id("Graceful legs"), (int) main.get("LEGS"));
		}
	}

	@Test
	public void gracefulColoursMixAndTheAgilityCapeCountsAsItsCape()
	{
		TabBuilder.Built mixed = build("agility", own("Graceful hood", "Graceful top", "Graceful legs",
			"Graceful gloves", "Graceful boots", "Graceful cape (Arceuus)", "Boots of lightness"));
		Map<String, Integer> main = bySlot(section(mixed, "main"));
		assertEquals("a mixed set still counts as full graceful", id("Graceful boots"), (int) main.get("BOOTS"));
		assertEquals(id("Graceful cape (Arceuus)"), (int) main.get("CAPE"));

		TabBuilder.Built agilityCape = build("movement", own("Graceful hood", "Graceful top", "Graceful legs",
			"Graceful gloves", "Graceful boots", "Agility cape", "Penance gloves"));
		main = bySlot(section(agilityCape, "main"));
		assertEquals(id("Agility cape"), (int) main.get("CAPE"));
		assertEquals("the agility cape completes the set", id("Graceful gloves"), (int) main.get("GLOVES"));
	}

	@Test
	public void fishingShowsBothAnglerLooks()
	{
		TabBuilder.Built built = build("fishing", own("Spirit angler headband", "Spirit angler top", "Angler hat",
			"Angler top", "Angler waders", "Angler boots"));
		Map<String, Integer> main = bySlot(section(built, "main"));
		assertEquals("the more complete look fills the block", id("Angler hat"), (int) main.get("HEAD"));
		assertEquals(id("Angler top"), (int) main.get("BODY"));
		assertTrue("the other look stands beside it", items(section(built, "situational"))
			.containsAll(own("Spirit angler headband", "Spirit angler top")));
	}

	@Test
	public void miningRewardsHaveTheirOwnRow()
	{
		TabBuilder.Built built = build("mining", own("Golden nugget", "Stardust"));
		assertEquals(own("Golden nugget", "Stardust"), items(section(built, "currency")));
		assertTrue(built.reasons().get(id("Golden nugget")).startsWith("Motherlode Mine currency"));
	}

	@Test
	public void emptySkillSlotsTakeYourLightestGear()
	{
		Set<Integer> owned = own("Rogue mask", "Rogue gloves", "Graceful hood", "Graceful cape", "Graceful top",
			"Graceful legs", "Graceful gloves", "Graceful boots", "Boots of lightness");
		Map<String, Integer> main = bySlot(section(build("thieving", owned), "main"));
		assertEquals("the skill's own piece wins", id("Rogue mask"), (int) main.get("HEAD"));
		assertEquals(id("Rogue gloves"), (int) main.get("GLOVES"));
		assertEquals("an empty slot takes graceful", id("Graceful top"), (int) main.get("BODY"));
		assertEquals(id("Graceful legs"), (int) main.get("LEGS"));
		assertEquals(id("Graceful cape"), (int) main.get("CAPE"));
		assertEquals("no set bonus with a rogue mask on, so the lighter boots", id("Boots of lightness"),
			(int) main.get("BOOTS"));
	}

	@Test
	public void alternativesFillEmptySlotsBeforeGraceful()
	{
		// no smiths' uniform, cape or ring: the owned alternatives move into the block, graceful fills the rest
		TabBuilder.Built built = build("smithing", own("Varrock armour 2", "Goldsmith gauntlets", "Ice gloves",
			"Graceful hood", "Graceful cape", "Graceful gloves", "Hammer"));
		Map<String, Integer> main = bySlot(section(built, "main"));
		assertEquals(id("Varrock armour 2"), (int) main.get("BODY"));
		assertEquals(id("Goldsmith gauntlets"), (int) main.get("GLOVES"));
		assertEquals(id("Graceful hood"), (int) main.get("HEAD"));
		assertEquals(id("Graceful cape"), (int) main.get("CAPE"));
		assertTrue("the other gloves stay beside it", items(section(built, "situational")).contains(id("Ice gloves")));
		int[] layout = LayoutPlanner.plan(built, false);
		assertEquals("graceful gloves beside the gauntlets", 4, pos(layout, id("Graceful gloves")) / LayoutPlanner.WIDTH);
	}

	@Test
	public void gracefulStandsBesideTheSkillPiece()
	{
		TabBuilder.Built built = build("thieving", own("Rogue mask", "Rogue gloves", "Graceful hood", "Graceful gloves",
			"Graceful top"));
		Map<String, Integer> main = bySlot(section(built, "main"));
		assertEquals(id("Rogue mask"), (int) main.get("HEAD"));
		int[] layout = LayoutPlanner.plan(built, false);
		int hood = pos(layout, id("Graceful hood"));
		assertEquals("the graceful hood is on the head row", 0, hood / LayoutPlanner.WIDTH);
		assertTrue("to the side", hood % LayoutPlanner.WIDTH >= 4);
		int gloves = pos(layout, id("Graceful gloves"));
		assertEquals("the graceful gloves on the gloves row", 4, gloves / LayoutPlanner.WIDTH);
		assertTrue(gloves % LayoutPlanner.WIDTH >= 4);
		assertEquals("an empty slot still takes graceful in the block", id("Graceful top"), (int) main.get("BODY"));
	}

	@Test
	public void noSkillGearMeansNoGracefulFiller()
	{
		TabBuilder.Built built = build("thieving", own("Graceful hood", "Graceful top", "Graceful legs"));
		assertTrue(items(section(built, "main")).isEmpty());
		assertTrue("graceful alone doesn't make a skill tab", built.isEmpty());
	}

	@Test
	public void thievingGetsAnEquipmentBlock()
	{
		TabBuilder.Built built = build("thieving", own("Rogue mask", "Rogue gloves", "Gloves of silence",
			"Thieving cape", "Max cape"));
		Map<String, Integer> main = bySlot(section(built, "main"));
		assertEquals(id("Rogue mask"), (int) main.get("HEAD"));
		assertEquals(id("Rogue gloves"), (int) main.get("GLOVES"));
		assertEquals(id("Thieving cape"), (int) main.get("CAPE"));
		assertTrue(items(section(built, "situational")).contains(id("Gloves of silence")));
		int[] layout = LayoutPlanner.plan(built, false);
		assertEquals("the mask sits in the head cell", id("Rogue mask"), layout[1]);
	}

	@Test
	public void skillTabs()
	{
		TabBuilder.Built wc = build("woodcutting", own("Dragon axe", "Rune axe", "Crystal axe", "Lumberjack hat",
			"Lumberjack top", "Logs"));
		assertTrue(wc.itemIds().containsAll(own("Crystal axe", "Lumberjack hat", "Lumberjack top")));
		assertFalse(wc.itemIds().contains(id("Dragon axe")));
		assertEquals(id("Crystal axe"), wc.getHero());

		TabBuilder.Built herb = build("herblore", own("Grimy ranarr weed", "Ranarr weed", "Ranarr potion (unf)",
			"Snape grass", "Prayer potion"));
		assertTrue(herb.itemIds().containsAll(own("Grimy ranarr weed", "Ranarr weed", "Ranarr potion (unf)",
			"Snape grass")));

		TabBuilder.Options noSupplies = new TabBuilder.Options();
		noSupplies.skillSupplies = false;
		assertTrue(builder.build(tab("herblore"), own("Ranarr weed"), noSupplies).isEmpty());

		TabBuilder.Built fm = build("firemaking", own("Tinderbox", "Logs", "Oak logs"));
		assertTrue(fm.itemIds().containsAll(own("Tinderbox", "Logs", "Oak logs")));
	}

	@Test
	public void everydaySawFirstCrystalSawForTheBoost()
	{
		TabBuilder.Built built = build("construction", own("Crystal saw", "Saw", "Hammer"));
		List<Integer> gear = section(built, "gear").getItems().stream().map(TabBuilder.Placement::getItemId)
			.collect(Collectors.toList());
		assertEquals(Arrays.asList(id("Saw"), id("Crystal saw"), id("Hammer")), gear);
		assertEquals(id("Saw"), built.getHero());
		assertTrue(built.reasons().get(id("Crystal saw")).contains("+3"));

		TabBuilder.Built amy = build("construction", own("Crystal saw", "Saw", "Amy's saw"));
		assertTrue("Amy's saw replaces the plain saw", amy.itemIds().contains(id("Amy's saw")));
		assertFalse(amy.itemIds().contains(id("Saw")));
	}

	@Test
	public void reasonsExplainEachItem()
	{
		TabBuilder.Options options = new TabBuilder.Options();
		options.backups = 1;
		TabBuilder.Built built = builder.build(tab("melee"), own(MAXED_MELEE), options);
		Map<Integer, String> reasons = built.reasons();
		assertEquals("Best helmet for melee stats", reasons.get(id("Torva full helm")));
		assertEquals("Best stab weapon", build("melee", own(MAXED_MELEE)).reasons().get(id("Osmumten's fang")));
		assertTrue(reasons.get(id("Fire cape")).startsWith("Backup: "));
		for (TabBuilder.Section s : built.getSections())
		{
			for (TabBuilder.Placement p : s.getItems())
			{
				assertNotNull("reason for " + p.getItemId(), p.getReason());
				assertFalse(p.getReason().isEmpty());
			}
		}
	}

	@Test
	public void cosmeticsSkipWhatOtherTabsShow()
	{
		Set<Integer> owned = own("Graceful hood", "Graceful cape", "Graceful top", "Graceful legs", "Graceful gloves",
			"Graceful boots", "Graceful hood (Arceuus)", "Graceful top (Arceuus)", "Chef's hat");
		TabBuilder.Options options = new TabBuilder.Options();
		options.shownElsewhere = build("movement", owned).itemIds();
		TabBuilder.Built cosmetics = builder.build(tab("cosmetics"), owned, options);
		assertTrue(cosmetics.itemIds().containsAll(own("Graceful hood (Arceuus)", "Graceful top (Arceuus)")));
		assertFalse("the chef's hat opens the Cooking Guild, so it lives in the cooking tab",
			cosmetics.itemIds().contains(id("Chef's hat")));
		assertFalse("the colour movement uses isn't repeated", cosmetics.itemIds().contains(id("Graceful hood")));
	}

	@Test
	public void herbChainsGetARowPerHerb()
	{
		TabBuilder.Built herb = build("herblore", own("Guam leaf", "Eye of newt", "Irit leaf", "Grimy ranarr weed",
			"Ranarr weed", "Ranarr potion (unf)", "Snape grass", "Prayer potion"));
		int[] layout = LayoutPlanner.plan(herb, false);
		int w = LayoutPlanner.WIDTH;
		// only herbs you have something for get a row: guam 0, ranarr 1, irit 2
		// columns: grimy, clean, secondary, unfinished, then the potion's doses 4 to 1
		assertEquals(id("Guam leaf"), layout[1]);
		assertEquals("eye of newt beside guam", id("Eye of newt"), layout[2]);
		assertEquals(id("Grimy ranarr weed"), layout[w]);
		assertEquals(id("Ranarr weed"), layout[w + 1]);
		assertEquals(id("Snape grass"), layout[w + 2]);
		assertEquals(id("Ranarr potion (unf)"), layout[w + 3]);
		assertEquals("the potion in its dose column", 1, row(layout, id("Prayer potion")));
		assertTrue(col(layout, id("Prayer potion")) >= 4);
		assertEquals(id("Irit leaf"), layout[2 * w + 1]);
		assertEquals("and beside irit", id("Eye of newt"), layout[2 * w + 2]);
		assertTrue(herb.reasons().get(id("Eye of newt")).contains("super attack"));
	}

	@Test
	public void iritGetsARowForEachOfItsPotions()
	{
		TabBuilder.Built herb = build("herblore", own("Irit leaf", "Eye of newt", "Unicorn horn dust", "Superantipoison"));
		int[] layout = LayoutPlanner.plan(herb, false);
		int w = LayoutPlanner.WIDTH;
		List<Integer> iritRows = new ArrayList<>();
		for (int i = 0; i < layout.length; i++)
		{
			if (layout[i] == id("Irit leaf"))
			{
				assertEquals("clean herbs in column 1", 1, i % w);
				iritRows.add(i / w);
			}
		}
		assertEquals("one irit row per potion", 2, iritRows.size());
		assertEquals("super attack first", id("Eye of newt"), layout[iritRows.get(0) * w + 2]);
		assertEquals(id("Unicorn horn dust"), layout[iritRows.get(1) * w + 2]);
		assertEquals((int) iritRows.get(1), row(layout, id("Superantipoison")));
		assertTrue(herb.getSections().stream().noneMatch(s -> s.getKey().equals("main") && !s.getItems().isEmpty()));
	}

	static TabBuilder.Built slayerOnTask(String task, String... owned)
	{
		TabBuilder.Options options = new TabBuilder.Options();
		options.slayerTask = new SlayerTask(task, null, false);
		return builder.build(tab("slayer"), own(owned), options);
	}

	@Test
	public void theSlayerTabFollowsTheTask()
	{
		TabBuilder.Built built = slayerOnTask("basilisks", "Abyssal whip", "Slayer helmet (i)", "Rune platebody",
			"Mirror shield", "Shark");
		Map<String, Integer> setup = bySlot(section(built, "ontask_main"));
		assertEquals("the slayer helmet on task", id("Slayer helmet (i)"), (int) setup.get("HEAD"));
		assertEquals("the basilisk gaze needs a mirror shield", id("Mirror shield"), (int) setup.get("SHIELD"));
		assertEquals(id("Abyssal whip"), (int) setup.get("WEAPON"));
		assertTrue("food comes along", items(section(built, "ontask_supplies")).contains(id("Shark")));
		assertTrue("the general slayer gear doesn't repeat the helmet",
			!items(section(built, "gear")).contains(id("Slayer helmet (i)")));
		int[] layout = LayoutPlanner.plan(built, false);
		assertEquals("drawn like the equipment screen", id("Slayer helmet (i)"), layout[1]);
	}

	@Test
	public void undeadTasksWearTheSalveAndDragonTasksTheLance()
	{
		Map<String, Integer> undead = bySlot(section(slayerOnTask("aberrant spectres", "Abyssal whip",
			"Slayer helmet (i)", "Salve amulet(ei)", "Amulet of strength"), "ontask_main"));
		assertEquals(id("Salve amulet(ei)"), (int) undead.get("AMULET"));
		Map<String, Integer> dragons = bySlot(section(slayerOnTask("blue dragons", "Abyssal whip",
			"Dragon hunter lance", "Rune platebody"), "ontask_main"));
		assertEquals(id("Dragon hunter lance"), (int) dragons.get("WEAPON"));
	}

	@Test
	public void noTaskKeepsTheGeneralSlayerTab()
	{
		TabBuilder.Built built = build("slayer", own("Slayer helmet (i)", "Abyssal whip"));
		assertTrue("sections " + built.getSections().stream().map(TabBuilder.Section::getKey).collect(Collectors.toList()),
			built.getSections().stream().noneMatch(s -> s.getKey().startsWith("ontask_")));
		assertTrue("gear " + items(section(built, "gear")), items(section(built, "gear")).contains(id("Slayer helmet (i)")));
	}

	static Map<String, Integer> taskSetup(String task, String... owned)
	{
		return bySlot(section(slayerOnTask(task, owned), "ontask_main"));
	}

	@Test
	public void aRecolouredSlayerHelmetIsTheTasksHelmetToo()
	{
		assertEquals(id("Black slayer helmet (i)"),
			(int) taskSetup("dust devils", "Black slayer helmet (i)", "Facemask", "Trident of the swamp").get("HEAD"));
	}

	@Test
	public void aShieldTheTaskNeedsLeavesOneHandForTheWeapon()
	{
		Map<String, Integer> basilisks = taskSetup("basilisks", "Scythe of Vitur", "Abyssal whip", "Mirror shield",
			"Rune platebody");
		assertEquals(id("Abyssal whip"), (int) basilisks.get("WEAPON"));
		assertEquals(id("Mirror shield"), (int) basilisks.get("SHIELD"));
		// a super antifire makes the ward a choice: the two-handed bow sets it aside
		TabBuilder.Built vorkath = slayerOnTask("vorkath", "Twisted bow", "Dragon arrow", "Dragonfire ward",
			"Super antifire potion");
		Map<String, Integer> setup = bySlot(section(vorkath, "ontask_main"));
		assertEquals(id("Twisted bow"), (int) setup.get("WEAPON"));
		assertNull("both hands on the bow", setup.get("SHIELD"));
		assertTrue(items(section(vorkath, "ontask_alts")).contains(id("Dragonfire ward")));
		// without one, the dragonfire shield is needed
		assertEquals(id("Anti-dragon shield"), (int) taskSetup("vorkath", "Twisted bow", "Dragon arrow", "Rune crossbow",
			"Runite bolts", "Anti-dragon shield").get("SHIELD"));
	}

	@Test
	public void aBaneLauncherTakesItsOwnAmmo()
	{
		Map<String, Integer> setup = taskSetup("black dragons", "Twisted bow", "Dragon arrow", "Dragon hunter crossbow",
			"Ruby dragon bolts (e)");
		assertEquals(id("Dragon hunter crossbow"), (int) setup.get("WEAPON"));
		assertEquals(id("Ruby dragon bolts (e)"), (int) setup.get("AMMO"));
	}

	@Test
	public void theTasksOwnWeaponPicksTheStyle()
	{
		// the lance stays out of the main weapon pick, yet arms melee
		Map<String, Integer> red = taskSetup("red dragons", "Dragon hunter lance", "Magic shortbow", "Rune arrow",
			"Anti-dragon shield");
		assertEquals(id("Dragon hunter lance"), (int) red.get("WEAPON"));
		assertNull(red.get("AMMO"));
		// ranged comes first on vorkath, but only the lance is a dragonbane
		assertEquals(id("Dragon hunter lance"),
			(int) taskSetup("vorkath", "Dragon hunter lance", "Rune crossbow", "Runite bolts").get("WEAPON"));
		// a stab weapon of the melee tab is still the vampyre bane
		assertEquals(id("Sunspear"), (int) taskSetup("vampyres", "Abyssal whip", "Sunspear").get("WEAPON"));
		// every Krystilia task is in the Wilderness
		TabBuilder.Options options = new TabBuilder.Options();
		options.slayerTask = new SlayerTask("hill giants", null, true);
		assertEquals(id("Ursine chainmace"), (int) bySlot(section(builder.build(tab("slayer"),
			own("Abyssal whip", "Ursine chainmace"), options), "ontask_main")).get("WEAPON"));
	}

	@Test
	public void onlyAStyleThatCanHurtTheMonsterIsChosen()
	{
		// the whip can't hurt turoth; the shortbow with broad arrows can
		Map<String, Integer> turoth = taskSetup("turoth", "Abyssal whip", "Magic shortbow", "Broad arrows");
		assertEquals(id("Magic shortbow"), (int) turoth.get("WEAPON"));
		assertEquals(id("Broad arrows"), (int) turoth.get("AMMO"));
		TabBuilder.Built kurask = slayerOnTask("kurask", "Abyssal whip", "Leaf-bladed sword", "Broad arrows");
		Map<String, Integer> setup = bySlot(section(kurask, "ontask_main"));
		assertEquals(id("Leaf-bladed sword"), (int) setup.get("WEAPON"));
		assertNull("no arrows in a melee set", setup.get("AMMO"));
		assertTrue(items(section(kurask, "ontask_alts")).contains(id("Broad arrows")));
	}

	@Test
	public void aDaggerIsNoMagicWeapon()
	{
		assertEquals(id("Rune crossbow"), (int) taskSetup("dust devils", "Abyssal whip", "Dragon dagger", "Rune crossbow",
			"Runite bolts", "Slayer helmet (i)").get("WEAPON"));
	}

	@Test
	public void aSalveThatDoesNoBetterThanTheHelmetStaysOff()
	{
		// the bonuses don't stack: a plain salve only matches the helmet
		assertEquals(id("Amulet of strength"), (int) taskSetup("aberrant spectres", "Abyssal whip", "Slayer helmet (i)",
			"Salve amulet", "Amulet of strength").get("AMULET"));
	}

	@Test
	public void aMagicSetupBringsItsRunes()
	{
		Set<Integer> supplies = items(section(slayerOnTask("smoke devils", "Slayer helmet (i)", "Kodai wand",
			"Blood rune", "Death rune", "Rune pouch"), "ontask_supplies"));
		assertTrue(supplies.contains(id("Blood rune")));
		assertTrue(supplies.contains(id("Death rune")));
	}

	@Test
	public void kreearrasGrappleIsCarriedNotWorn()
	{
		TabBuilder.Built built = slayerOnTask("kree'arra", "Twisted bow", "Dragon arrow", "Armadyl crossbow",
			"Mith grapple");
		Map<String, Integer> setup = bySlot(section(built, "ontask_main"));
		assertEquals(id("Twisted bow"), (int) setup.get("WEAPON"));
		assertEquals(id("Dragon arrow"), (int) setup.get("AMMO"));
		assertTrue(items(section(built, "ontask_items")).contains(id("Mith grapple")));
	}

	@Test
	public void finishedGoodsGoLast()
	{
		TabBuilder.Built wc = build("woodcutting", own("Rune axe", "Logs", "Oak logs"));
		List<TabBuilder.Section> sections = wc.getSections();
		TabBuilder.Section last = sections.stream().filter(s -> !s.getItems().isEmpty())
			.reduce((a, b) -> b).orElseThrow(IllegalStateException::new);
		assertEquals("goods", last.getLayout());
		assertEquals(Arrays.asList(id("Logs"), id("Oak logs")),
			last.getItems().stream().map(TabBuilder.Placement::getItemId).collect(Collectors.toList()));

		TabBuilder.Options off = new TabBuilder.Options();
		off.skillGoods = false;
		assertFalse(builder.build(tab("woodcutting"), own("Rune axe", "Logs"), off).itemIds().contains(id("Logs")));
	}

	@Test
	public void everyUsefulBoltAndBooksAreSituational()
	{
		TabBuilder.Built built = build("ranged", own("Rune crossbow", "Runite bolts", "Broad bolts", "Rune kiteshield",
			"Book of Law"));
		Map<String, Integer> main = bySlot(section(built, "main"));
		assertEquals(id("Rune crossbow"), (int) main.get("WEAPON"));
		assertEquals("a real shield, not the prayer book", id("Rune kiteshield"), (int) main.get("SHIELD"));
		assertTrue(items(section(built, "situational")).contains(id("Book of Law")));
		assertTrue("broad bolts show beside the runite bolts", built.itemIds().containsAll(own("Runite bolts", "Broad bolts")));
	}

	@Test
	public void prayerGearIsSituationalNotBestInSlot()
	{
		TabBuilder.Built built = build("melee", own("Abyssal whip", "Ardougne cloak 4", "Cape of Legends"));
		Map<String, Integer> main = bySlot(section(built, "main"));
		assertEquals("a teleport cape with prayer is not the best melee cape", id("Cape of Legends"),
			(int) main.get("CAPE"));
		assertTrue("but it is the situational prayer cape", items(section(built, "situational"))
			.contains(id("Ardougne cloak 4")));
		assertTrue(built.reasons().get(id("Ardougne cloak 4")).contains("+6 prayer"));
	}

	@Test
	public void blightedSuppliesSitApartBelowTheSupplies()
	{
		Set<Integer> owned = own("Tumeken's shadow", "Super restore", "Shark", "Blighted super restore",
			"Blighted anglerfish", "Blighted manta ray", "Blighted karambwan", "Blighted ancient ice sack",
			"Blighted vengeance sack", "Unpowered orb");
		TabBuilder.Built magic = build("magic", owned);
		List<String> keys = magic.getSections().stream().map(TabBuilder.Section::getKey).collect(Collectors.toList());
		assertTrue(keys.indexOf("supplies") < keys.indexOf("blighted"));
		assertTrue(keys.indexOf("blighted") < keys.indexOf("blighted_sacks"));
		assertTrue(keys.indexOf("blighted_sacks") < keys.indexOf("orbs"));
		assertEquals(own("Super restore"), items(section(magic, "supplies")));
		assertEquals(own("Shark"), items(section(magic, "supplies_food")));
		Set<Integer> blighted = items(section(magic, "blighted"));
		assertTrue(blighted.containsAll(own("Blighted anglerfish", "Blighted manta ray", "Blighted karambwan")));
		assertEquals(own("Blighted ancient ice sack", "Blighted vengeance sack"), items(section(magic, "blighted_sacks")));
		assertTrue(blighted.contains(id("Blighted super restore")));
		assertTrue(magic.reasons().get(id("Blighted manta ray")).startsWith("Wilderness/PvP only"));

		int[] layout = LayoutPlanner.plan(magic, false);
		int shark = row(layout, id("Shark"));
		int food = row(layout, id("Blighted manta ray"));
		int sacks = row(layout, id("Blighted ancient ice sack"));
		assertEquals("a blank row between the supplies and the blighted ones", shark + 2, food);
		assertEquals("the spell sacks get their own row", food + 1, sacks);
		assertEquals("the orbs keep their blank row", sacks + 2, row(layout, id("Unpowered orb")));

		TabBuilder.Built melee = build("melee", owned);
		assertTrue(items(section(melee, "blighted")).contains(id("Blighted manta ray")));
		assertTrue("spell sacks are for the magic tab", melee.getSections().stream()
			.noneMatch(s -> s.getKey().equals("blighted_sacks")));

		TabBuilder.Options off = new TabBuilder.Options();
		off.blightedSupplies = false;
		TabBuilder.Built without = builder.build(tab("magic"), owned, off);
		assertTrue(without.getSections().stream().noneMatch(s -> s.getKey().startsWith("blighted")));
		assertTrue(items(section(without, "supplies_food")).contains(id("Shark")));
		off.blightedSupplies = true;
		off.combatSupplies = false;
		assertTrue("blighted supplies follow the combat supplies setting", builder.build(tab("magic"), owned, off)
			.getSections().stream().noneMatch(s -> s.getKey().startsWith("blighted")));
	}

	@Test
	public void onlyLitLightSourcesCount()
	{
		int unlit = 5014;
		int lit = 5013;
		assertTrue("an unlit mining helmet gives no light", build("slayer", new HashSet<>(Arrays.asList(unlit)))
			.itemIds().isEmpty());
		TabBuilder.Built built = build("slayer", new HashSet<>(Arrays.asList(lit)));
		assertTrue(built.itemIds().contains(lit));
		assertTrue(built.reasons().get(lit).contains("worn"));
		TabBuilder.Built kandarin = build("slayer", new HashSet<>(Arrays.asList(lit, id("Kandarin headgear 1"))));
		assertTrue("Kandarin headgear works carried and never goes out, so it is the head light",
			kandarin.itemIds().contains(id("Kandarin headgear 1")));
	}

	@Test
	public void magicRunesGetARowPerKindWithLawUnderFire()
	{
		TabBuilder.Built magic = build("magic", own("Tumeken's shadow", "Fire rune", "Water rune", "Law rune",
			"Cosmic rune", "Mind rune", "Death rune", "Blood rune", "Mist rune"));
		int[] layout = LayoutPlanner.plan(magic, false);
		int fire = pos(layout, id("Fire rune"));
		int law = pos(layout, id("Law rune"));
		assertEquals("law sits right under fire", fire + LayoutPlanner.WIDTH, law);
		assertEquals("cosmic right of law", law + 1, pos(layout, id("Cosmic rune")));
		int mind = pos(layout, id("Mind rune"));
		assertEquals("combat runes on their own row", row(layout, id("Law rune")) + 1, mind / LayoutPlanner.WIDTH);
		assertEquals(mind + 1, pos(layout, id("Death rune")));
		assertEquals("combination runes on their own row", mind / LayoutPlanner.WIDTH + 1,
			row(layout, id("Mist rune")));
	}

	static int pos(int[] layout, int itemId)
	{
		return row(layout, itemId) * LayoutPlanner.WIDTH + col(layout, itemId);
	}

	static int col(int[] layout, int itemId)
	{
		for (int i = 0; i < layout.length; i++)
		{
			if (layout[i] == itemId)
			{
				return i % LayoutPlanner.WIDTH;
			}
		}
		throw new AssertionError("not laid out: " + itemId);
	}

	static int row(int[] layout, int itemId)
	{
		for (int i = 0; i < layout.length; i++)
		{
			if (layout[i] == itemId)
			{
				return i / LayoutPlanner.WIDTH;
			}
		}
		throw new AssertionError("not laid out: " + itemId);
	}

	@Test
	public void emptyBankBuildsEmptyTabs()
	{
		for (TabData.Tab t : data.tabs)
		{
			assertTrue(t.key, builder.build(t, new HashSet<>(), new TabBuilder.Options()).isEmpty());
		}
	}
}
