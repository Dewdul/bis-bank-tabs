package com.ultimatebanktabs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;
import net.runelite.client.util.Text;

/**
 * Publishes built tabs through RuneLite's Bank Tags plugin.
 *
 * <p>Tab contents are served by an in-memory {@code BankTag} registered with
 * the TagManager, so no per-item tags are ever written; only the tab list,
 * tab icons and layouts go to the {@code banktags} config group. Tag names
 * that belong to the player are never reused: a taken name falls back to
 * "melee bis", "melee (auto)" and so on.
 */
@Slf4j
class BankTagsWriter
{
	static final String BANKTAGS_GROUP = "banktags";
	private static final String TAG_TABS = "tagtabs";
	private static final String ICON_PREFIX = "icon_";
	private static final String NAME_PREFIX = "tagName_";
	/** an icon the player picked with "Change icon": the plugin stops replacing it */
	static final String CUSTOM_ICON_PREFIX = "customIcon_";
	/** the player chose "Disable layout": the tab shows its items in bank order until they enable it again */
	static final String LAYOUT_OFF_PREFIX = "layoutOff_";
	/** the layout last planned for a tab, so a restart can tell the player's moves from the plan */
	static final String PLANNED_PREFIX = "planned_";

	private final ConfigManager configManager;
	private final TagManager tagManager;
	private final LayoutManager layoutManager;
	private final BankTagsService bankTagsService;
	private final IntUnaryOperator canonicalize;

	/** tab key -> tag name, for every tab this plugin currently owns */
	private final Map<String, String> names = new LinkedHashMap<>();
	/** tag name -> items the tab shows (canonical ids) */
	private final Map<String, Set<Integer>> members = new HashMap<>();
	/** tag name -> the layout last planned for it (before keeping the player's moves) */
	private final Map<String, int[]> written = new HashMap<>();
	/** the player's replaced tabs: never reused as names for ours */
	private final Set<String> reserved = new HashSet<>();
	private boolean writing;

	BankTagsWriter(ConfigManager configManager, TagManager tagManager, LayoutManager layoutManager,
		BankTagsService bankTagsService, IntUnaryOperator canonicalize)
	{
		this.configManager = configManager;
		this.tagManager = tagManager;
		this.layoutManager = layoutManager;
		this.bankTagsService = bankTagsService;
		this.canonicalize = canonicalize;
	}

	/** True while this class is changing banktags config itself. */
	boolean isWriting()
	{
		return writing;
	}

	/** Tag names of the tabs this plugin owns. */
	Collection<String> ownedTags()
	{
		return names.values();
	}

	String keyOf(String tag)
	{
		return names.entrySet().stream().filter(e -> e.getValue().equals(tag)).map(Map.Entry::getKey).findFirst().orElse(null);
	}

	String tagOf(String key)
	{
		return names.get(key);
	}

	/** Re-adopt the tag names chosen in earlier sessions. */
	void restore(Collection<String> tabKeys)
	{
		for (String key : tabKeys)
		{
			String name = configManager.getConfiguration(UltimateBankTabsConfig.GROUP, NAME_PREFIX + key);
			if (name != null && !name.isEmpty())
			{
				names.put(key, name);
				register(name);
				int[] planned = parseLayout(configManager.getConfiguration(UltimateBankTabsConfig.GROUP,
					PLANNED_PREFIX + key));
				if (planned != null)
				{
					written.put(name, planned);
				}
			}
		}
	}

	/**
	 * Make the bank show exactly these tabs (in this order for new ones).
	 *
	 * @param tabs   tab key -> what to show; tabs missing here are removed
	 * @param titles tab key -> display name used to name a new tag
	 * @return how many tabs were newly added to the tab list
	 */
	int publish(Map<String, Tab> tabs, Map<String, String> titles)
	{
		writing = true;
		try
		{
			List<String> tagTabs = new ArrayList<>(Text.fromCSV(
				Objects.toString(configManager.getConfiguration(BANKTAGS_GROUP, TAG_TABS), "")));
			List<String> before = new ArrayList<>(tagTabs);

			for (String key : new ArrayList<>(names.keySet()))
			{
				if (!tabs.containsKey(key))
				{
					drop(key, tagTabs);
				}
			}

			int added = 0;
			for (Map.Entry<String, Tab> e : tabs.entrySet())
			{
				String key = e.getKey();
				Tab tab = e.getValue();
				String name = names.get(key);
				if (name == null)
				{
					name = freeName(titles.getOrDefault(key, key), tagTabs);
					names.put(key, name);
					configManager.setConfiguration(UltimateBankTabsConfig.GROUP, NAME_PREFIX + key, name);
					register(name);
				}
				members.put(name, tab.items);
				if (!tagTabs.contains(name))
				{
					tagTabs.add(name);
					added++;
				}
				String custom = configManager.getConfiguration(UltimateBankTabsConfig.GROUP, CUSTOM_ICON_PREFIX + key);
				String icon = custom != null ? custom : String.valueOf(tab.icon);
				if (!icon.equals(configManager.getConfiguration(BANKTAGS_GROUP, ICON_PREFIX + name)))
				{
					configManager.setConfiguration(BANKTAGS_GROUP, ICON_PREFIX + name, icon);
				}
				boolean layoutOff = Boolean.parseBoolean(
					configManager.getConfiguration(UltimateBankTabsConfig.GROUP, LAYOUT_OFF_PREFIX + key));
				Layout saved = layoutManager.loadLayout(name);
				if (!layoutOff && (!Arrays.equals(written.get(name), tab.layout) || saved == null))
				{
					// keep the player's own arrangement; only a new best in slot takes its cell back
					int[] next = LayoutMerger.next(saved == null ? null : saved.getLayout(), written.get(name),
						tab.layout, tab.bis);
					layoutManager.saveLayout(new Layout(name, next));
					written.put(name, tab.layout);
					configManager.setConfiguration(UltimateBankTabsConfig.GROUP, PLANNED_PREFIX + key,
						joinLayout(tab.layout));
					if (name.equals(bankTagsService.getActiveTag()))
					{
						bankTagsService.openBankTag(name, BankTagsService.OPTION_ALLOW_MODIFICATIONS);
					}
				}
			}

			if (!tagTabs.equals(before))
			{
				configManager.setConfiguration(BANKTAGS_GROUP, TAG_TABS, Text.toCSV(tagTabs));
			}
			return added;
		}
		finally
		{
			writing = false;
		}
	}

	/** Remove every tab this plugin made (when the plugin is turned off). */
	void removeAll()
	{
		writing = true;
		try
		{
			List<String> tagTabs = new ArrayList<>(Text.fromCSV(
				Objects.toString(configManager.getConfiguration(BANKTAGS_GROUP, TAG_TABS), "")));
			List<String> before = new ArrayList<>(tagTabs);
			for (String key : new ArrayList<>(names.keySet()))
			{
				String name = names.get(key);
				if (name.equals(bankTagsService.getActiveTag()))
				{
					bankTagsService.closeBankTag();
				}
				tagTabs.remove(name);
				configManager.unsetConfiguration(BANKTAGS_GROUP, ICON_PREFIX + name);
				layoutManager.removeLayout(name);
			}
			if (!tagTabs.equals(before))
			{
				configManager.setConfiguration(BANKTAGS_GROUP, TAG_TABS, Text.toCSV(tagTabs));
			}
		}
		finally
		{
			writing = false;
		}
		unregisterAll();
	}

	/** The tabs in the bank that this plugin didn't make, in tab order. */
	List<String> otherTabs()
	{
		List<String> others = new ArrayList<>(Text.fromCSV(
			Objects.toString(configManager.getConfiguration(BANKTAGS_GROUP, TAG_TABS), "")));
		others.removeAll(names.values());
		return others;
	}

	/**
	 * Take every other tab out of the bank (the player agreed to this plugin
	 * replacing them). Items keep their tags, so "tag:" searches still work.
	 *
	 * @return the removed tabs with their icons and layouts, for {@link #restoreOthers}
	 */
	List<SavedTab> replaceOthers()
	{
		List<String> others = otherTabs();
		List<SavedTab> saved = new ArrayList<>();
		if (others.isEmpty())
		{
			return saved;
		}
		writing = true;
		try
		{
			for (String name : others)
			{
				Layout layout = layoutManager.loadLayout(name);
				saved.add(new SavedTab(name, configManager.getConfiguration(BANKTAGS_GROUP, ICON_PREFIX + name),
					layout == null ? null : layout.getLayout()));
				if (name.equals(bankTagsService.getActiveTag()))
				{
					bankTagsService.closeBankTag();
				}
				configManager.unsetConfiguration(BANKTAGS_GROUP, ICON_PREFIX + name);
				layoutManager.removeLayout(name);
			}
			configManager.setConfiguration(BANKTAGS_GROUP, TAG_TABS, Text.toCSV(new ArrayList<>(names.values())));
		}
		finally
		{
			writing = false;
		}
		reserved.addAll(others);
		return saved;
	}

	/** Put back tabs taken out by {@link #replaceOthers}, after any tabs still there. */
	void restoreOthers(List<SavedTab> saved)
	{
		writing = true;
		try
		{
			List<String> tagTabs = new ArrayList<>(Text.fromCSV(
				Objects.toString(configManager.getConfiguration(BANKTAGS_GROUP, TAG_TABS), "")));
			List<String> before = new ArrayList<>(tagTabs);
			for (SavedTab tab : saved)
			{
				if (tab.name == null || tagTabs.contains(tab.name))
				{
					continue;
				}
				tagTabs.add(tab.name);
				if (tab.icon != null && configManager.getConfiguration(BANKTAGS_GROUP, ICON_PREFIX + tab.name) == null)
				{
					configManager.setConfiguration(BANKTAGS_GROUP, ICON_PREFIX + tab.name, tab.icon);
				}
				if (tab.layout != null && layoutManager.loadLayout(tab.name) == null)
				{
					layoutManager.saveLayout(new Layout(tab.name, tab.layout));
				}
			}
			if (!tagTabs.equals(before))
			{
				configManager.setConfiguration(BANKTAGS_GROUP, TAG_TABS, Text.toCSV(tagTabs));
			}
		}
		finally
		{
			writing = false;
		}
	}

	/** Names a new tab of ours must not take: the replaced tabs come back under them. */
	void reserve(Collection<String> tags)
	{
		reserved.addAll(tags);
	}

	/** Forget the in-memory tags without touching config (plugin stop that keeps tabs). */
	void unregisterAll()
	{
		for (String name : names.values())
		{
			tagManager.unregisterTag(name);
		}
		names.clear();
		members.clear();
		written.clear();
	}

	/**
	 * The player edited the tab list in the bank. A rename (one of our tags gone
	 * and exactly one new tag added) moves the tab to its new name; a deletion
	 * stops managing that tab and forgets its name so it isn't recreated.
	 *
	 * @return the tab keys that were deleted (not renamed)
	 */
	List<String> onTagTabsEdited(String oldCsv, String newCsv)
	{
		List<String> before = Text.fromCSV(Objects.toString(oldCsv, ""));
		Set<String> present = new HashSet<>(Text.fromCSV(Objects.toString(newCsv, "")));
		List<String> added = new ArrayList<>(present);
		added.removeAll(before);
		added.removeAll(names.values());
		List<Map.Entry<String, String>> removed = new ArrayList<>();
		for (Map.Entry<String, String> e : names.entrySet())
		{
			if (!present.contains(e.getValue()))
			{
				removed.add(e);
			}
		}
		if (removed.size() == 1 && added.size() == 1)
		{
			rename(removed.get(0).getKey(), removed.get(0).getValue(), added.get(0));
			return new ArrayList<>();
		}
		List<String> gone = new ArrayList<>();
		for (Map.Entry<String, String> e : removed)
		{
			gone.add(e.getKey());
			tagManager.unregisterTag(e.getValue());
			members.remove(e.getValue());
			written.remove(e.getValue());
			names.remove(e.getKey());
			configManager.unsetConfiguration(UltimateBankTabsConfig.GROUP, NAME_PREFIX + e.getKey());
		}
		return gone;
	}

	/** Bank Tags already moved the icon and layout to the new name; follow it. */
	private void rename(String key, String oldName, String newName)
	{
		names.put(key, newName);
		configManager.setConfiguration(UltimateBankTabsConfig.GROUP, NAME_PREFIX + key, newName);
		tagManager.unregisterTag(oldName);
		Set<Integer> items = members.remove(oldName);
		if (items != null)
		{
			members.put(newName, items);
		}
		int[] layout = written.remove(oldName);
		if (layout != null)
		{
			written.put(newName, layout);
		}
		register(newName);
		log.debug("tab {} renamed {} -> {}", key, oldName, newName);
	}

	/** The player re-enabled layout mode: lay the tab out from the plan again on the next publish. */
	void forgetLayout(String name)
	{
		written.remove(name);
		String key = keyOf(name);
		if (key != null)
		{
			configManager.unsetConfiguration(UltimateBankTabsConfig.GROUP, PLANNED_PREFIX + key);
		}
	}

	private static String joinLayout(int[] layout)
	{
		StringBuilder sb = new StringBuilder();
		for (int id : LayoutMerger.trim(layout))
		{
			if (sb.length() > 0)
			{
				sb.append(',');
			}
			sb.append(id);
		}
		return sb.toString();
	}

	private static int[] parseLayout(String csv)
	{
		if (csv == null || csv.isEmpty())
		{
			return null;
		}
		try
		{
			return Arrays.stream(csv.split(",")).mapToInt(s -> Integer.parseInt(s.trim())).toArray();
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/** Whether the tab list currently holds this tag. */
	boolean listed(String name)
	{
		return Text.fromCSV(Objects.toString(configManager.getConfiguration(BANKTAGS_GROUP, TAG_TABS), "")).contains(name);
	}

	private void drop(String key, List<String> tagTabs)
	{
		String name = names.remove(key);
		configManager.unsetConfiguration(UltimateBankTabsConfig.GROUP, NAME_PREFIX + key);
		configManager.unsetConfiguration(UltimateBankTabsConfig.GROUP, PLANNED_PREFIX + key);
		if (name.equals(bankTagsService.getActiveTag()))
		{
			bankTagsService.closeBankTag();
		}
		tagTabs.remove(name);
		configManager.unsetConfiguration(BANKTAGS_GROUP, ICON_PREFIX + name);
		layoutManager.removeLayout(name);
		tagManager.unregisterTag(name);
		members.remove(name);
		written.remove(name);
	}

	private void register(String name)
	{
		tagManager.registerTag(name, itemId ->
		{
			Set<Integer> items = members.get(name);
			return items != null && items.contains(canonicalize.applyAsInt(itemId));
		});
	}

	/** First of "melee", "melee bis", "melee (auto)", "melee (auto 2)"... that the player isn't using. */
	private String freeName(String title, List<String> tagTabs)
	{
		String base = Text.standardize(title);
		List<String> candidates = new ArrayList<>(Arrays.asList(base, base + " bis", base + " (auto)"));
		for (int i = 2; i < 100; i++)
		{
			candidates.add(base + " (auto " + i + ")");
		}
		for (String name : candidates)
		{
			if (!tagTabs.contains(name) && !names.containsValue(name) && !reserved.contains(name)
				&& tagManager.getItemsForTag(name).isEmpty())
			{
				return name;
			}
		}
		return base + " (auto " + System.currentTimeMillis() + ")";
	}

	/** One of the player's own tabs, kept while this plugin has replaced it (stored as JSON). */
	static class SavedTab
	{
		String name;
		String icon;
		int[] layout;

		SavedTab(String name, String icon, int[] layout)
		{
			this.name = name;
			this.icon = icon;
			this.layout = layout;
		}
	}

	/** What one tab should show. */
	static class Tab
	{
		final Set<Integer> items;
		final int[] layout;
		final int icon;
		/** bank positions of the best-in-slot cells in the layout */
		final Set<Integer> bis;

		Tab(Set<Integer> items, int[] layout, int icon, Set<Integer> bis)
		{
			this.items = items;
			this.layout = layout;
			this.icon = icon;
			this.bis = bis;
		}
	}
}
