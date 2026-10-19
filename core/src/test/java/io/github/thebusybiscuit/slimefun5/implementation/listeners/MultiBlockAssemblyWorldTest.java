package io.github.thebusybiscuit.slimefun5.implementation.listeners;

import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import io.github.thebusybiscuit.slimefun5.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun5.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun5.core.multiblocks.MultiBlock;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;

class MultiBlockAssemblyWorldTest {

    private ServerMock server;
    private MultiBlockListener listener;
    private final AtomicBoolean matched = new AtomicBoolean();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Slimefun plugin = MockBukkit.load(Slimefun.class);
        listener = new MultiBlockListener(plugin);
        ItemGroup itemGroup = new ItemGroup(new NamespacedKey(plugin, "multiblock_world_test"), new ItemStack(Material.DIAMOND));
        SlimefunItem item = new SlimefunItem(itemGroup, new SlimefunItemStack("MULTIBLOCK_WORLD_TEST", Material.DIAMOND), RecipeType.NULL, new ItemStack[9]);
        item.register(plugin);
        Material[] structure = { null, Material.EMERALD_BLOCK, null, null, Material.DIAMOND_BLOCK, null, null, Material.LAPIS_BLOCK, null };
        Slimefun.getRegistry().getMultiBlocks().add(new MultiBlock(item, structure, BlockFace.SELF) {

            @Override
            public boolean matches(Block center) {
                matched.set(true);
                return false;
            }
        });
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aWorldUnloadedBeforeTheDeferredMatchIsSkipped() {
        server.addSimpleWorld("multiblock_players");
        PlayerMock player = server.addPlayer();
        World world = server.addSimpleWorld("multiblock_unloaded");
        Block placed = buildStructure(world);

        Assertions.assertTrue(server.unloadWorld(world, false));
        listener.announceAssembly(placed, Material.DIAMOND_BLOCK, true, player);

        Assertions.assertFalse(matched.get());
    }

    @Test
    void aWorldTheServerNeverListedIsStillMatched() {
        PlayerMock player = server.addPlayer();
        Block placed = buildStructure(new WorldMock(Material.AIR, 0));

        listener.onMultiBlockComplete(new BlockPlaceEvent(placed, placed.getState(), placed.getRelative(0, -1, 0), new ItemStack(Material.DIAMOND_BLOCK), player, true, EquipmentSlot.HAND));

        Assertions.assertTrue(matched.get());
    }

    private static Block buildStructure(World world) {
        world.getBlockAt(10, 92, 10).setType(Material.EMERALD_BLOCK);
        world.getBlockAt(10, 90, 10).setType(Material.LAPIS_BLOCK);
        Block placed = world.getBlockAt(10, 91, 10);
        placed.setType(Material.DIAMOND_BLOCK);
        return placed;
    }
}
