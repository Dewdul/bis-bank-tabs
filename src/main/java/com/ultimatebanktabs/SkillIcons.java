package com.ultimatebanktabs;

import java.util.HashMap;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.runelite.api.Client;
import net.runelite.api.SpritePixels;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/**
 * Draws a skill icon on this plugin's bank tabs instead of an item.
 *
 * <p>Bank Tags only knows item icons: each tab is a background graphic
 * followed by an item graphic, from child 4 of the bank's item container.
 * Bank Tags rebuilds and scrolls those widgets itself, so the swap is
 * re-applied before every frame.
 */
class SkillIcons
{
	/** tab key -> sprite; tabs missing here (cosmetics) keep their item icon */
	static final Map<String, Integer> SPRITES = new HashMap<>();

	static
	{
		SPRITES.put("melee", SpriteID.Staticons.ATTACK);
		SPRITES.put("ranged", SpriteID.Staticons.RANGED);
		SPRITES.put("magic", SpriteID.Staticons.MAGIC);
		SPRITES.put("movement", SpriteID.OrbIcon.RUN);
		SPRITES.put("prayer", SpriteID.Staticons.PRAYER);
		SPRITES.put("slayer", SpriteID.Staticons2.SLAYER);
		SPRITES.put("agility", SpriteID.Staticons.AGILITY);
		SPRITES.put("thieving", SpriteID.Staticons.THIEVING);
		SPRITES.put("herblore", SpriteID.Staticons.HERBLORE);
		SPRITES.put("crafting", SpriteID.Staticons.CRAFTING);
		SPRITES.put("fletching", SpriteID.Staticons.FLETCHING);
		SPRITES.put("hunter", SpriteID.Staticons2.HUNTER);
		SPRITES.put("mining", SpriteID.Staticons.MINING);
		SPRITES.put("smithing", SpriteID.Staticons.SMITHING);
		SPRITES.put("fishing", SpriteID.Staticons.FISHING);
		SPRITES.put("cooking", SpriteID.Staticons.COOKING);
		SPRITES.put("firemaking", SpriteID.Staticons.FIREMAKING);
		SPRITES.put("woodcutting", SpriteID.Staticons.WOODCUTTING);
		SPRITES.put("farming", SpriteID.Staticons2.FARMING);
		SPRITES.put("runecraft", SpriteID.Staticons2.RUNECRAFT);
		SPRITES.put("construction", SpriteID.Staticons2.CONSTRUCTION);
		SPRITES.put("sailing", SpriteID.Staticons2.SAILING);
	}

	/** Bank Tags' tab widgets (TabInterface) */
	private static final int FIRST_TAB_CHILD = 4;
	private static final int ICON_X = 4;
	private static final int ICON_Y_OFFSET = 4;
	private static final int ICON_WIDTH = 36;
	private static final int ICON_HEIGHT = 32;

	private final Client client;
	/** sprite id -> {width, height}; sprites never change size */
	private final Map<Integer, int[]> sizes = new HashMap<>();

	SkillIcons(Client client)
	{
		this.client = client;
	}

	/**
	 * @param sprites  tag name -> sprite to show; other tabs keep (or get back) their item icon
	 * @param itemIcon tag name -> the item icon to give back to a tab that no longer gets a sprite
	 */
	void paint(Map<String, Integer> sprites, ToIntFunction<String> itemIcon)
	{
		Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER);
		Widget[] children = container == null ? null : container.getChildren();
		if (children == null)
		{
			return;
		}
		for (int i = FIRST_TAB_CHILD; i + 1 < children.length; i += 2)
		{
			Widget background = children[i];
			Widget icon = children[i + 1];
			if (background == null || icon == null || icon.getName() == null)
			{
				continue;
			}
			String tag = Text.removeTags(icon.getName());
			Integer sprite = sprites.get(tag);
			int[] size = sprite == null ? null : size(sprite);
			if (size == null)
			{
				if (icon.getItemId() == -1 && SPRITES.containsValue(icon.getSpriteId()))
				{
					// painted earlier, and the setting or a Change icon says items now
					place(icon, background, -1, itemIcon.applyAsInt(tag), ICON_WIDTH, ICON_HEIGHT);
				}
				continue;
			}
			place(icon, background, sprite, -1, size[0], size[1]);
		}
	}

	/** Centre the icon in the tab, as Bank Tags lays out its 36x32 item icons. */
	private static void place(Widget icon, Widget background, int sprite, int item, int width, int height)
	{
		int x = ICON_X + (ICON_WIDTH - width) / 2;
		int y = background.getOriginalY() + ICON_Y_OFFSET + (ICON_HEIGHT - height) / 2;
		if (icon.getSpriteId() == sprite && icon.getItemId() == item && icon.getOriginalX() == x
			&& icon.getOriginalY() == y && icon.getOriginalWidth() == width && icon.getOriginalHeight() == height)
		{
			return;
		}
		icon.setSpriteId(sprite);
		icon.setItemId(item);
		icon.setOriginalWidth(width);
		icon.setOriginalHeight(height);
		icon.setOriginalX(x);
		icon.setOriginalY(y);
		icon.revalidate();
	}

	private int[] size(int sprite)
	{
		int[] size = sizes.get(sprite);
		if (size == null)
		{
			SpritePixels[] frames = client.getSprites(client.getIndexSprites(), sprite, 0);
			if (frames == null || frames.length == 0)
			{
				return null;
			}
			size = new int[]{frames[0].getMaxWidth(), frames[0].getMaxHeight()};
			sizes.put(sprite, size);
		}
		return size;
	}
}
