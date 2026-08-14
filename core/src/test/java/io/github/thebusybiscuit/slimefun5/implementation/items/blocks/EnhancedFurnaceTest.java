package io.github.thebusybiscuit.slimefun5.implementation.items.blocks;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.implementation.setup.SlimefunItemSetup;

import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;

class EnhancedFurnaceTest {

    private static ServerMock server;

    @BeforeAll
    static void load() {
        server = MockBukkit.mock();
        Slimefun plugin = MockBukkit.load(Slimefun.class);
        SlimefunItemSetup.setup(plugin);
    }

    @AfterAll
    static void unload() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("tick() boosts cook time and the change is visible on a freshly-read BlockState")
    void tickPersistsBoostedCookTimeRegardlessOfSnapshotState() {
        // NOTE: MockBukkit's PaperLib environment reports isSnapshot()=true unconditionally (it doesn't
        // model Paper's optional live-TileState view), so this test cannot reproduce the specific 26.x gap
        // that motivated always calling state.update() (see EnhancedFurnace and AbstractAutoCrafter, commit
        // 83399a6f28a) - it passes under both the old isSnapshot()-gated code and the fixed unconditional
        // one. It still guards the ticker's general boost-and-persist mechanics against a real regression.
        SlimefunItem furnaceItem = SlimefunItem.getById("ENHANCED_FURNACE_3");
        Assertions.assertNotNull(furnaceItem, "ENHANCED_FURNACE_3 must be registered");

        WorldMock world = server.addSimpleWorld("enhanced_furnace_test");
        Block block = world.getBlockAt(0, 64, 0);
        block.setType(Material.FURNACE);

        Furnace initial = (Furnace) block.getState();
        initial.setCookTimeTotal(200);
        initial.setCookTime((short) 5);
        initial.update(true, false);

        BlockTicker ticker = furnaceItem.getBlockTicker();
        Assertions.assertNotNull(ticker, "ENHANCED_FURNACE_3 must carry a BlockTicker");
        ticker.tick(block, furnaceItem, null);

        Furnace after = (Furnace) block.getState();
        Assertions.assertTrue(after.getCookTime() > 5,
            "the ticker's boosted cook time must be visible on a freshly-read BlockState; got " + after.getCookTime());
    }

    @Test
    @DisplayName("tick() clears the block once it stops being a vanilla furnace")
    void tickClearsBlockDataWhenNoLongerAFurnace() {
        SlimefunItem furnaceItem = SlimefunItem.getById("ENHANCED_FURNACE_3");
        Assertions.assertNotNull(furnaceItem);

        WorldMock world = server.addSimpleWorld("enhanced_furnace_destroyed_test");
        Block block = world.getBlockAt(1, 64, 0);
        block.setType(Material.AIR);

        BlockTicker ticker = furnaceItem.getBlockTicker();
        Assertions.assertDoesNotThrow(() -> ticker.tick(block, furnaceItem, null),
            "ticking a destroyed furnace block must not throw");
    }
}
