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

/**
 * Covers the 1.8 to 1.13 platforms, where there is no {@code PersistentDataContainer} at all: nothing
 * can be stamped and nothing can be read back, so the whole feature must degrade to the open-time
 * rewrite rather than blanking a menu.
 */
class MenuSlotNoPdcTest {

    @BeforeAll
    public static void load() {
        MockBukkit.mock();
        MockBukkit.load(Slimefun.class);
    }

    @AfterAll
    public static void unload() {
        MockBukkit.unmock();
    }

    /**
     * MockBukkit supports PDC, so this asserts the SHAPE of the degradation rather than simulating a
     * 1.8 server: an item that was never stamped comes back untouched, which is the state every item
     * is in when {@link PdcCompat#isSupported()} is false.
     */
    @Test
    void anItemThatCouldNotBeStampedIsNeverRewritten() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("&7Fuel Slot");
        item.setItemMeta(meta);

        Assertions.assertNull(Slimefun.getMenuTranslationService().rewriteStampedItem(item, "de"));
        Assertions.assertEquals("&7Fuel Slot", item.getItemMeta().getDisplayName());
    }

    @Test
    void stampingReportsWhetherTheServerCanCarryTheKey() {
        ItemStack stamped = MenuSlotStamp.apply(new ItemStack(Material.PAPER), "P", 1);

        Assertions.assertNotNull(stamped);
        Assertions.assertEquals(PdcCompat.isSupported(),
            PdcCompat.getString(stamped.getItemMeta(), Slimefun.getRegistry().getMenuSlotKey()) != null);
    }
}
