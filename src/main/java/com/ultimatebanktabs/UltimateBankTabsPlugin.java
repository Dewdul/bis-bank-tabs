package com.ultimatebanktabs;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
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
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDependency(BankTagsPlugin.class)
@PluginDescriptor(
	name = "Ultimate Bank Tabs",
	description = "Automatic bank tag tabs with your best-in-slot gear for each combat style, movement and every skill,"
		+ " laid out for you",
	tags = {"bank", "tags", "tabs", "bis", "best in slot", "gear", "layout", "skilling", "teleports", "organize"}
)
public class UltimateBankTabsPlugin extends Plugin
{
	private static final String OWNED_KEY = "owned";

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

	private TabData data;
	private TabBuilder builder;
	private BankTagsWriter writer;
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
		builder = new TabBuilder(data, id -> EquipStats.of(itemManager.getItemStats(id)));
		writer = new BankTagsWriter(configManager, tagManager, layoutManager, bankTagsService, itemManager::canonicalize);
		writer.restore(data.tabs.stream().map(t -> t.key).collect(Collectors.toList()));
		overlayManager.add(reasonOverlay);
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				loadSnapshot();
				rebuild(false);
			}
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(reasonOverlay);
		reasons = new HashMap<>();
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
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			bankOpen = true;
			potionsDirty = true;
			dirty = true;
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

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (UltimateBankTabsConfig.GROUP.equals(event.getGroup()) && !event.getKey().startsWith("tagName_")
			&& !OWNED_KEY.equals(event.getKey()))
		{
			dirty = true;
		}
		else if (BankTagsWriter.BANKTAGS_GROUP.equals(event.getGroup()) && "tagtabs".equals(event.getKey())
			&& writer != null && !writer.isWriting())
		{
			clientThread.invokeLater(() -> tabsEdited(event.getNewValue()));
		}
	}

	/** The player deleted one of our tabs from the bank: switch that tab off rather than recreating it. */
	private void tabsEdited(String csv)
	{
		List<String> gone = writer.onTagTabsEdited(csv);
		for (String key : gone)
		{
			configManager.setConfiguration(UltimateBankTabsConfig.GROUP, UltimateBankTabsConfig.TAB_PREFIX + key, false);
			String name = data.tabs.stream().filter(t -> t.key.equals(key)).map(t -> t.name).findFirst().orElse(key);
			chat("Ultimate Bank Tabs: the " + name + " tab is off now. Turn it back on in the plugin settings.");
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
	private void rebuild(boolean announce)
	{
		dirty = false;
		if (client.getGameState() != GameState.LOGGED_IN || stored.isEmpty() && potions.isEmpty())
		{
			// nothing known about this account yet: keep whatever tabs exist
			return;
		}
		Set<Integer> owned = owned();
		TabBuilder.Options options = new TabBuilder.Options();
		options.backups = config.backups();
		options.skillSupplies = config.skillSupplies();
		options.combatSupplies = config.combatSupplies();
		options.foodCount = config.foodCount();

		Map<String, BankTagsWriter.Tab> tabs = new LinkedHashMap<>();
		Map<String, String> titles = new HashMap<>();
		Map<String, Map<Integer, String>> why = new HashMap<>();
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
				continue;
			}
			int icon = config.dynamicIcons() && built.getHero() > 0 ? built.getHero() : def.icon;
			tabs.put(def.key, new BankTagsWriter.Tab(built.itemIds(), LayoutPlanner.plan(built, config.gapRows()), icon));
			why.put(def.key, built.reasons());
		}
		int added = writer.publish(tabs, titles);
		reasons = why;
		if (added > 0 && announce)
		{
			chat("Ultimate Bank Tabs: " + added + (added == 1 ? " new tab is" : " new tabs are")
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
