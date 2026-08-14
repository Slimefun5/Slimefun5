package io.github.thebusybiscuit.slimefun5.core.guide.wiki;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.implementation.setup.SlimefunItemSetup;

/** The wiki site is served from a lowercase path; GitHub Pages paths are case-sensitive. */
class WikiLinksUrlTest {

    private static SlimefunItem coreItem;

    @BeforeAll
    public static void load() {
        MockBukkit.mock();
        Slimefun plugin = MockBukkit.load(Slimefun.class);
        SlimefunItemSetup.setup(plugin);

        List<SlimefunItem> items = Slimefun.getRegistry().getEnabledSlimefunItems();
        Assertions.assertFalse(items.isEmpty(), "Need at least one registered item");
        coreItem = items.get(0);
    }

    @AfterAll
    public static void unload() {
        MockBukkit.unmock();
    }

    @Test
    void coreItemUrlUsesTheLowercaseWikiPath() {
        String url = WikiLinks.urlFor(coreItem);
        Assertions.assertEquals(
            "https://slimefun5.github.io/wiki/slimefun/" + coreItem.getId().toLowerCase(java.util.Locale.ROOT),
            url);
    }
}
