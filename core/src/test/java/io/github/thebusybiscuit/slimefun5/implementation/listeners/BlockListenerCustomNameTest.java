package io.github.thebusybiscuit.slimefun5.implementation.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Nameable;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import io.github.thebusybiscuit.slimefun5.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun5.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;

import me.mrCookieSlime.Slimefun.api.BlockStorage;

/**
 * Covers the fix for a placed vanilla-container {@link SlimefunItem} (furnace, chest, dropper, hopper,
 * dispenser, ...) permanently inheriting its baked, physically-translated display name as the block
 * entity's {@code CustomName} - which then shows to every future viewer instead of the client's own
 * localized container title (see {@link BlockListener#clearInheritedCustomName}).
 */
class BlockListenerCustomNameTest {

    /** A minimal Furnace-backed SlimefunItem: no research/permission gate, so canUse() is always true. */
    private static final class TestFurnaceItem extends SlimefunItem {
        TestFurnaceItem(ItemGroup itemGroup, SlimefunItemStack item) {
            super(itemGroup, item, RecipeType.NULL, new ItemStack[9]);
        }
    }

    private static ServerMock server;
    private static SlimefunItem furnaceItem;

    @BeforeAll
    static void load() {
        server = MockBukkit.mock();
        Slimefun plugin = MockBukkit.load(Slimefun.class);
        new BlockListener(plugin);

        ItemGroup itemGroup = new ItemGroup(new NamespacedKey(plugin, "block_listener_custom_name_test"), new ItemStack(Material.CHEST));
        furnaceItem = new TestFurnaceItem(itemGroup, new SlimefunItemStack("TEST_NAMEABLE_FURNACE", Material.FURNACE));
        furnaceItem.register(plugin);
    }

    @AfterAll
    static void unload() {
        MockBukkit.unmock();
    }

    /**
     * Real vanilla placement stamps a Nameable block entity's CustomName from a renamed item's display
     * name BEFORE Bukkit's BlockPlaceEvent fires; MockBukkit does not model that NMS-level step, so it is
     * simulated here by setting the CustomName on the not-yet-placed block right before firing the event.
     *
     * <p>The item stack itself is given an explicit display name (standing in for the physical bake that
     * {@code ItemTranslationService.canonicalizeToId()} applies during a full plugin boot) so the scenario
     * matches a real, physically-baked item without depending on that full translation pipeline here.
     */
    private static Block placeWithInheritedName(ItemStack item, Player player) {
        WorldMock world = server.addSimpleWorld("block_listener_custom_name_" + System.nanoTime());
        Slimefun.getRegistry().getWorlds().put(world.getName(), new BlockStorage(world));

        Block block = new BlockMock(item.getType(), new Location(world, 0, 64, 0));
        Block blockAgainst = new BlockMock(Material.GRASS_BLOCK, new Location(world, 0, 63, 0));

        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Baked Physical Name");
        item.setItemMeta(meta);

        BlockState beforePlacement = block.getState();
        ((Nameable) beforePlacement).setCustomName(meta.getDisplayName());
        beforePlacement.update(true, false);

        BlockPlaceEvent event = new BlockPlaceEvent(block, block.getState(), blockAgainst, item, player, true, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);

        return block;
    }

    @Test
    @DisplayName("placing a Nameable vanilla-container SlimefunItem clears the inherited CustomName")
    void placingNameableContainerClearsCustomName() {
        Player player = new PlayerMock(server, "FurnacePlacer");
        ItemStack item = furnaceItem.getItem();

        Block block = placeWithInheritedName(item, player);

        BlockState after = block.getState();
        Assertions.assertInstanceOf(Nameable.class, after, "a FURNACE BlockState must be Nameable");
        Assertions.assertNull(((Nameable) after).getCustomName(),
            "the placed container's inherited CustomName must be cleared so every viewer gets the client-translated title");
    }

    @Test
    @DisplayName("clearing the inherited CustomName does not touch the block's Slimefun identity")
    void clearingCustomNameKeepsBlockStorageIdentity() {
        Player player = new PlayerMock(server, "FurnacePlacer2");
        ItemStack item = furnaceItem.getItem();

        Block block = placeWithInheritedName(item, player);

        Assertions.assertEquals(furnaceItem.getId(), BlockStorage.checkID(block),
            "the Slimefun identity must come from BlockStorage, not the block's display name");
    }
}
