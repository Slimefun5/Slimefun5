package io.github.thebusybiscuit.slimefun5.core.guide.wiki;

import java.util.List;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.implementation.setup.SlimefunItemSetup;

/** The wiki site is served from a lowercase path; GitHub Pages paths are case-sensitive. */
class WikiLinksUrlTest {

    private static Slimefun plugin;
    private static SlimefunItem coreItem;

    @BeforeAll
    public static void load() {
        MockBukkit.mock();
        plugin = MockBukkit.load(Slimefun.class);
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

    @Test
    void unsafeIdCharactersAreSanitizedToHyphens() {
        SlimefunItemStack itemStack = new SlimefunItemStack("WHO_NEEDS_PRESSURE_PLATES?_TRAIT+PROP,LINKS'REDSTONE!ALLOY", Material.STONE);
        SlimefunItem item = new SlimefunItem(coreItem.getItemGroup(), itemStack);
        item.register(plugin);

        String url = WikiLinks.urlFor(item);

        Assertions.assertEquals(
            "https://slimefun5.github.io/wiki/slimefun/who_needs_pressure_plates-_trait-prop-links-redstone-alloy",
            url);
    }

    @Test
    @SuppressWarnings("deprecation")
    void itemWithLegacyOfficialWikipageStillUsesTheUniformUrl() {
        SlimefunItemStack itemStack = new SlimefunItemStack("TEST_LEGACY_WIKIPAGE_ITEM", Material.DIAMOND_SWORD);
        SlimefunItem item = new SlimefunItem(coreItem.getItemGroup(), itemStack);
        item.addOfficialWikipage("Sword-of-Beheading");
        item.register(plugin);

        String url = WikiLinks.urlFor(item);

        Assertions.assertEquals("https://slimefun5.github.io/wiki/slimefun/test_legacy_wikipage_item", url);
        Assertions.assertFalse(url.contains("github.com/Slimefun5/Slimefun5/wiki"),
            "a legacy addOfficialWikipage() call must not send players to the old GitHub wiki");
    }
}
