package io.github.thebusybiscuit.slimefun5.core.guide;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/** Covers the bulk toggle-all and solo state transitions backing the addon-visibility menu's buttons. */
class AddonVisibilityTest {

    private static ServerMock server;

    @BeforeAll
    public static void load() {
        server = MockBukkit.mock();
        MockBukkit.load(Slimefun.class);
    }

    @AfterAll
    public static void unload() {
        MockBukkit.unmock();
    }

    private static Set<String> addons(String... ids) {
        return new LinkedHashSet<>(Arrays.asList(ids));
    }

    @Test
    @DisplayName("bulk setHidden(false) shows every addon in one call")
    void bulkShowAll() {
        Player p = server.addPlayer();
        Set<String> ids = addons("slimefun", "foxymachines", "networks");

        AddonVisibility.setHidden(p, "networks", true);
        AddonVisibility.setHidden(p, "foxymachines", true);
        Assertions.assertTrue(AddonVisibility.isHidden(p, "networks"));

        AddonVisibility.setHidden(p, ids, false);

        for (String id : ids) {
            Assertions.assertFalse(AddonVisibility.isHidden(p, id), id + " must be visible after show-all");
        }
    }

    @Test
    @DisplayName("bulk setHidden(true) hides every addon passed in")
    void bulkHideAll() {
        Player p = server.addPlayer();
        Set<String> ids = addons("slimefun", "foxymachines", "networks");

        AddonVisibility.setHidden(p, ids, true);

        for (String id : ids) {
            Assertions.assertTrue(AddonVisibility.isHidden(p, id), id + " must be hidden after hide-all");
        }
    }

    @Test
    @DisplayName("solo() hides every other addon and leaves only the chosen one visible")
    void soloHidesEveryoneElse() {
        Player p = server.addPlayer();
        Set<String> ids = addons("slimefun", "foxymachines", "networks", "supreme");

        AddonVisibility.solo(p, ids, "networks");

        Assertions.assertFalse(AddonVisibility.isHidden(p, "networks"), "the solo'd addon must stay visible");
        Assertions.assertTrue(AddonVisibility.isHidden(p, "slimefun"));
        Assertions.assertTrue(AddonVisibility.isHidden(p, "foxymachines"));
        Assertions.assertTrue(AddonVisibility.isHidden(p, "supreme"));
    }

    @Test
    @DisplayName("solo() is case-insensitive and replaces any previous hidden set entirely")
    void soloReplacesPreviousState() {
        Player p = server.addPlayer();
        Set<String> ids = addons("slimefun", "foxymachines", "networks");

        // Start from an unrelated prior hidden state to prove solo() doesn't merge with it.
        AddonVisibility.setHidden(p, "networks", true);
        AddonVisibility.solo(p, ids, "NETWORKS");

        Assertions.assertFalse(AddonVisibility.isHidden(p, "networks"));
        Assertions.assertTrue(AddonVisibility.isHidden(p, "slimefun"));
        Assertions.assertTrue(AddonVisibility.isHidden(p, "foxymachines"));
    }

    @Test
    @DisplayName("solo() on a single-addon set hides nothing")
    void soloWithOnlyOneAddon() {
        Player p = server.addPlayer();
        AddonVisibility.solo(p, addons("slimefun"), "slimefun");
        Assertions.assertFalse(AddonVisibility.isHidden(p, "slimefun"));
    }
}
