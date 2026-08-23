package io.github.thebusybiscuit.slimefun5.core.services.localization;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.implementation.setup.SlimefunItemSetup;

/**
 * Regression test: internal GUI chrome stacks (e.g. {@code ChestMenuUtils}' {@code _UI_MENU}) carry a
 * Slimefun id but are never registered as a {@link SlimefunItem}, so the packet layer used to treat them
 * as an orphaned template and overwrite their code-defined name with a humanized version of the id
 * ("_UI_MENU" -> "Ui Menu"). {@link PacketItemRewriter#isInternalChromeId} now makes
 * {@link PacketItemRewriter#applyPacketTranslation} skip such ids entirely.
 */
class PacketTranslationChromeIdTest {

    @BeforeAll
    public static void load() {
        MockBukkit.mock();
        Slimefun plugin = MockBukkit.load(Slimefun.class);
        SlimefunItemSetup.setup(plugin);
    }

    @AfterAll
    public static void unload() {
        MockBukkit.unmock();
    }

    @Test
    void internalChromeIdKeepsItsCodeDefinedName() {
        ItemStack item = new SlimefunItemStack("_UI_MENU", Material.COMPARATOR, "&eSettings / Info").item();

        PacketItemRewriter.applyPacketTranslation(item, "_UI_MENU", "en", TranslationConfig.FallbackMode.ENGLISH, true);

        ItemMeta meta = item.getItemMeta();
        Assertions.assertEquals(ChatColor.translateAlternateColorCodes('&', "&eSettings / Info"), meta.getDisplayName(),
            "an internal chrome id must keep its code-defined name, not the humanized-id fallback");
    }

    @Test
    void nonChromeOrphanedIdStillGetsTheHumanizedFallback() {
        ItemStack item = new SlimefunItemStack("SOME_REMOVED_ADDON_ITEM", Material.STONE, "Placeholder").item();

        PacketItemRewriter.applyPacketTranslation(item, "SOME_REMOVED_ADDON_ITEM", "en", TranslationConfig.FallbackMode.ENGLISH, true);

        ItemMeta meta = item.getItemMeta();
        Assertions.assertEquals(ChatColor.WHITE + "Some Removed Addon Item", meta.getDisplayName(),
            "a genuinely orphaned (non-chrome) id must still fall back to its humanized name, "
                + "otherwise the fix would be masking the orphan-template case instead of the chrome case");
    }

    @Test
    void normalRegisteredItemStillGetsItsUsualResolution() {
        SlimefunItem probe = SlimefunItem.getById("ELECTRIC_MOTOR");
        Assertions.assertNotNull(probe, "ELECTRIC_MOTOR must be registered");

        // MockBukkit's unit-test harness has no baked English baseline for core items (that only happens
        // during a real plugin boot), so give the id an explicit translation - the same way PacketRenderTest
        // does - to prove the usual (non-chrome) resolution path still runs, rather than degrading to the
        // id-fallback that a missing baseline would otherwise produce regardless of this fix.
        Slimefun.getItemTranslationService().loadTranslationsForTest("en", new java.io.ByteArrayInputStream(
            "ELECTRIC_MOTOR:\n  name: '&aTest Electric Motor'\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Slimefun.getItemTranslationService().clearRenderCache();

        ItemStack item = probe.getItem().clone();
        PacketItemRewriter.applyPacketTranslation(item, "ELECTRIC_MOTOR", "en", TranslationConfig.FallbackMode.ENGLISH, true);

        ItemMeta meta = item.getItemMeta();
        Assertions.assertEquals("Test Electric Motor", ChatColor.stripColor(meta.getDisplayName()),
            "a normal registered item id must still resolve its translated name through the usual path");
    }
}
