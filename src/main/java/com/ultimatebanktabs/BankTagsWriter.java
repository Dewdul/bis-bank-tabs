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
import java.util.TreeMap;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.plugins.banktags.BankTag;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;
import net.runelite.client.plugins.banktags.tabs.TagTab;
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
	/** where items that left a rearranged tab were, so they go back there when they return */
	static final String HOME_PREFIX = "home_";

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
	/** tag name -> whether the layout last planned for it had the equipment block */
	private final Map<String, Boolean> writtenBlock = new HashMap<>();
	/** tab key -> variant group -> the cell an item had when it left the tab */
	private final Map<String, Map<Integer, Integer>> homes = new HashMap<>();
	/** tabs that are on but empty for this account, kept out of the tab list */
	private final Set<String> hiddenKeys = new HashSet<>();
	/** the tab list (standardized) as Bank Tags last loaded or saved it; its own saves write this back */
	private Set<String> loaded;
	/** standardized tag -> the icon Bank Tags holds in memory (loaded at bank open, or the player's pick since) */
	private final Map<String, String> heldIcons = new HashMap<>();
	/** hidden tab key -> the layout this plugin left under its name when it hid the tab */
	private final Map<String, int[]> hiddenLayouts = new HashMap<>();
	/** the tab list as it last held this plugin's tabs, so a tab coming back takes its old place */
	static final String ORDER_KEY = "tabOrder";
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

	/** Bank Tags compares tag names standardized (a renamed tab may keep its capitals in the tab list). */
	String keyOf(String tag)
	{
		return tag == null ? null : names.entrySet().stream().filter(e -> same(e.getValue(), tag)).map(Map.Entry::getKey)
			.findFirst().orElse(null);
	}

	private static boolean same(String a, String b)
	{
		return a != null && b != null && Text.standardize(a).equals(Text.standardize(b));
	}

	private static String iconKey(String name)
	{
		return ICON_PREFIX + Text.standardize(name);
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
				String plannedValue = configManager.getConfiguration(UltimateBankTabsConfig.GROUP, PLANNED_PREFIX + key);
				int[] planned = parseLayout(plannedValue);
				if (planned != null)
				{
					written.put(name, planned);
					Boolean block = plannedBlock(plannedValue);
					if (block != null)
					{
						writtenBlock.put(name, block);
					}
				}
				homes.put(key, parseHomes(configManager.getConfiguration(UltimateBankTabsConfig.GROUP, HOME_PREFIX + key)));
			}
		}
	}

	/**
	 * Make the bank show exactly these tabs (in this order for new ones).
	 *
	 * @param tabs   tab key -> what to show; tabs missing here are removed
	 * @param hidden tabs that are on but have nothing to show for this account: out of the tab list,
	 *               but their name and layout are kept for when they fill again
	 * @param titles tab key -> display name used to name a new tag
	 * @return how many tabs were newly added to the tab list
	 */
	int publish(Map<String, Tab> tabs, Set<String> hidden, Map<String, String> titles)
	{
		writing = true;
		try
		{
			List<String> tagTabs = new ArrayList<>(Text.fromCSV(
				Objects.toString(configManager.getConfiguration(BANKTAGS_GROUP, TAG_TABS), "")));
			List<String> before = new ArrayList<>(tagTabs);

			for (String key : new ArrayList<>(names.keySet()))
			{
				if (hidden.contains(key))
				{
					hide(key, tagTabs);
				}
				else if (!tabs.containsKey(key))
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
				hiddenKeys.remove(key);
				hiddenLayouts.remove(key);
				final String tag = name;
				if (tagTabs.stream().noneMatch(t -> same(t, tag)))
				{
					tagTabs.add(placeOf(name, tagTabs), name);
					added++;
				}
				String custom = configManager.getConfiguration(UltimateBankTabsConfig.GROUP, CUSTOM_ICON_PREFIX + key);
				String icon = custom != null ? custom : String.valueOf(tab.icon);
				// started with the bank open: Bank Tags holds what the config had
				heldIcons.putIfAbsent(Text.standardize(name), configManager.getConfiguration(BANKTAGS_GROUP, iconKey(name)));
				if (!icon.equals(configManager.getConfiguration(BANKTAGS_GROUP, iconKey(name))))
				{
					configManager.setConfiguration(BANKTAGS_GROUP, iconKey(name), icon);
				}
				boolean layoutOff = Boolean.parseBoolean(
					configManager.getConfiguration(UltimateBankTabsConfig.GROUP, LAYOUT_OFF_PREFIX + key));
				Layout saved = layoutManager.loadLayout(name);
				if (!layoutOff && (!Arrays.equals(written.get(name), tab.layout) || saved == null))
				{
					// keep the player's own arrangement; only a new best in slot takes its cell back
					Map<Integer, Integer> home = homes.computeIfAbsent(key, k -> new HashMap<>());
					if (saved == null && !home.isEmpty())
					{
						// a fresh layout (tab deleted, or a layout from before): old remembered cells mean nothing here
						home.clear();
						configManager.unsetConfiguration(UltimateBankTabsConfig.GROUP, HOME_PREFIX + key);
					}
					String homeBefore = joinHomes(home);
					int[] next = LayoutMerger.next(saved == null ? null : saved.getLayout(), written.get(name),
						tab.layout, tab.rows, tab.bis, tab.block, writtenBlock.get(name), ItemVariationMapping::map, home);
					layoutManager.saveLayout(new Layout(name, next));
					written.put(name, tab.layout);
					writtenBlock.put(name, tab.block);
					configManager.setConfiguration(UltimateBankTabsConfig.GROUP, PLANNED_PREFIX + key,
						(tab.block ? "b:" : "n:") + joinLayout(tab.layout));
					if (!joinHomes(home).equals(homeBefore))
					{
						setOrUnset(HOME_PREFIX + key, joinHomes(home));
					}
					if (same(name, bankTagsService.getActiveTag()))
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
			rememberOrder(tagTabs);
			for (String key : new ArrayList<>(names.keySet()))
			{
				String name = names.get(key);
				if (same(name, bankTagsService.getActiveTag()))
				{
					bankTagsService.closeBankTag();
				}
				tagTabs.removeIf(t -> same(t, name));
				configManager.unsetConfiguration(BANKTAGS_GROUP, iconKey(name));
				// the layout stays (unused while the tab is out of the list), so the tab comes back as arranged
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
	List<SavedTab> replaceOthers(Collection<String> only)
	{
		List<String> others = otherTabs();
		if (only != null)
		{
			// just the tabs the player was asked about
			others.removeIf(t -> only.stream().noneMatch(o -> same(o, t)));
		}
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
				saved.add(new SavedTab(name, configManager.getConfiguration(BANKTAGS_GROUP, iconKey(name)),
					layout == null ? null : layout.getLayout()));
				if (same(name, bankTagsService.getActiveTag()))
				{
					bankTagsService.closeBankTag();
				}
				configManager.unsetConfiguration(BANKTAGS_GROUP, iconKey(name));
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
				if (tab.name == null)
				{
					continue;
				}
				if (tagTabs.stream().noneMatch(t -> same(t, tab.name)))
				{
					tagTabs.add(tab.name);
				}
				if (tab.icon != null && configManager.getConfiguration(BANKTAGS_GROUP, iconKey(tab.name)) == null)
				{
					configManager.setConfiguration(BANKTAGS_GROUP, iconKey(tab.name), tab.icon);
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
			unregister(name);
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
		// Bank Tags saves its in-memory list, loaded when the bank opened, so diff against that: a tab this
		// plugin added since then isn't in it, and isn't deleted by it
		Set<String> was = loaded != null ? loaded : standardized(Text.fromCSV(Objects.toString(oldCsv, "")));
		Map<String, String> now = new LinkedHashMap<>();
		for (String t : Text.fromCSV(Objects.toString(newCsv, "")))
		{
			now.putIfAbsent(Text.standardize(t), t);
		}
		loaded = new HashSet<>(now.keySet());

		// the player made their own tab with the name of one of ours that is hidden (empty): it's theirs now
		for (String key : new ArrayList<>(hiddenKeys))
		{
			String name = names.get(key);
			if (name != null && now.containsKey(Text.standardize(name)) && !was.contains(Text.standardize(name)))
			{
				Layout current = layoutManager.loadLayout(name);
				if (current != null && Arrays.equals(current.getLayout(), hiddenLayouts.get(key)))
				{
					// still the arrangement this plugin left (a New tab); an import or rename wrote the player's own
					layoutManager.removeLayout(name);
				}
				forget(key);
				hiddenKeys.remove(key);
				hiddenLayouts.remove(key);
			}
		}

		List<String> added = new ArrayList<>();
		for (Map.Entry<String, String> e : now.entrySet())
		{
			if (!was.contains(e.getKey()) && names.values().stream().noneMatch(n -> same(n, e.getKey())))
			{
				added.add(e.getValue());
			}
		}
		List<Map.Entry<String, String>> removed = new ArrayList<>();
		for (Map.Entry<String, String> e : new ArrayList<>(names.entrySet()))
		{
			if (hiddenKeys.contains(e.getKey()))
			{
				// already out of the list: a write without it (Bank Tags' or anyone's) deletes nothing
				continue;
			}
			String std = Text.standardize(e.getValue());
			if (was.contains(std) && !now.containsKey(std))
			{
				removed.add(e);
			}
			else if (now.containsKey(std) && !now.get(std).equals(e.getValue()))
			{
				// Bank Tags rewrote the spelling ("Melee Gear" -> "melee gear"): follow it
				rename(e.getKey(), e.getValue(), now.get(std));
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
			forget(e.getKey());
		}
		return gone;
	}

	/** Stop managing a tab: its name, plan and remembered cells (the player deleted it, or took its name). */
	private void forget(String key)
	{
		String name = names.remove(key);
		if (name != null)
		{
			unregister(name);
			members.remove(name);
			written.remove(name);
			writtenBlock.remove(name);
		}
		homes.remove(key);
		configManager.unsetConfiguration(UltimateBankTabsConfig.GROUP, NAME_PREFIX + key);
		configManager.unsetConfiguration(UltimateBankTabsConfig.GROUP, PLANNED_PREFIX + key);
		configManager.unsetConfiguration(UltimateBankTabsConfig.GROUP, HOME_PREFIX + key);
	}

	/** Bank Tags already moved the icon and layout to the new name; follow it. */
	private void rename(String key, String oldName, String newName)
	{
		String held = heldIcons.remove(Text.standardize(oldName));
		if (held != null)
		{
			// Bank Tags carries its in-memory (possibly stale) icon over to the new name
			heldIcons.put(Text.standardize(newName), held);
		}
		names.put(key, newName);
		configManager.setConfiguration(UltimateBankTabsConfig.GROUP, NAME_PREFIX + key, newName);
		unregister(oldName);
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
		Boolean block = writtenBlock.remove(oldName);
		if (block != null)
		{
			writtenBlock.put(newName, block);
		}
		register(newName);
		log.debug("tab {} renamed {} -> {}", key, oldName, newName);
	}

	/** The player re-enabled layout mode: lay the tab out from the plan again on the next publish. */
	void forgetLayout(String name)
	{
		String key = keyOf(name);
		if (key != null)
		{
			written.remove(names.get(key));
			writtenBlock.remove(names.get(key));
			homes.remove(key);
			configManager.unsetConfiguration(UltimateBankTabsConfig.GROUP, PLANNED_PREFIX + key);
			configManager.unsetConfiguration(UltimateBankTabsConfig.GROUP, HOME_PREFIX + key);
		}
	}

	private void setOrUnset(String key, String value)
	{
		if (value.isEmpty())
		{
			configManager.unsetConfiguration(UltimateBankTabsConfig.GROUP, key);
		}
		else
		{
			configManager.setConfiguration(UltimateBankTabsConfig.GROUP, key, value);
		}
	}

	private static String joinHomes(Map<Integer, Integer> home)
	{
		return new TreeMap<>(home).entrySet().stream().map(e -> e.getKey() + ":" + e.getValue())
			.collect(Collectors.joining(","));
	}

	private static Map<Integer, Integer> parseHomes(String csv)
	{
		Map<Integer, Integer> home = new HashMap<>();
		if (csv != null)
		{
			for (String pair : csv.split(","))
			{
				String[] kv = pair.split(":");
				try
				{
					home.put(Integer.parseInt(kv[0].trim()), Integer.parseInt(kv[1].trim()));
				}
				catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored)
				{
					// skip junk
				}
			}
		}
		return home;
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

	/** Whether a stored plan ("b:..." or "n:...") had the equipment block; null for plans stored before it was kept. */
	private static Boolean plannedBlock(String value)
	{
		return value == null || value.length() < 2 || value.charAt(1) != ':' ? null : value.charAt(0) == 'b';
	}

	private static int[] parseLayout(String csv)
	{
		if (csv != null && csv.length() > 1 && csv.charAt(1) == ':')
		{
			csv = csv.substring(2);
		}
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
		return Text.fromCSV(Objects.toString(configManager.getConfiguration(BANKTAGS_GROUP, TAG_TABS), "")).stream()
			.anyMatch(t -> same(t, name));
	}

	/** On but empty for this account: leave the tab list, keep everything else for when it fills again. */
	private void hide(String key, List<String> tagTabs)
	{
		String name = names.get(key);
		if (same(name, bankTagsService.getActiveTag()))
		{
			bankTagsService.closeBankTag();
		}
		if (hiddenKeys.add(key))
		{
			rememberOrder(tagTabs);
			Layout layout = layoutManager.loadLayout(name);
			hiddenLayouts.put(key, layout == null ? null : layout.getLayout());
		}
		tagTabs.removeIf(t -> same(t, name));
		members.put(name, new HashSet<>());
	}

	/** Remember where this plugin's tabs sit in the list, for when they come back. */
	private void rememberOrder(List<String> tagTabs)
	{
		if (tagTabs.stream().noneMatch(t -> names.values().stream().anyMatch(n -> same(n, t))))
		{
			return;
		}
		// tabs of ours already out of the list (hidden, or removed when the plugin stopped) keep their old place
		List<String> merged = new ArrayList<>(tagTabs);
		int at = 0;
		for (String t : Text.fromCSV(Objects.toString(
			configManager.getConfiguration(UltimateBankTabsConfig.GROUP, ORDER_KEY), "")))
		{
			int i = -1;
			for (int k = 0; k < merged.size(); k++)
			{
				if (same(merged.get(k), t))
				{
					i = k;
					break;
				}
			}
			if (i >= 0)
			{
				at = i + 1;
			}
			else if (names.values().stream().anyMatch(n -> same(n, t)))
			{
				merged.add(at++, t);
			}
		}
		configManager.setConfiguration(UltimateBankTabsConfig.GROUP, ORDER_KEY, Text.toCSV(merged));
	}

	/** Where a tab coming back goes: right after the tab before it in the remembered order, else at the end. */
	private int placeOf(String name, List<String> tagTabs)
	{
		List<String> order = Text.fromCSV(Objects.toString(
			configManager.getConfiguration(UltimateBankTabsConfig.GROUP, ORDER_KEY), ""));
		int i = -1;
		for (int k = 0; k < order.size(); k++)
		{
			if (same(order.get(k), name))
			{
				i = k;
			}
		}
		if (i < 0)
		{
			return tagTabs.size();
		}
		for (int k = i - 1; k >= 0; k--)
		{
			for (int t = 0; t < tagTabs.size(); t++)
			{
				if (same(tagTabs.get(t), order.get(k)))
				{
					return t + 1;
				}
			}
		}
		return 0;
	}

	/** Bank Tags just loaded its tab list (bank opened): its own saves will write this list back. */
	void bankOpened()
	{
		loaded = standardized(Text.fromCSV(Objects.toString(configManager.getConfiguration(BANKTAGS_GROUP, TAG_TABS), "")));
		heldIcons.clear();
		for (String name : names.values())
		{
			heldIcons.put(Text.standardize(name), configManager.getConfiguration(BANKTAGS_GROUP, iconKey(name)));
		}
	}

	/** Bank Tags wrote back the icon it holds in memory (stale since this plugin changed it), not a new pick. */
	boolean isOurIcon(String tag, String icon)
	{
		return icon.equals(heldIcons.get(Text.standardize(tag)));
	}

	/** On but empty: out of the tab list, so Bank Tags holds no tab of ours under its name. */
	boolean isHidden(String key)
	{
		return hiddenKeys.contains(key);
	}

	/** At startup (maybe at an open bank): Bank Tags' own tab list and icons are what its next save writes back. */
	void bankHolds(Collection<TagTab> tabs)
	{
		loaded = tabs.stream().map(t -> Text.standardize(t.getTag())).collect(Collectors.toSet());
		heldIcons.clear();
		for (TagTab t : tabs)
		{
			heldIcons.put(Text.standardize(t.getTag()), String.valueOf(t.getIconItemId()));
		}
	}

	/** The player picked this icon with Change icon: Bank Tags holds it now. */
	void iconPicked(String tag, String icon)
	{
		heldIcons.put(Text.standardize(tag), icon);
	}

	private static Set<String> standardized(Collection<String> tags)
	{
		return tags.stream().map(Text::standardize).collect(Collectors.toSet());
	}

	private void drop(String key, List<String> tagTabs)
	{
		String name = names.remove(key);
		configManager.unsetConfiguration(UltimateBankTabsConfig.GROUP, NAME_PREFIX + key);
		configManager.unsetConfiguration(UltimateBankTabsConfig.GROUP, PLANNED_PREFIX + key);
		configManager.unsetConfiguration(UltimateBankTabsConfig.GROUP, HOME_PREFIX + key);
		homes.remove(key);
		if (same(name, bankTagsService.getActiveTag()))
		{
			bankTagsService.closeBankTag();
		}
		tagTabs.removeIf(t -> same(t, name));
		configManager.unsetConfiguration(BANKTAGS_GROUP, iconKey(name));
		layoutManager.removeLayout(name);
		unregister(name);
		members.remove(name);
		written.remove(name);
		writtenBlock.remove(name);
	}

	private void register(String name)
	{
		BankTag tag = itemId ->
		{
			Set<Integer> items = members.get(name);
			return items != null && items.contains(canonicalize.applyAsInt(itemId));
		};
		tagManager.registerTag(name, tag);
		if (!Text.standardize(name).equals(name))
		{
			// a renamed tab keeps its capitals; Bank Tags may look it up standardized
			tagManager.registerTag(Text.standardize(name), tag);
		}
	}

	private void unregister(String name)
	{
		tagManager.unregisterTag(name);
		tagManager.unregisterTag(Text.standardize(name));
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
			if (tagTabs.stream().noneMatch(t -> same(t, name)) && names.values().stream().noneMatch(n -> same(n, name))
				&& reserved.stream().noneMatch(r -> same(r, name))
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
		/** the layout has the equipment block (rows 0-4) */
		final boolean block;
		/** the section each row of the layout belongs to */
		final String[] rows;

		Tab(Set<Integer> items, LayoutPlanner.Plan plan, int icon, Set<Integer> bis, boolean block)
		{
			this.items = items;
			this.layout = plan.layout;
			this.rows = plan.rows;
			this.icon = icon;
			this.bis = bis;
			this.block = block;
		}
	}
}
