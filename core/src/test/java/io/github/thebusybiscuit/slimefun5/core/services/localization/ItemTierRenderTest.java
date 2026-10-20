package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import io.github.thebusybiscuit.slimefun5.api.items.ItemTier;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.implementation.setup.SlimefunItemSetup;

class ItemTierRenderTest {

    private static Slimefun plugin;

    @BeforeAll
    public static void load() {
        MockBukkit.mock();
        plugin = MockBukkit.load(Slimefun.class);
        SlimefunItemSetup.setup(plugin);
        Slimefun.getItemTranslationService().loadBundled();
    }

    @AfterAll
    public static void unload() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("Core's bundled tiers style its names everywhere a name is shown")
    void coreNamesFollowTheirBundledTier() {
        ItemTranslationService service = Slimefun.getItemTranslationService();
        String translation = "BLISTERING_INGOT:\n  name: '&6Blistering Ingot &7(33%)'\n";
        service.loadTranslationsForTest("en", new ByteArrayInputStream(translation.getBytes(StandardCharsets.UTF_8)));
        service.clearRenderCache();

        String expected = ChatColor.LIGHT_PURPLE + "Blistering Ingot" + ChatColor.GRAY + " (33%)";
        Assertions.assertEquals(expected, service.renderForPacket("BLISTERING_INGOT", "en", TranslationConfig.FallbackMode.ENGLISH, true).name);
        Assertions.assertEquals(expected, service.getNameForLanguage("en", "BLISTERING_INGOT"));
        Assertions.assertEquals(ItemTier.EPIC, SlimefunItem.getById("BLISTERING_INGOT").getItemTier());
        Assertions.assertEquals(ItemTier.COMMON, SlimefunItem.getById("SMALL_BACKPACK").getItemTier());
    }

    @Test
    @DisplayName("A plugin that ships no tiers keeps its names as authored")
    void pluginWithoutTiersIsNotStyled() {
        ItemTiers tiers = new ItemTiers();
        SlimefunItem item = SlimefunItem.getById("BLISTERING_INGOT");

        Assertions.assertFalse(tiers.isStyled(item));

        tiers.loadDeclared(plugin.getName(), new ByteArrayInputStream("NOT_A_REAL_ITEM: rare\n".getBytes(StandardCharsets.UTF_8)));

        Assertions.assertTrue(tiers.isStyled(item));
        Assertions.assertEquals(ItemTier.COMMON, tiers.tierOf("BLISTERING_INGOT"));
        Assertions.assertTrue(tiers.declaredIds().contains("NOT_A_REAL_ITEM"));
    }
}
