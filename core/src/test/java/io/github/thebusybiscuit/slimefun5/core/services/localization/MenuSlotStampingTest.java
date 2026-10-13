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

    /** Declares one slot translatable, which is what the stamp is gated on. */
    private static void translatable(String presetId, int slot) {
        Slimefun.getMenuTranslationService().load("en", new java.io.ByteArrayInputStream(
            (presetId + ":\n  '" + slot + "':\n    name: 'baseline'\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
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
        translatable("STAMP_WRITE_PRESET", 1);
        ItemStack stamped = MenuSlotStamp.apply(named("&7Fuel Slot"), "STAMP_WRITE_PRESET", 1);

        Assertions.assertEquals("STAMP_WRITE_PRESET/1",
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
        translatable("STAMP_IDEMPOTENT_PRESET", 3);
        ItemStack once = MenuSlotStamp.apply(named("x"), "STAMP_IDEMPOTENT_PRESET", 3);
        ItemStack twice = MenuSlotStamp.apply(once, "STAMP_IDEMPOTENT_PRESET", 3);

        Assertions.assertEquals("STAMP_IDEMPOTENT_PRESET/3",
            PdcCompat.getString(twice.getItemMeta(), Slimefun.getRegistry().getMenuSlotKey()));
    }

    @Test
    void aStampedItemIsRewrittenIntoTheViewersLanguage() {
        Slimefun.getMenuTranslationService().load("en", new java.io.ByteArrayInputStream(
            ("REWRITE_PRESET:\n  '1':\n    name: 'Fuel Slot'\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Slimefun.getMenuTranslationService().load("de", new java.io.ByteArrayInputStream(
            ("REWRITE_PRESET:\n  '1':\n    name: 'Brennstoff'\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        ItemStack item = MenuSlotStamp.apply(named("Fuel Slot"), "REWRITE_PRESET", 1);
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

    /**
     * C1: the stamp used to mark EVERY slot, including the input and output slots that machines push
     * items through and that BlockMenu persists. A PDC-only meta flips hasItemMeta(), and dough's
     * ItemUtils.canStack rejects a pair whose hasItemMeta() differs, so a stamped machine output
     * stopped stacking with the next one: the machine jammed and android drops were destroyed.
     * <p>
     * This asserts the absence of the KEY rather than of the meta, because MockBukkit reports
     * hasItemMeta() as true for a bare ItemStack where a real server reports false.
     */
    @Test
    void aSlotThatCarriesNoTranslationIsNeverStamped() {
        Slimefun.getMenuTranslationService().load("en", new java.io.ByteArrayInputStream(
            ("STAMP_GATE_PRESET:\n  '1':\n    name: 'Fuel Slot'\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        ItemStack translated = MenuSlotStamp.apply(named("Fuel Slot"), "STAMP_GATE_PRESET", 1);
        ItemStack output = MenuSlotStamp.apply(new ItemStack(Material.IRON_INGOT), "STAMP_GATE_PRESET", 22);

        Assertions.assertEquals("STAMP_GATE_PRESET/1",
            PdcCompat.getString(translated.getItemMeta(), Slimefun.getRegistry().getMenuSlotKey()));
        Assertions.assertNull(
            PdcCompat.getString(output.getItemMeta(), Slimefun.getRegistry().getMenuSlotKey()),
            "an untranslatable slot must carry no key, or its meta stops the item stacking");
    }

    /** The invariant the whole design rests on: the packet path must not touch its input. */
    @Test
    void rewritingLeavesTheSharedItemUntouched() {
        Slimefun.getMenuTranslationService().load("de", new java.io.ByteArrayInputStream(
            ("SHARED_SOURCE:\n  '1':\n    name: 'Brennstoff'\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Slimefun.getMenuTranslationService().load("en", new java.io.ByteArrayInputStream(
            ("SHARED_SOURCE:\n  '1':\n    name: 'Fuel Slot'\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        ItemStack shared = MenuSlotStamp.apply(named("Fuel Slot"), "SHARED_SOURCE", 1);
        ItemStack rewritten = Slimefun.getMenuTranslationService().rewriteStampedItem(shared, "de");

        Assertions.assertEquals("Brennstoff", rewritten.getItemMeta().getDisplayName());
        Assertions.assertEquals("Fuel Slot", shared.getItemMeta().getDisplayName());
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
