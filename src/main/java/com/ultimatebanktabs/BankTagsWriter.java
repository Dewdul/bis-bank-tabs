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

	private final ConfigManager configManager;
	private final TagManager tagManager;
	private final LayoutManager layoutManager;
	private final BankTagsService bankTagsService;
	private final IntUnaryOperator canonicalize;

	/** tab key -> tag name, for every tab this plugin currently owns */
	private final Map<String, String> names = new LinkedHashMap<>();
	/** tag name -> items the tab shows (canonical ids) */
	private final Map<String, Set<Integer>> members = new HashMap<>();
	private final Map<String, int[]> written = new HashMap<>();
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
				String icon = String.valueOf(tab.icon);
				if (!icon.equals(configManager.getConfiguration(BANKTAGS_GROUP, ICON_PREFIX + name)))
				{
					configManager.setConfiguration(BANKTAGS_GROUP, ICON_PREFIX + name, icon);
				}
				if (!Arrays.equals(written.get(name), tab.layout) || layoutManager.loadLayout(name) == null)
				{
					layoutManager.saveLayout(new Layout(name, tab.layout));
					written.put(name, tab.layout);
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
	 * The player deleted or renamed one of our tabs in the bank: stop managing it
	 * and forget its name so it isn't recreated.
	 *
	 * @return the tab keys that were dropped
	 */
	List<String> onTagTabsEdited(String csv)
	{
		Set<String> present = new HashSet<>(Text.fromCSV(Objects.toString(csv, "")));
		List<String> gone = new ArrayList<>();
		for (Map.Entry<String, String> e : new ArrayList<>(names.entrySet()))
		{
			if (!present.contains(e.getValue()))
			{
				gone.add(e.getKey());
				tagManager.unregisterTag(e.getValue());
				members.remove(e.getValue());
				written.remove(e.getValue());
				names.remove(e.getKey());
				configManager.unsetConfiguration(UltimateBankTabsConfig.GROUP, NAME_PREFIX + e.getKey());
			}
		}
		return gone;
	}

	private void drop(String key, List<String> tagTabs)
	{
		String name = names.remove(key);
		configManager.unsetConfiguration(UltimateBankTabsConfig.GROUP, NAME_PREFIX + key);
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
			if (!tagTabs.contains(name) && !names.containsValue(name) && tagManager.getItemsForTag(name).isEmpty())
			{
				return name;
			}
		}
		return base + " (auto " + System.currentTimeMillis() + ")";
	}

	/** What one tab should show. */
	static class Tab
	{
		final Set<Integer> items;
		final int[] layout;
		final int icon;

		Tab(Set<Integer> items, int[] layout, int icon)
		{
			this.items = items;
			this.layout = layout;
			this.icon = icon;
		}
	}
}
