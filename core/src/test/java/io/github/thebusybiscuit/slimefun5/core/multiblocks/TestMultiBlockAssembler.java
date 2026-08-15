package io.github.thebusybiscuit.slimefun5.core.multiblocks;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.thebusybiscuit.slimefun5.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun5.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;

import me.mrCookieSlime.Slimefun.api.BlockStorage;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

class TestMultiBlockAssembler {

    private static ServerMock server;
    private static Slimefun plugin;

    @BeforeAll
    static void load() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Slimefun.class);
    }

    @AfterAll
    static void unload() {
        MockBukkit.unmock();
    }

    /** A fresh, BlockStorage-backed world so each test's blocks are isolated from the others. */
    private static WorldMock freshWorld() {
        WorldMock world = server.addSimpleWorld("multiblock-assembler-" + System.nanoTime());
        Slimefun.getRegistry().getWorlds().put(world.getName(), new BlockStorage(world));
        return world;
    }

    private static SlimefunItem registerCustomBlock(String id, Material material) {
        ItemGroup itemGroup = new ItemGroup(new NamespacedKey(plugin, id.toLowerCase() + "_group"), new ItemStack(Material.EMERALD));
        SlimefunItem item = new SlimefunItem(itemGroup, new SlimefunItemStack(id, material), RecipeType.NULL, new ItemStack[9]);
        item.register(plugin);
        return item;
    }

    private static MultiBlockMachine registerMachine(String id, Material[] recipeMaterials) {
        ItemGroup itemGroup = new ItemGroup(new NamespacedKey(plugin, id.toLowerCase() + "_group"), new ItemStack(Material.EMERALD));
        SlimefunItemStack item = new SlimefunItemStack(id, Material.PAPER);
        ItemStack[] recipe = new ItemStack[9];

        for (int i = 0; i < 9; i++) {
            if (recipeMaterials[i] != null) {
                recipe[i] = new ItemStack(recipeMaterials[i]);
            }
        }

        MultiBlockMachine machine = new MultiBlockMachine(itemGroup, item, recipe, BlockFace.UP) {
            @Override
            public void onInteract(Player p, Block b) {}
        };

        machine.register(plugin);
        return machine;
    }

    @Test
    @DisplayName("A Material-only structure still assembles (backward compatibility)")
    void testMaterialOnlyStructureStillAssembles() {
        Material[] recipeMaterials = new Material[9];
        recipeMaterials[1] = Material.EMERALD_BLOCK;
        recipeMaterials[4] = Material.DIAMOND_BLOCK;
        recipeMaterials[7] = Material.LAPIS_BLOCK;

        MultiBlockMachine machine = registerMachine("ASSEMBLER_MATERIAL_ONLY", recipeMaterials);
        Player player = new PlayerMock(server, "MaterialOnlyPlayer");

        WorldMock world = freshWorld();
        Block base = world.getBlockAt(0, 64, 0);

        boolean built = MultiBlockAssembler.assemble(machine, base, player, EquipmentSlot.HAND);

        Assertions.assertTrue(built);
        Assertions.assertEquals(Material.LAPIS_BLOCK, base.getType());
        Assertions.assertEquals(Material.DIAMOND_BLOCK, base.getRelative(BlockFace.UP).getType());
        Assertions.assertEquals(Material.EMERALD_BLOCK, base.getRelative(BlockFace.UP).getRelative(BlockFace.UP).getType());
    }

    @Test
    @DisplayName("A custom block cell registers the right BlockStorage id when assembled")
    void testCustomBlockCellRegistersId() {
        SlimefunItem customBlock = registerCustomBlock("ASSEMBLER_CUSTOM_BLOCK", Material.IRON_BLOCK);
        Player player = new PlayerMock(server, "CustomBlockPlayer");

        WorldMock world = freshWorld();
        Block center = world.getBlockAt(0, 64, 0);

        Material[] layout = new Material[9];
        String[] customBlocks = new String[9];
        layout[4] = Material.IRON_BLOCK;
        customBlocks[4] = customBlock.getId();

        boolean built = MultiBlockAssembler.assembleAround(layout, customBlocks, center, player);

        Assertions.assertTrue(built);
        Assertions.assertEquals(Material.IRON_BLOCK, center.getType());
        Assertions.assertEquals(customBlock.getId(), BlockStorage.checkID(center));
    }

    @Test
    @DisplayName("A cell already holding the matching custom block is accepted as satisfied")
    void testSatisfiesAcceptsMatchingCustomBlock() {
        SlimefunItem customBlock = registerCustomBlock("ASSEMBLER_MATCHING_BLOCK", Material.GOLD_BLOCK);
        Player player = new PlayerMock(server, "MatchingBlockPlayer");

        WorldMock world = freshWorld();
        Block center = world.getBlockAt(0, 64, 0);
        center.setType(Material.GOLD_BLOCK);
        BlockStorage.addBlockInfo(center, "id", customBlock.getId(), true);

        Material[] layout = new Material[9];
        String[] customBlocks = new String[9];
        layout[4] = Material.GOLD_BLOCK;
        customBlocks[4] = customBlock.getId();

        boolean built = MultiBlockAssembler.assembleAround(layout, customBlocks, center, player);

        Assertions.assertTrue(built);
        Assertions.assertEquals(Material.GOLD_BLOCK, center.getType());
        Assertions.assertEquals(customBlock.getId(), BlockStorage.checkID(center));
    }

    @Test
    @DisplayName("A cell with the right Material but the wrong custom id is rejected")
    void testSatisfiesRejectsMismatchedId() {
        SlimefunItem expected = registerCustomBlock("ASSEMBLER_EXPECTED_BLOCK", Material.NETHERITE_BLOCK);
        SlimefunItem other = registerCustomBlock("ASSEMBLER_OTHER_BLOCK", Material.NETHERITE_BLOCK);
        Player player = new PlayerMock(server, "MismatchedIdPlayer");

        WorldMock world = freshWorld();
        Block center = world.getBlockAt(0, 64, 0);
        center.setType(Material.NETHERITE_BLOCK);
        BlockStorage.addBlockInfo(center, "id", other.getId(), true);

        Material[] layout = new Material[9];
        String[] customBlocks = new String[9];
        layout[4] = Material.NETHERITE_BLOCK;
        customBlocks[4] = expected.getId();

        boolean built = MultiBlockAssembler.assembleAround(layout, customBlocks, center, player);

        Assertions.assertFalse(built);
        // Nothing should have been touched - the mismatched block is left exactly as it was.
        Assertions.assertEquals(other.getId(), BlockStorage.checkID(center));
    }

    @Test
    @DisplayName("An unresolvable custom block id fails cleanly without placing anything")
    void testUnresolvableCustomBlockFailsCleanly() {
        Player player = new PlayerMock(server, "UnresolvablePlayer");

        WorldMock world = freshWorld();
        Block center = world.getBlockAt(0, 64, 0);
        Block above = center.getRelative(BlockFace.UP);

        Material[] layout = new Material[9];
        String[] customBlocks = new String[9];
        // A perfectly buildable Material-only cell alongside the unresolvable one.
        layout[1] = Material.EMERALD_BLOCK;
        layout[4] = Material.IRON_BLOCK;
        customBlocks[4] = "ASSEMBLER_TOTALLY_UNKNOWN_ITEM_ID";

        boolean built = MultiBlockAssembler.assembleAround(layout, customBlocks, center, player);

        Assertions.assertFalse(built);
        Assertions.assertEquals(Material.AIR, center.getType());
        Assertions.assertEquals(Material.AIR, above.getType());
    }
}
