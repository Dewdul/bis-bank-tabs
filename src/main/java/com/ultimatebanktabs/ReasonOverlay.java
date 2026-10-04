package com.ultimatebanktabs;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * Hovering an item in one of the plugin's tabs shows why it is there, e.g.
 * "Demonbane: +60% accuracy and damage vs demons".
 */
class ReasonOverlay extends Overlay
{
	private final Client client;
	private final ItemManager itemManager;
	private final TooltipManager tooltipManager;
	private final UltimateBankTabsPlugin plugin;
	private final UltimateBankTabsConfig config;

	@Inject
	ReasonOverlay(Client client, ItemManager itemManager, TooltipManager tooltipManager, UltimateBankTabsPlugin plugin,
		UltimateBankTabsConfig config)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.tooltipManager = tooltipManager;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showReasons() || client.isMenuOpen())
		{
			return null;
		}
		Map<Integer, String> reasons = plugin.activeReasons();
		Widget items = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (reasons == null || items == null || items.isHidden())
		{
			return null;
		}
		Point mouse = client.getMouseCanvasPosition();
		if (!items.getBounds().contains(mouse.getX(), mouse.getY()))
		{
			return null;
		}
		Widget[] children = items.getDynamicChildren();
		if (children == null)
		{
			return null;
		}
		for (Widget child : children)
		{
			int itemId = child.getItemId();
			if (itemId <= 0 || itemId == ItemID.BLANKOBJECT || child.isHidden()
				|| !child.getBounds().contains(mouse.getX(), mouse.getY()))
			{
				continue;
			}
			String reason = reasons.get(itemManager.canonicalize(itemId));
			if (reason != null && !reason.isEmpty())
			{
				tooltipManager.add(new Tooltip(reason));
			}
			break;
		}
		return null;
	}
}
