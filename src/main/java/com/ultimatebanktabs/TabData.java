package com.ultimatebanktabs;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The bundled tab definitions (tabs.json, built by tools/generate_data.py).
 * Field names mirror the JSON; see the generator for what each mode means.
 */
class TabData
{
	int version;
	String generated;
	List<Integer> excludeAuto = Collections.emptyList();
	List<Integer> excludeMeleeWeapon = Collections.emptyList();
	Map<String, Double> meleeMultiplier = Collections.emptyMap();
	List<Integer> doubleAccuracy = Collections.emptyList();
	Map<String, String> launcherAmmo = Collections.emptyMap();
	Map<String, List<List<Integer>>> ammoClasses = Collections.emptyMap();
	List<Tab> tabs = Collections.emptyList();

	static class Tab
	{
		String key;
		String name;
		String kind;
		int icon;
		List<Section> sections = Collections.emptyList();

		boolean isGear()
		{
			return "gear".equals(kind);
		}
	}

	static class Section
	{
		String key;
		String title;
		/** equipment, alts or rows */
		String layout;
		/** generated from wiki recipes rather than curated */
		boolean supplies;
		List<Pick> picks = Collections.emptyList();
	}

	static class Pick
	{
		String key;
		/** first, all, auto, weapon, ammo, set, outfit */
		String mode;
		String slot;
		String profile;
		String style;
		String launcher;
		String fallback;
		boolean complete;
		/** requirements must already be in the tab rather than merely owned */
		boolean requiresPlaced;
		/** hover text: one per entry (may be shorter than entries), else the label */
		List<String> reasons = Collections.emptyList();
		String label;
		List<String> unless = Collections.emptyList();
		List<Integer> requires = Collections.emptyList();
		List<List<Integer>> entries = Collections.emptyList();
		List<Piece> pieces = Collections.emptyList();
		List<List<Piece>> variants = Collections.emptyList();
	}

	static class Piece
	{
		String slot;
		List<List<Integer>> entries = Collections.emptyList();
	}

	static TabData load(Gson gson)
	{
		try (InputStream in = TabData.class.getResourceAsStream("tabs.json"))
		{
			if (in == null)
			{
				throw new IllegalStateException("tabs.json missing from the plugin jar");
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				return gson.fromJson(reader, TabData.class);
			}
		}
		catch (IOException e)
		{
			throw new IllegalStateException("could not read tabs.json", e);
		}
	}
}
