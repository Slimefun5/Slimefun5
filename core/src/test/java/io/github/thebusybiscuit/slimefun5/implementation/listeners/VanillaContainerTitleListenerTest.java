package io.github.thebusybiscuit.slimefun5.implementation.listeners;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import io.github.thebusybiscuit.slimefun5.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun5.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;

/**
 * Covers {@link VanillaContainerTitleListener#resolveTitle} - the name-resolution behind the per-viewer
 * retitle of a vanilla-container-backed {@link SlimefunItem} block (see
 * {@link BlockListener#clearInheritedCustomName}) - isolated from the Inventory/InventoryView plumbing.
 * MockBukkit cannot simulate a real client receiving a corrected window title packet; that part of this
 * fix can only be confirmed by placing an Enhanced Furnace in-game (see this class's own report).
 */
class VanillaContainerTitleListenerTest {

    private static final class TestContainerItem extends SlimefunItem {
        TestContainerItem(ItemGroup itemGroup, SlimefunItemStack item) {
            super(itemGroup, item, RecipeType.NULL, new ItemStack[9]);
        }
    }

    private static SlimefunItem testItem;

    @BeforeAll
    static void load() {
        MockBukkit.mock();
        Slimefun plugin = MockBukkit.load(Slimefun.class);

        ItemGroup itemGroup = new ItemGroup(new NamespacedKey(plugin, "vanilla_container_title_test"), new ItemStack(Material.CHEST));
        SlimefunItemStack stack = new SlimefunItemStack("TEST_VANILLA_CONTAINER", Material.FURNACE, "&9Test Vanilla Container");
        testItem = new TestContainerItem(itemGroup, stack);
        testItem.register(plugin);
    }

    @AfterAll
    static void unload() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("an id with no registered SlimefunItem falls back to the raw id, never a blank title")
    void unregisteredIdFallsBackToRawId() {
        Assertions.assertEquals("NOT_A_REAL_MACHINE", VanillaContainerTitleListener.resolveTitle("NOT_A_REAL_MACHINE", "en"));
    }

    @Test
    @DisplayName("a registered machine resolves to its own name rather than the vanilla block title")
    void registeredItemResolvesItsOwnName() {
        String title = VanillaContainerTitleListener.resolveTitle(testItem.getId(), "en");
        Assertions.assertNotEquals(testItem.getId(), title, "must not degrade to the raw id when the item itself has a name");
        Assertions.assertEquals("Test Vanilla Container", ChatColor.stripColor(title));
    }
}
