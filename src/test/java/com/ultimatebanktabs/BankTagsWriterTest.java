package com.ultimatebanktabs;

import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class BankTagsWriterTest
{
	@Test
	public void theirTabsNamedLikeOursAreTheSameNamedOnes()
	{
		assertEquals(Arrays.asList("Slayer", "MELEE"), BankTagsWriter.sameNamed(
			Arrays.asList("Bossing", "Slayer", "MELEE", "slayer stuff"), Arrays.asList("Melee", "Slayer", "Mining")));
		assertEquals(Collections.emptyList(), BankTagsWriter.sameNamed(Arrays.asList("Clues"), Arrays.asList("Melee")));
	}

	@Test
	public void replacingSomeTabsKeepsTheRestInOrder()
	{
		assertEquals(Arrays.asList("Bossing", "melee", "Clues"), BankTagsWriter.without(
			Arrays.asList("Bossing", "Slayer", "melee", "Clues"), Arrays.asList("slayer")));
	}
}
