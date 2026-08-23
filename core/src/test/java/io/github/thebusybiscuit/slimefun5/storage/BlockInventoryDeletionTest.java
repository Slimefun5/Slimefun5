package io.github.thebusybiscuit.slimefun5.storage;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.storage.backend.jdbc.JdbcBackend;
import io.github.thebusybiscuit.slimefun5.utils.FileUtils;

import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;

/**
 * Breaking a machine has to remove its stored inventory from whichever backend is active, and moving one
 * has to leave the menu persistable at its new location.
 * <p>
 * Both used to go through {@link BlockMenu}'s own flat-file helpers, which hardcode a {@code .sfi} path.
 * On the default H2 backend that deleted nothing, so the next block placed on the same spot loaded the
 * broken machine's contents straight back out of the database and the player collected them twice.
 */
class BlockInventoryDeletionTest {

    private static ServerMock server;
    private static World world;
    private static JdbcBackend backend;

    @BeforeAll
    static void load() {
        server = MockBukkit.mock();
        MockBukkit.load(Slimefun.class);
        world = server.createWorld(WorldCreator.name("world").environment(Environment.NORMAL));

        ConfigurationSerialization.registerClass(ItemStack.class);
        ConfigurationSerialization.registerClass(ItemMeta.class);

        backend = new JdbcBackend("jdbc:h2:mem:sf_inventory_deletion;DB_CLOSE_DELAY=-1");
        Slimefun.setBlockStorageBackendForTesting(backend);
    }

    @AfterAll
    static void unload() throws IOException {
        backend.close();
        MockBukkit.unmock();
        FileUtils.deleteDirectory(new File("data-storage"));
    }

    private static final class TestPreset extends BlockMenuPreset {

        TestPreset(String id) {
            super(id, "Test Machine", false);
        }

        @Override
        public void init() {
            setSize(9);
        }

        @Override
        public boolean canOpen(org.bukkit.block.Block block, org.bukkit.entity.Player player) {
            return true;
        }

        @Override
        public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
            return new int[] { 0 };
        }
    }

    @Test
    @DisplayName("Breaking a machine must drop its inventory from the active backend, not just a .sfi file")
    void testClearingAnInventoryRemovesItFromTheBackend() {
        Location location = new Location(world, 100, 64, 100);
        BlockMenuPreset preset = new TestPreset("TEST_DELETION_MACHINE");

        BlockStorage storage = BlockStorage.getOrCreate(world);
        BlockMenu menu = storage.loadInventory(location, preset);
        menu.replaceExistingItem(0, new ItemStack(Material.DIAMOND, 44));

        backend.flushInventories(Collections.singletonMap(location, menu));
        Assertions.assertNotNull(backend.loadInventoryIfPresent(location, preset), "the machine's contents should have been persisted");

        storage.clearInventory(location);

        Assertions.assertNull(
            backend.loadInventoryIfPresent(location, preset),
            "a broken machine's inventory must not survive in the backend, or the next block placed here inherits (and duplicates) its contents"
        );
    }

    @Test
    @DisplayName("A machine broken and rebuilt on the same spot must not inherit the old contents")
    void testRebuildingOnTheSameSpotStartsEmpty() {
        Location location = new Location(world, 300, 64, 300);
        BlockMenuPreset preset = new TestPreset("TEST_REBUILD_MACHINE");

        BlockStorage storage = BlockStorage.getOrCreate(world);
        BlockMenu menu = storage.loadInventory(location, preset);
        menu.replaceExistingItem(0, new ItemStack(Material.GOLD_INGOT, 64));
        backend.flushInventories(Collections.singletonMap(location, menu));

        storage.clearInventory(location);

        BlockMenu rebuilt = storage.loadInventory(location, preset);
        Assertions.assertNull(rebuilt.getItemInSlot(0), "the rebuilt machine must not start holding the broken one's contents");
    }

    @Test
    @DisplayName("Moving a machine must leave the menu dirty so the backend persists it at its new location")
    void testMovingAnInventoryKeepsItPersistable() {
        Location from = new Location(world, 200, 64, 200);
        Location to = new Location(world, 201, 64, 200);
        BlockMenuPreset preset = new TestPreset("TEST_MOVE_MACHINE");

        BlockStorage storage = BlockStorage.getOrCreate(world);
        BlockMenu menu = storage.loadInventory(from, preset);
        menu.replaceExistingItem(0, new ItemStack(Material.EMERALD, 12));

        backend.flushInventories(Collections.singletonMap(from, menu));

        menu.move(to);

        Assertions.assertNull(backend.loadInventoryIfPresent(from, preset), "the old location must not keep a copy");
        Assertions.assertTrue(menu.isDirty(), "the moved menu must still be dirty, otherwise the next flush skips it and its contents are lost");

        backend.flushInventories(Collections.singletonMap(to, menu));
        BlockMenu reloaded = backend.loadInventoryIfPresent(to, preset);

        Assertions.assertNotNull(reloaded, "the machine must be persisted at its new location");
        Assertions.assertEquals(Material.EMERALD, reloaded.getItemInSlot(0).getType());
    }
}
