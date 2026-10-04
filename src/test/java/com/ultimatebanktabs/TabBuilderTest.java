package com.ultimatebanktabs;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
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
				(int) v[11], v[12])));
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
		assertEquals(id("Rada's blessing 4"), (int) main.get("AMMO"));
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
		assertTrue(supplies.containsAll(own("Super combat potion", "Saradomin brew", "Super restore", "Anglerfish",
			"Shark")));
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
	public void crystalBowTakesABlessing()
	{
		TabBuilder.Built built = build("ranged", own("Bow of Faerdhinen (c)", "Dragon arrow", "Holy blessing"));
		Map<String, Integer> main = bySlot(section(built, "main"));
		assertEquals(id("Holy blessing"), (int) main.get("AMMO"));
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
		assertTrue(items(section(built, "runes")).containsAll(own("Blood rune", "Death rune", "Rune pouch")));
	}

	@Test
	public void gracefulPicksTheFullestColour()
	{
		TabBuilder.Built built = build("movement", own("Graceful hood", "Graceful cape", "Graceful top", "Graceful legs",
			"Graceful gloves", "Graceful boots", "Graceful hood (Arceuus)", "Graceful top (Arceuus)", "Stamina potion",
			"Ring of dueling", "Amulet of glory", "Varrock teleport (tablet)", "Law rune"));
		Set<Integer> run = items(section(built, "run"));
		assertTrue(run.containsAll(own("Graceful hood", "Graceful cape", "Graceful top", "Graceful legs",
			"Graceful gloves", "Graceful boots", "Stamina potion")));
		assertFalse(built.itemIds().contains(id("Graceful hood (Arceuus)")));
		assertTrue(built.itemIds().containsAll(own("Ring of dueling", "Amulet of glory", "Varrock teleport (tablet)",
			"Law rune")));
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
	public void herbChainsLineUpInColumns()
	{
		TabBuilder.Built herb = build("herblore", own("Guam leaf", "Eye of newt", "Irit leaf", "Grimy ranarr weed",
			"Ranarr weed", "Ranarr potion (unf)", "Snape grass", "Prayer potion"));
		int[] layout = LayoutPlanner.plan(herb, false);
		int w = LayoutPlanner.WIDTH;
		// only herbs you have something for get a column: guam 0, ranarr 1, irit 2
		assertEquals(id("Grimy ranarr weed"), layout[1]);
		assertEquals(id("Guam leaf"), layout[w]);
		assertEquals(id("Ranarr weed"), layout[w + 1]);
		assertEquals(id("Irit leaf"), layout[w + 2]);
		// band rows: grimy, clean, secondary, unfinished, potion
		assertEquals("eye of newt right under guam", id("Eye of newt"), layout[2 * w]);
		assertEquals(id("Snape grass"), layout[2 * w + 1]);
		assertEquals("and under irit", id("Eye of newt"), layout[2 * w + 2]);
		assertEquals(id("Ranarr potion (unf)"), layout[3 * w + 1]);
		assertEquals(id("Prayer potion"), layout[4 * w + 1]);
		assertTrue(herb.reasons().get(id("Eye of newt")).contains("super attack"));
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
