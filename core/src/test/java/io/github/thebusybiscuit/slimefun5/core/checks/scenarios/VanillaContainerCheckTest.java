package io.github.thebusybiscuit.slimefun5.core.checks.scenarios;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import io.github.thebusybiscuit.slimefun5.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun5.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckContext;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckStep;
import io.github.thebusybiscuit.slimefun5.core.checks.Findings;
import io.github.thebusybiscuit.slimefun5.core.checks.Stage;
import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.Sandbox;
import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.VoidWorld;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;

import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;

class VanillaContainerCheckTest {

    private Slimefun plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(Slimefun.class);
        Slimefun.getRegistry().getMenuPresets().clear();
    }

    @AfterEach
    void tearDown() {
        Sandbox.closeOpen();
        MockBukkit.unmock();
    }

    @Test
    void onlyMachinesThatOpenAsVanillaContainersAreChecked() {
        register("VANILLA_CHECK_FURNACE", Material.FURNACE);
        register("VANILLA_CHECK_HOPPER", Material.HOPPER);
        register("VANILLA_CHECK_STONE", Material.STONE);
        register("VANILLA_CHECK_MENU_CHEST", Material.CHEST);
        new BlockMenuPreset("VANILLA_CHECK_MENU_CHEST", "Menu chest") {

            @Override
            public void init() {}

            @Override
            public boolean canOpen(Block block, Player player) {
                return true;
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                return new int[0];
            }
        };

        List<String> checked = VanillaContainerCheck.vanillaContainerMachines().stream().map(SlimefunItem::getId).filter(id -> id.startsWith("VANILLA_CHECK_")).collect(Collectors.toList());

        Assertions.assertEquals(Arrays.asList("VANILLA_CHECK_FURNACE", "VANILLA_CHECK_HOPPER"), checked);
    }

    @Test
    void harnessRunRecordsASkipAndMakesNoWorld() {
        register("VANILLA_CHECK_FURNACE", Material.FURNACE);
        Findings findings = new Findings(line -> {});

        Iterator<CheckStep> steps = new VanillaContainerCheck().steps(new CheckContext(findings, Stage.HIGH, false));

        Assertions.assertFalse(steps.hasNext());

        Assertions.assertEquals(0, findings.count(), () -> "a skip must not be a problem: " + findings.lines());
        Assertions.assertTrue(findings.lines().contains("[check:vanilla-container] skipped: a real world can only be created on a running server"), () -> findings.lines().toString());
        Assertions.assertNull(Bukkit.getWorld(VoidWorld.NAME));
    }

    private void register(String id, Material type) {
        ItemGroup group = new ItemGroup(new NamespacedKey(plugin, "vanilla_check"), new ItemStack(Material.EMERALD));
        new SlimefunItem(group, new SlimefunItemStack(id, type, "&fVanilla check"), RecipeType.NULL, new ItemStack[9]).register(plugin);
    }
}
