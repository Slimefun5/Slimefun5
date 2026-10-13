package io.github.thebusybiscuit.slimefun5.core.services.localization;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.PdcCompat;

class MenuSlotStampingTest {

    @BeforeAll
    public static void load() {
        MockBukkit.mock();
        MockBukkit.load(Slimefun.class);
    }

    @AfterAll
    public static void unload() {
        MockBukkit.unmock();
    }

    private static ItemStack named(String name) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    @Test
    void theRegistryExposesOneStableKey() {
        Assertions.assertNotNull(Slimefun.getRegistry().getMenuSlotKey());
        Assertions.assertSame(Slimefun.getRegistry().getMenuSlotKey(),
            Slimefun.getRegistry().getMenuSlotKey());
    }

    @Test
    void stampingWritesThePresetAndSlot() {
        ItemStack stamped = MenuSlotStamp.apply(named("&7Fuel Slot"), "NUCLEAR_REACTOR", 1);

        Assertions.assertEquals("NUCLEAR_REACTOR/1",
            PdcCompat.getString(stamped.getItemMeta(), Slimefun.getRegistry().getMenuSlotKey()));
    }

    @Test
    void stampingNullOrMetalessItemsIsANoOp() {
        Assertions.assertNull(MenuSlotStamp.apply(null, "P", 1));
        Assertions.assertNotNull(MenuSlotStamp.apply(new ItemStack(Material.AIR), "P", 1));
    }

    /** Re-stamping the same slot must not accumulate or change the value. */
    @Test
    void stampingTwiceIsIdempotent() {
        ItemStack once = MenuSlotStamp.apply(named("x"), "P", 3);
        ItemStack twice = MenuSlotStamp.apply(once, "P", 3);

        Assertions.assertEquals("P/3",
            PdcCompat.getString(twice.getItemMeta(), Slimefun.getRegistry().getMenuSlotKey()));
    }

    @Test
    void aStampedItemIsRewrittenIntoTheViewersLanguage() {
        Slimefun.getMenuTranslationService().load("de", new java.io.ByteArrayInputStream(
            ("NUCLEAR_REACTOR:\n  '1':\n    name: 'Brennstoff'\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        ItemStack item = MenuSlotStamp.apply(named("Fuel Slot"), "NUCLEAR_REACTOR", 1);
        ItemStack rewritten = Slimefun.getMenuTranslationService().rewriteStampedItem(item, "de");

        Assertions.assertNotNull(rewritten);
        Assertions.assertEquals("Brennstoff", rewritten.getItemMeta().getDisplayName());
    }

    @Test
    void anUnstampedItemIsLeftExactlyAsItWas() {
        Assertions.assertNull(
            Slimefun.getMenuTranslationService().rewriteStampedItem(named("Fuel Slot"), "de"));
    }

    @Test
    void aStampedItemWithNoTranslationIsLeftExactlyAsItWas() {
        ItemStack item = MenuSlotStamp.apply(named("Fuel Slot"), "NO_SUCH_PRESET", 1);

        Assertions.assertNull(Slimefun.getMenuTranslationService().rewriteStampedItem(item, "de"));
    }

    /** A hand-edited or foreign PDC value must not throw on the Netty thread. */
    @Test
    void aMalformedKeyIsLeftExactlyAsItWas() {
        ItemStack item = named("Fuel Slot");
        ItemMeta meta = item.getItemMeta();
        PdcCompat.setString(meta, Slimefun.getRegistry().getMenuSlotKey(), "not-a-slot");
        item.setItemMeta(meta);

        Assertions.assertNull(Slimefun.getMenuTranslationService().rewriteStampedItem(item, "de"));
    }
}
