package io.github.thebusybiscuit.slimefun5.core.services;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import io.github.thebusybiscuit.slimefun5.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun5.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun5.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun5.core.multiblocks.MultiBlockMachine;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.implementation.items.SimpleSlimefunItem;
import io.github.thebusybiscuit.slimefun5.implementation.items.blocks.UnplaceableBlock;
import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;

import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.interfaces.InventoryBlock;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;

/**
 * Covers the boot audit that surfaces machines still on a superseded API. The two failures it exists to
 * catch both shipped in game: a multiblock guide entry that no assembler backs (cheating it in hands the
 * player an inert block), and a menu preset that declares no title colour so its GUI title renders gray.
 */
class MachineAuditTest {

    private static Slimefun plugin;

    @TempDir
    File tempDir;

    @BeforeAll
    static void load() {
        MockBukkit.mock();
        plugin = MockBukkit.load(Slimefun.class);
    }

    @AfterAll
    static void unload() {
        MockBukkit.unmock();
    }

    private static ItemGroup group(String id) {
        return new ItemGroup(new NamespacedKey(plugin, id.toLowerCase() + "_group"), new ItemStack(Material.EMERALD));
    }

    private MachineAuditService auditing() {
        MachineAuditService service = new MachineAuditService();
        service.audit(new File(tempDir, "machine-audit.yml"));
        return service;
    }

    private static boolean flagged(Map<String, List<String>> findings, String needle) {
        for (List<String> entries : findings.values()) {
            for (String entry : entries) {
                if (entry.startsWith(needle)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Test
    @DisplayName("A MULTIBLOCK recipe type on a non-MultiBlockMachine is flagged as unassemblable")
    void testUnassemblableMultiblockIsFlagged() {
        SlimefunItem dummy = new SlimefunItem(group("AUDIT_FAKE_MULTI"), new SlimefunItemStack("AUDIT_FAKE_MULTI", Material.BRICKS), RecipeType.MULTIBLOCK, new ItemStack[9]);
        dummy.register(plugin);

        Assertions.assertTrue(flagged(auditing().getFindings(MachineAuditService.KEY_UNASSEMBLABLE), "AUDIT_FAKE_MULTI"));
    }

    @Test
    @DisplayName("A real MultiBlockMachine is not flagged")
    void testRealMultiBlockMachineIsNotFlagged() {
        MultiBlockMachine machine = new MultiBlockMachine(group("AUDIT_REAL_MULTI"), new SlimefunItemStack("AUDIT_REAL_MULTI", Material.PAPER), new ItemStack[9], BlockFace.UP) {
            @Override
            public void onInteract(Player p, Block b) {}
        };
        machine.register(plugin);

        Assertions.assertFalse(flagged(auditing().getFindings(MachineAuditService.KEY_UNASSEMBLABLE), "AUDIT_REAL_MULTI"));
    }

    @Test
    @DisplayName("An UnplaceableBlock multiblock is flagged - its handler only cancels the click")
    void testInertPlaceholderIsFlagged() {
        UnplaceableBlock inert = new UnplaceableBlock(group("AUDIT_INERT_MULTI"), new SlimefunItemStack("AUDIT_INERT_MULTI", Material.BRICKS), RecipeType.MULTIBLOCK, new ItemStack[9]);
        inert.register(plugin);

        Assertions.assertTrue(flagged(auditing().getFindings(MachineAuditService.KEY_UNASSEMBLABLE), "AUDIT_INERT_MULTI"));
    }

    @Test
    @DisplayName("A multiblock that assembles itself through its own use handler is not flagged")
    void testSelfAssemblingMultiblockIsNotFlagged() {
        SimpleSlimefunItem<ItemUseHandler> selfAssembling = new SimpleSlimefunItem<ItemUseHandler>(group("AUDIT_SELF_MULTI"), new SlimefunItemStack("AUDIT_SELF_MULTI", Material.BRICKS), RecipeType.MULTIBLOCK, new ItemStack[9]) {
            @Override
            public ItemUseHandler getItemHandler() {
                return e -> e.cancel();
            }
        };
        selfAssembling.register(plugin);

        Assertions.assertFalse(flagged(auditing().getFindings(MachineAuditService.KEY_UNASSEMBLABLE), "AUDIT_SELF_MULTI"));
    }

    @Test
    @DisplayName("A preset with a declared header slot or title colour is not flagged, one without is")
    void testDeprecatedMenuDetection() {
        AuditedMenuBlock declared = new AuditedMenuBlock("AUDIT_MENU_DECLARED", true);
        declared.register(plugin);
        declared.build();

        AuditedMenuBlock guessed = new AuditedMenuBlock("AUDIT_MENU_GUESSED", false);
        guessed.register(plugin);
        guessed.build();

        Map<String, List<String>> findings = auditing().getFindings(MachineAuditService.KEY_DEPRECATED_MENU);

        Assertions.assertFalse(flagged(findings, "AUDIT_MENU_DECLARED"));
        Assertions.assertTrue(flagged(findings, "AUDIT_MENU_GUESSED"));
    }

    private static class AuditedMenuBlock extends SlimefunItem implements InventoryBlock {

        private final boolean declaresTitleColor;

        AuditedMenuBlock(String id, boolean declaresTitleColor) {
            super(group(id), new SlimefunItemStack(id, Material.DISPENSER), RecipeType.NULL, new ItemStack[9]);
            this.declaresTitleColor = declaresTitleColor;
        }

        @SuppressWarnings("deprecation")
        void build() {
            if (declaresTitleColor) {
                createPreset(this, getId(), ChatColor.AQUA, (BlockMenuPreset preset) -> {});
            } else {
                createPreset(this, (BlockMenuPreset preset) -> {});
            }
        }

        @Override
        public int[] getInputSlots() {
            return new int[0];
        }

        @Override
        public int[] getOutputSlots() {
            return new int[0];
        }
    }
}
