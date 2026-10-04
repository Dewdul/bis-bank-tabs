package com.ultimatebanktabs;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class UltimateBankTabsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(UltimateBankTabsPlugin.class);
		RuneLite.main(args);
	}
}
