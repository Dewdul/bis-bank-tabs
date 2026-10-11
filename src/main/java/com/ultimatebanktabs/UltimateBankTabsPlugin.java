package com.ultimatebanktabs;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;
import net.runelite.client.plugins.banktags.tabs.TabManager;
import net.runelite.client.plugins.itemstats.Combo;
import net.runelite.client.plugins.itemstats.Effect;
import net.runelite.client.plugins.itemstats.FoodBase;
import net.runelite.client.plugins.itemstats.ItemStatChangesService;
import net.runelite.client.plugins.itemstats.ItemStatPlugin;
import net.runelite.client.plugins.itemstats.StatChange;
import net.runelite.client.plugins.itemstats.stats.Stats;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDependency(BankTagsPlugin.class)
@PluginDependency(ItemStatPlugin.class)
@PluginDescriptor(
	name = "No Brain Bank Tabs",
	description = "Automatic bank tag tabs with your best-in-slot gear for each combat style, movement and every skill,"
		+ " laid out for you",
	tags = {"bank", "tags", "tabs", "bis", "best in slot", "gear", "layout", "skilling", "teleports", "organize"}
)
public class UltimateBankTabsPlugin extends Plugin
{
	private static final String OWNED_KEY = "owned";
	/** the player agreed to this plugin replacing their bank tabs */
	private static final String ACCEPTED_KEY = "replaceAccepted";
	/** the tabs it replaced (JSON), for "Bring back replaced tabs" */
	private static final String REPLACED_KEY = "replacedTabs";
	private static final String RESTORE_KEY = "restoreTabs";
	/** the player agreed with the bank open: the takeover waits for the next bank open */
	private static final String REPLACE_PENDING_KEY = "replacePending";
	/** the food count default went from 4 to 6 */
	private static final String SIX_FOODS_KEY = "sixFoods";
	private static final Set<String> INTERNAL_KEYS = new HashSet<>(Arrays.asList(OWNED_KEY, ACCEPTED_KEY, REPLACED_KEY,
		REPLACE_PENDING_KEY, SIX_FOODS_KEY));
	private static final Type SAVED_TABS = new TypeToken<List<BankTagsWriter.SavedTab>>()
	{
	}.getType();
	/** Hub plugins that fight over the same tabs: internal name, display name, what they do. */
	private static final String[][] CONFLICTS = {
		{"bank-skill-sorting", "Auto Bank Sorter", "makes its own skill tabs"},
		{"bank-assistant", "Bank Assistant", "re-lays out the bank its own way"},
		{"bank-sorter", "Bank Sorter", "re-sorts the items inside tabs"},
		{"sorted-bank", "Sorted Bank", "re-sorts the items inside tabs"},
	};

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private UltimateBankTabsConfig config;

	@Inject
	private ItemManager itemManager;

	@Inject
	private TagManager tagManager;

	@Inject
	private LayoutManager layoutManager;

	@Inject
	private BankTagsService bankTagsService;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private Gson gson;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ReasonOverlay reasonOverlay;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private TabManager tabManager;

	@Inject
	private ItemStatChangesService itemStatChanges;

	/** item id -> HP one eat heals the player, for this rebuild */
	private final Map<Integer, Integer> healCache = new HashMap<>();

	private TabData data;
	private TabBuilder builder;
	private BankTagsWriter writer;
	private SkillIcons skillIcons;
	/** tag name -> skill sprite, for the tabs that show one */
	private Map<String, Integer> tabSprites = new HashMap<>();
	/** nothing is built until the player agrees to this plugin replacing their tabs */
	private boolean accepted;
	/** the player's current slayer task, or null */
	private SlayerTask slayerTask;
	/** conflicting plugins already mentioned this session */
	private final Set<String> warned = new HashSet<>();
	/** tab key -> item id -> why the item is in that tab */
	private volatile Map<String, Map<Integer, String>> reasons = new HashMap<>();

	/** canonical ids in the bank (items and placeholders) and potion storage */
	private Set<Integer> stored = new HashSet<>();
	private Set<Integer> potions = new HashSet<>();
	private boolean bankOpen;
	private boolean dirty;
	private boolean potionsDirty;

	@Provides
	UltimateBankTabsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(UltimateBankTabsConfig.class);
	}

	@Override
	protected void startUp()
	{
		data = TabData.load(gson);
		// six foods became the default: a stored four is the old default (RuneLite saves defaults), not a choice
		if (configManager.getConfiguration(UltimateBankTabsConfig.GROUP, SIX_FOODS_KEY) == null)
		{
			if (config.foodCount() == 4)
			{
				configManager.setConfiguration(UltimateBankTabsConfig.GROUP, "foodCount", 6);
			}
			configManager.setConfiguration(UltimateBankTabsConfig.GROUP, SIX_FOODS_KEY, true);
		}
		builder = new TabBuilder(data, id -> EquipStats.of(itemManager.getItemStats(id)), this::heals);
		writer = new BankTagsWriter(configManager, tagManager, layoutManager, bankTagsService, itemManager::canonicalize);
		writer.restore(data.tabs.stream().map(t -> t.key).collect(Collectors.toList()));
		// what Bank Tags holds now (the bank may be open); BANKMAIN_INIT refreshes it on every bank open
		writer.bankHolds(new ArrayList<>(tabManager.getTabs()));
		writer.reserve(replacedTabs().stream().map(t -> t.name).collect(Collectors.toList()));
		skillIcons = new SkillIcons(client);
		accepted = Boolean.parseBoolean(configManager.getConfiguration(UltimateBankTabsConfig.GROUP, ACCEPTED_KEY));
		overlayManager.add(reasonOverlay);
		BankTagsWriter w = writer;
		clientThread.invokeLater(() ->
		{
			bankOpen = client.getWidget(InterfaceID.Bankmain.UNIVERSE) != null;
			if (!accepted)
			{
				askToReplace(w);
				return;
			}
			if (!bankOpen)
			{
				replacePending();
			}
			if (!bankOpen && config.restoreTabs())
			{
				restoreReplaced(false);
			}
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				loadSnapshot();
				slayerTask = SlayerTask.read(client);
				rebuild(false);
			}
		});
	}

	/**
	 * First start: this plugin takes over the bank's tabs. The player's own
	 * tabs are removed (and backed up) only once they agree; saying no turns
	 * the plugin back off.
	 */
	private void askToReplace(BankTagsWriter w)
	{
		List<String> others = w.otherTabs();
		if (others.isEmpty())
		{
			accept(w, others);
			return;
		}
		List<String> conflicts = conflicts();
		StringBuilder msg = new StringBuilder("<html><body style='width: 340px'>")
			.append("<b>No Brain Bank Tabs replaces your bank tabs with its own.</b><br><br>")
			.append("Your ").append(others.size()).append(others.size() == 1 ? " tab" : " tabs")
			.append(" will be removed from the bank: <i>")
			.append(others.stream().limit(12).collect(Collectors.joining(", ")))
			.append(others.size() > 12 ? ", ..." : "").append("</i><br><br>")
			.append("Items keep their tags, so tag: searches still work, and a backup is kept: <b>Bring back")
			.append(" replaced tabs</b> in this plugin's settings puts them back.");
		if (!conflicts.isEmpty())
		{
			msg.append("<br><br><b>Also on:</b> ").append(String.join("; ", conflicts))
				.append(". Turn ").append(conflicts.size() == 1 ? "it" : "them").append(" off so it doesn't fight over your tabs.");
		}
		msg.append("</body></html>");
		String[] options = {"Replace my tabs", "Turn plugin off"};
		SwingUtilities.invokeLater(() ->
		{
			// owned by the game window so it can't open behind it
			int choice = JOptionPane.showOptionDialog(client.getCanvas(), msg.toString(), "No Brain Bank Tabs",
				JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
			if (choice == 0)
			{
				clientThread.invokeLater(() -> accept(w, others));
			}
			else if (writer == w)
			{
				turnOff();
			}
		});
	}

	/** @param agreed the player's tabs the question listed: only these are replaced */
	private void accept(BankTagsWriter w, List<String> agreed)
	{
		if (writer != w)
		{
			// turned off while the question was open
			return;
		}
		configManager.setConfiguration(UltimateBankTabsConfig.GROUP, ACCEPTED_KEY, true);
		accepted = true;
		bankOpen = client.getWidget(InterfaceID.Bankmain.UNIVERSE) != null;
		if (agreed.isEmpty())
		{
			// nothing of theirs to replace
		}
		else if (bankOpen)
		{
			// Bank Tags holds the open bank's tab list and would write the old tabs back: finish on the next open
			configManager.setConfiguration(UltimateBankTabsConfig.GROUP, REPLACE_PENDING_KEY, Text.toCSV(agreed));
			chat("No Brain Bank Tabs: close your bank and open it again to finish replacing your tabs.");
		}
		else
		{
			replaceNow(agreed);
		}
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			loadSnapshot();
			rebuild(false);
		}
	}

	/** A takeover the player agreed to with the bank open, done now (Bank Tags is between bank opens). */
	private void replacePending()
	{
		String pending = configManager.getConfiguration(UltimateBankTabsConfig.GROUP, REPLACE_PENDING_KEY);
		if (pending != null && !pending.isEmpty())
		{
			// "true": stored before the agreed tabs were kept, meaning all of them
			replaceNow("true".equals(pending) ? null : Text.fromCSV(pending));
		}
	}

	/** Take the player's own tabs out (backed up), now that Bank Tags isn't holding an open bank's list. */
	private void replaceNow(List<String> agreed)
	{
		configManager.unsetConfiguration(UltimateBankTabsConfig.GROUP, REPLACE_PENDING_KEY);
		List<BankTagsWriter.SavedTab> saved = writer.replaceOthers(agreed);
		if (!saved.isEmpty())
		{
			List<BankTagsWriter.SavedTab> all = replacedTabs();
			all.addAll(saved);
			configManager.setConfiguration(UltimateBankTabsConfig.GROUP, REPLACED_KEY, gson.toJson(all, SAVED_TABS));
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				chat("No Brain Bank Tabs: replaced your " + saved.size() + " bank tabs. Bring back replaced tabs in the"
					+ " plugin settings puts them back.");
			}
		}
	}

	private void turnOff()
	{
		pluginManager.setPluginEnabled(this, false);
		try
		{
			pluginManager.stopPlugin(this);
		}
		catch (PluginInstantiationException e)
		{
			log.warn("could not turn No Brain Bank Tabs off", e);
		}
	}

	private List<BankTagsWriter.SavedTab> replacedTabs()
	{
		String json = configManager.getConfiguration(UltimateBankTabsConfig.GROUP, REPLACED_KEY);
		List<BankTagsWriter.SavedTab> tabs = json == null || json.isEmpty() ? null : gson.fromJson(json, SAVED_TABS);
		return tabs == null ? new ArrayList<>() : new ArrayList<>(tabs);
	}

	/** "Bring back replaced tabs": put the player's old tabs back next to ours. */
	private void restoreReplaced(boolean atBankInit)
	{
		if (writer == null)
		{
			return;
		}
		if (bankOpen && !atBankInit)
		{
			// Bank Tags holds the open bank's tab list and would write it back over the restored tabs; the
			// setting stays on until the next bank open does it
			chat("No Brain Bank Tabs: close your bank and open it again to bring back your replaced tabs.");
			return;
		}
		List<BankTagsWriter.SavedTab> saved = replacedTabs();
		writer.restoreOthers(saved);
		configManager.unsetConfiguration(UltimateBankTabsConfig.GROUP, REPLACED_KEY);
		configManager.setConfiguration(UltimateBankTabsConfig.GROUP, RESTORE_KEY, false);
		chat(saved.isEmpty() ? "No Brain Bank Tabs: there are no replaced tabs to bring back."
			: "No Brain Bank Tabs: brought back " + saved.size() + " tabs."
				+ (atBankInit ? "" : " Open your bank to see them."));
	}

	/** Enabled plugins that fight over the same tabs, as "Name (what it does)". */
	private List<String> conflicts()
	{
		List<String> found = new ArrayList<>();
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (plugin == this || !pluginManager.isPluginEnabled(plugin))
			{
				continue;
			}
			String internal = ExternalPluginManager.getInternalName(plugin.getClass());
			PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
			String name = descriptor == null ? null : descriptor.name();
			for (String[] c : CONFLICTS)
			{
				if (c[0].equals(internal) || c[1].equalsIgnoreCase(name))
				{
					found.add(c[1] + " (" + c[2] + ")");
				}
			}
		}
		return found;
	}

	private void warnConflicts()
	{
		for (String conflict : conflicts())
		{
			if (warned.add(conflict))
			{
				chat("No Brain Bank Tabs: " + conflict + " is also on. Turn one of them off so they don't fight over"
					+ " your bank tabs.");
			}
		}
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(reasonOverlay);
		reasons = new HashMap<>();
		tabSprites = new HashMap<>();
		warned.clear();
		BankTagsWriter w = writer;
		boolean remove = config.removeOnStop();
		clientThread.invokeLater(() ->
		{
			if (remove)
			{
				w.removeAll();
			}
			else
			{
				w.unregisterAll();
			}
		});
		// an open "replace your tabs?" question checks this before acting
		writer = null;
		accepted = false;
		stored = new HashSet<>();
		potions = new HashSet<>();
		bankOpen = false;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			loadSnapshot();
			dirty = true;
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			stored = new HashSet<>();
			potions = new HashSet<>();
		}
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			// the server sends the task again after logging in
			slayerTask = null;
		}
	}

	/** A new slayer task (or none): the Slayer tab follows it. */
	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (SlayerTask.affects(event.getVarpId(), event.getVarbitId()))
		{
			// later, so every value of the same server update has landed (as the core Slayer plugin does)
			clientThread.invokeLater(() ->
			{
				SlayerTask now = SlayerTask.read(client);
				if (!Objects.equals(now, slayerTask))
				{
					slayerTask = now;
					dirty = true;
				}
			});
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			bankOpen = true;
			potionsDirty = true;
			dirty = true;
			if (accepted)
			{
				warnConflicts();
			}
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN && event.isUnload())
		{
			bankOpen = false;
		}
	}

	/**
	 * Bank Tags loads its tab list when the bank opens. Running first (higher
	 * priority) lets tabs that just became non-empty show up in this same open.
	 */
	@Subscribe(priority = 1)
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_INIT)
		{
			ItemContainer bank = client.getItemContainer(InventoryID.BANK);
			if (bank != null)
			{
				readBank(bank);
				// tabs added here are picked up by this very bank open, so no "reopen" hint
				rebuild(false);
			}
			if (writer != null && accepted)
			{
				replacePending();
			}
			if (writer != null && config.restoreTabs())
			{
				restoreReplaced(true);
			}
			if (writer != null)
			{
				// Bank Tags loads the tab list right after this
				writer.bankOpened();
			}
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.BANK)
		{
			readBank(event.getItemContainer());
			potionsDirty = true;
			dirty = true;
		}
		else if ((id == InventoryID.INV || id == InventoryID.WORN) && (bankOpen || config.countCarried()))
		{
			// potions moved between the inventory and potion storage show up here too
			potionsDirty |= bankOpen;
			dirty = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (potionsDirty && bankOpen)
		{
			potionsDirty = false;
			readPotionStorage();
		}
		if (dirty)
		{
			rebuild(bankOpen);
		}
	}

	/**
	 * Bank Tags' Remove-tag can't take an item out of these tabs (the plugin decides what they hold), but it
	 * does take it out of the layout, after which Bank Tags drops it in the first empty cell for good.
	 */
	@Subscribe(priority = -1)
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		// Bank Tags adds Remove-tag with createMenuEntry, which posts no event of its own, right after the game's
		// Examine (Withdraw-All-but-1 in potion storage), so strip it on that entry's event
		String option = event.getOption();
		if (writer == null || !("Examine".equals(option) || "Withdraw-All-but-1".equals(option))
			|| writer.keyOf(bankTagsService.getActiveTag()) == null)
		{
			return;
		}
		for (MenuEntry entry : client.getMenu().getMenuEntries())
		{
			if (entry.getOption().startsWith("Remove-tag"))
			{
				client.getMenu().removeMenuEntry(entry);
			}
		}
	}

	/** Bank Tags rebuilds and scrolls its tab widgets at will, so skill icons go back on before every frame. */
	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		if (bankOpen && writer != null && !writer.ownedTags().isEmpty())
		{
			skillIcons.paint(tabSprites, this::itemIcon);
		}
	}

	private int itemIcon(String tag)
	{
		try
		{
			return Integer.parseInt(Objects.toString(
				configManager.getConfiguration(BankTagsWriter.BANKTAGS_GROUP, "icon_" + Text.standardize(tag)), ""));
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (UltimateBankTabsConfig.GROUP.equals(event.getGroup()) && RESTORE_KEY.equals(event.getKey()))
		{
			if (Boolean.parseBoolean(event.getNewValue()) && writer != null)
			{
				clientThread.invokeLater(() -> restoreReplaced(false));
			}
		}
		else if (UltimateBankTabsConfig.GROUP.equals(event.getGroup()) && !event.getKey().startsWith("tagName_")
			&& !event.getKey().startsWith(BankTagsWriter.PLANNED_PREFIX) && !event.getKey().startsWith(BankTagsWriter.HOME_PREFIX)
			&& !INTERNAL_KEYS.contains(event.getKey()))
		{
			if ("tabIcons".equals(event.getKey()))
			{
				// toggling automatic icons hands every tab's icon back to the plugin
				data.tabs.forEach(t -> configManager.unsetConfiguration(UltimateBankTabsConfig.GROUP,
					BankTagsWriter.CUSTOM_ICON_PREFIX + t.key));
			}
			dirty = true;
		}
		else if (BankTagsWriter.BANKTAGS_GROUP.equals(event.getGroup()) && writer != null && !writer.isWriting())
		{
			bankTagsEdited(event);
		}
	}

	/** The player used a tab's own menu (Change icon, Disable/Enable layout, Rename, Delete) on one of ours. */
	private void bankTagsEdited(ConfigChanged event)
	{
		String key = event.getKey();
		if ("tagtabs".equals(key))
		{
			clientThread.invokeLater(() -> tabsEdited(event.getOldValue(), event.getNewValue()));
			return;
		}
		String tag = key.startsWith("icon_") ? key.substring("icon_".length())
			: key.startsWith("layout_") ? key.substring("layout_".length()) : null;
		String tabKey = tag == null ? null : writer.keyOf(tag);
		if (tabKey == null || writer.isHidden(tabKey))
		{
			// a hidden tab isn't in Bank Tags' list: an icon or layout written under its name is another tab's
			return;
		}
		if (key.startsWith("icon_") && event.getNewValue() != null && writer.isOurIcon(tag, event.getNewValue()))
		{
			// Bank Tags writing back its stale copy of an icon this plugin set: put the current one back
			dirty = true;
		}
		else if (key.startsWith("icon_") && event.getNewValue() != null)
		{
			// Change icon: keep the player's pick from now on (over a skill icon too)
			writer.iconPicked(tag, event.getNewValue());
			configManager.setConfiguration(UltimateBankTabsConfig.GROUP, BankTagsWriter.CUSTOM_ICON_PREFIX + tabKey,
				event.getNewValue());
			dirty = true;
		}
		else if (key.startsWith("layout_") && event.getNewValue() == null)
		{
			// Disable layout - unless the tab is being renamed or deleted, which also drops its layout
			clientThread.invokeLater(() ->
			{
				if (writer.listed(tag) && layoutManager.loadLayout(tag) == null)
				{
					configManager.setConfiguration(UltimateBankTabsConfig.GROUP,
						BankTagsWriter.LAYOUT_OFF_PREFIX + tabKey, true);
				}
			});
		}
		else if (key.startsWith("layout_") && event.getOldValue() == null)
		{
			// Enable layout: lay the tab out again
			configManager.unsetConfiguration(UltimateBankTabsConfig.GROUP, BankTagsWriter.LAYOUT_OFF_PREFIX + tabKey);
			writer.forgetLayout(tag);
			dirty = true;
		}
	}

	/** The player renamed or deleted tabs; a deleted tab of ours is switched off rather than recreated. */
	private void tabsEdited(String oldCsv, String newCsv)
	{
		List<String> gone = writer.onTagTabsEdited(oldCsv, newCsv);
		// a renamed tab keeps its skill icon under the new name
		dirty = true;
		for (String key : gone)
		{
			configManager.setConfiguration(UltimateBankTabsConfig.GROUP, UltimateBankTabsConfig.TAB_PREFIX + key, false);
			String name = data.tabs.stream().filter(t -> t.key.equals(key)).map(t -> t.name).findFirst().orElse(key);
			chat("No Brain Bank Tabs: the " + name + " tab is off now. Turn it back on in the plugin settings.");
		}
	}

	private void readBank(ItemContainer bank)
	{
		Set<Integer> ids = new HashSet<>();
		for (Item item : bank.getItems())
		{
			int id = item.getId();
			if (id > 0 && id != ItemID.BANK_FILLER && id != ItemID.BLANKOBJECT)
			{
				ids.add(itemManager.canonicalize(id));
			}
		}
		if (!ids.equals(stored))
		{
			stored = ids;
			saveSnapshot();
		}
	}

	/** Same walk the core potion store does: every potion with doses stored counts as owned. */
	private void readPotionStorage()
	{
		Set<Integer> ids = new HashSet<>();
		for (int enumId : new int[]{EnumID.POTIONSTORE_POTIONS, EnumID.POTIONSTORE_UNFINISHED_POTIONS})
		{
			EnumComposition potionList = client.getEnum(enumId);
			if (potionList == null)
			{
				continue;
			}
			for (int potionEnumId : potionList.getIntVals())
			{
				client.runScript(ScriptID.POTIONSTORE_DOSES, potionEnumId);
				if (client.getIntStack()[0] <= 0)
				{
					continue;
				}
				EnumComposition potion = client.getEnum(potionEnumId);
				for (int dose = 4; dose >= 1; dose--)
				{
					int itemId = potion.getIntValue(dose);
					if (itemId > 0)
					{
						// one id per potion: the layout matches any dose to the stored potion
						ids.add(itemId);
						break;
					}
				}
			}
		}
		if (!ids.equals(potions))
		{
			potions = ids;
			saveSnapshot();
			dirty = true;
		}
	}

	private void loadSnapshot()
	{
		Set<Integer> bank = new HashSet<>();
		Set<Integer> pots = new HashSet<>();
		String csv = configManager.getRSProfileConfiguration(UltimateBankTabsConfig.GROUP, OWNED_KEY);
		if (csv != null && !csv.isEmpty())
		{
			String[] parts = csv.split("\\|", -1);
			parse(parts[0], bank);
			if (parts.length > 1)
			{
				parse(parts[1], pots);
			}
		}
		stored = bank;
		potions = pots;
	}

	private static void parse(String csv, Set<Integer> into)
	{
		for (String s : csv.split(","))
		{
			if (!s.isEmpty())
			{
				try
				{
					into.add(Integer.parseInt(s.trim()));
				}
				catch (NumberFormatException ignored)
				{
					// skip junk
				}
			}
		}
	}

	private void saveSnapshot()
	{
		String csv = join(stored) + "|" + join(potions);
		configManager.setRSProfileConfiguration(UltimateBankTabsConfig.GROUP, OWNED_KEY, csv);
	}

	private static String join(Set<Integer> ids)
	{
		return new TreeSet<>(ids).stream().map(String::valueOf).collect(Collectors.joining(","));
	}

	private Set<Integer> owned()
	{
		Set<Integer> owned = new HashSet<>(stored);
		owned.addAll(potions);
		if (config.countCarried())
		{
			for (int containerId : new int[]{InventoryID.WORN, InventoryID.INV})
			{
				ItemContainer c = client.getItemContainer(containerId);
				if (c != null)
				{
					Arrays.stream(c.getItems()).filter(i -> i.getId() > 0)
						.forEach(i -> owned.add(itemManager.canonicalize(i.getId())));
				}
			}
		}
		return owned;
	}

	private boolean enabled(String tabKey)
	{
		String v = configManager.getConfiguration(UltimateBankTabsConfig.GROUP, UltimateBankTabsConfig.TAB_PREFIX + tabKey);
		return v == null || Boolean.parseBoolean(v);
	}

	/** @param announce tell the player when new tabs need the bank reopened */
	/**
	 * HP one eat of the item heals the player now, from RuneLite's item stats (the Item Stats tooltips):
	 * anglerfish follows their Hitpoints, and new foods count as soon as RuneLite knows them. 0 for what
	 * isn't plain food. Client thread.
	 */
	private int heals(int itemId)
	{
		return healCache.computeIfAbsent(itemId, id ->
		{
			Effect effect = itemStatChanges.getItemStatChanges(id);
			if (!(effect instanceof FoodBase) && !(effect instanceof Combo))
			{
				// potions, brews and the random or special ones
				return 0;
			}
			int hp = 0;
			for (StatChange change : effect.calculate(client).getStatChanges())
			{
				if (change.getStat() == Stats.HITPOINTS)
				{
					hp = change.getTheoretical();
				}
				else if (change.getTheoretical() < 0)
				{
					// drains a stat on the way (jug of wine): not a food to bring
					return 0;
				}
			}
			return Math.max(hp, 0);
		});
	}

	private void rebuild(boolean announce)
	{
		dirty = false;
		// Hitpoints may have levelled since: anglerfish heal more
		healCache.clear();
		if (!accepted || client.getGameState() != GameState.LOGGED_IN || stored.isEmpty() && potions.isEmpty())
		{
			// nothing known about this account yet: keep whatever tabs exist
			return;
		}
		Set<Integer> owned = owned();
		TabBuilder.Options options = new TabBuilder.Options();
		options.backups = config.backups();
		options.skillSupplies = config.skillSupplies();
		options.skillGoods = config.skillGoods();
		options.combatSupplies = config.combatSupplies();
		options.blightedSupplies = config.blightedSupplies();
		options.slayerTask = slayerTask;
		options.foodCount = config.foodCount();

		Map<String, BankTagsWriter.Tab> tabs = new LinkedHashMap<>();
		Map<String, String> titles = new HashMap<>();
		Map<String, Map<Integer, String>> why = new HashMap<>();
		Set<String> hidden = new HashSet<>();
		Set<Integer> shown = new HashSet<>();
		for (TabData.Tab def : data.tabs)
		{
			titles.put(def.key, def.name);
			if (!enabled(def.key))
			{
				continue;
			}
			options.shownElsewhere = shown;
			TabBuilder.Built built = builder.build(def, owned, options);
			shown.addAll(built.itemIds());
			if (built.isEmpty())
			{
				// nothing for this account (yet): hide the tab but keep its layout
				hidden.add(def.key);
				continue;
			}
			// the item icon also stands under a skill icon, for when Bank Tags draws the tab without us
			int icon = built.getHero() > 0 ? built.getHero() : def.icon;
			tabs.put(def.key, new BankTagsWriter.Tab(built.itemIds(), LayoutPlanner.layOut(built, config.gapRows()), icon,
				LayoutPlanner.bisPositions(built), LayoutPlanner.hasBlock(built)));
			why.put(def.key, built.reasons());
		}
		int added = writer.publish(tabs, hidden, titles);
		reasons = why;
		Map<String, Integer> sprites = new HashMap<>();
		if (config.tabIcons() == UltimateBankTabsConfig.IconStyle.SKILL)
		{
			for (String key : tabs.keySet())
			{
				String tag = writer.tagOf(key);
				Integer sprite = SkillIcons.SPRITES.get(key);
				if (tag != null && sprite != null && configManager.getConfiguration(UltimateBankTabsConfig.GROUP,
					BankTagsWriter.CUSTOM_ICON_PREFIX + key) == null)
				{
					sprites.put(Text.standardize(tag), sprite);
				}
			}
		}
		tabSprites = sprites;
		if (added > 0 && announce)
		{
			chat("No Brain Bank Tabs: " + added + (added == 1 ? " new tab is" : " new tabs are")
				+ " ready. Close and reopen your bank to see " + (added == 1 ? "it." : "them."));
		}
		log.debug("rebuilt {} tabs from {} owned items", tabs.size(), owned.size());
	}

	/** Why each item is in the bank tab currently open, or null when it isn't one of ours. */
	Map<Integer, String> activeReasons()
	{
		String tag = bankTagsService.getActiveTag();
		String key = tag == null || writer == null ? null : writer.keyOf(tag);
		return key == null ? null : reasons.get(key);
	}

	private void chat(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(message)
			.build());
	}
}
