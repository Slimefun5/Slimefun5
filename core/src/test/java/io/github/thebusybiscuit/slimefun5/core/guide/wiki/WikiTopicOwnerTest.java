package io.github.thebusybiscuit.slimefun5.core.guide.wiki;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * An addon registering a topic through the ownerless {@code registerTopic} must still end up owning it.
 * <p>
 * Without this every addon's guides were listed as core Slimefun's own on the wiki home, and Browse by
 * Addon reported those same addons as shipping no pages at all.
 */
class WikiTopicOwnerTest {

    /** The addons that actually register topics this way, as they are named on a live server. */
    private static final List<String> INSTALLED = Arrays.asList(
        "ChestTerminal", "DynaTech", "ExoticGarden", "ExtraGear", "FluffyMachines", "Galactifun",
        "InfinityExpansion", "LiteXpansion", "SlimefunLuckyBlocks", "MissileWarfare", "Networks",
        "SensibleToolbox", "SlimeTinker");

    @Test
    @DisplayName("An addon claims the topics it registered, with or without a group suffix")
    void addonClaimsItsTopics() {
        Assertions.assertEquals("Networks", WikiText.resolveOwner("networks_cells", INSTALLED));
        Assertions.assertEquals("ChestTerminal", WikiText.resolveOwner("chestterminal", INSTALLED));
        Assertions.assertEquals("ExoticGarden", WikiText.resolveOwner("exoticgarden_plants_and_fruits", INSTALLED));
    }

    @Test
    @DisplayName("An addon whose plugin name differs from the id it uses is still matched")
    void addonWithADifferentPluginNameIsMatched() {
        // SlimefunLuckyBlocks registers its topics as addon_luckyblocks_*, so a plain prefix match misses.
        Assertions.assertEquals("SlimefunLuckyBlocks", WikiText.resolveOwner("luckyblocks_lucky_blocks", INSTALLED));
    }

    @Test
    @DisplayName("The longest matching addon name wins")
    void longestMatchWins() {
        List<String> ambiguous = Arrays.asList("Infinity", "InfinityExpansion");

        Assertions.assertEquals("InfinityExpansion", WikiText.resolveOwner("infinityexpansion_storage", ambiguous));
    }

    @Test
    @DisplayName("A segment matching no installed addon stays unowned")
    void unknownAddonKeepsNoOwner() {
        Assertions.assertNull(WikiText.resolveOwner("somethingnotinstalled_machines", INSTALLED));
        Assertions.assertNull(WikiText.resolveOwner("networks_cells", Collections.<String>emptyList()));
    }
}
