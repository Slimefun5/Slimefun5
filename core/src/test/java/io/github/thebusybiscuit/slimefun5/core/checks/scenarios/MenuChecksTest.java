package io.github.thebusybiscuit.slimefun5.core.checks.scenarios;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import io.github.thebusybiscuit.slimefun5.core.checks.Check;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckContext;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckRunner;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckStep;
import io.github.thebusybiscuit.slimefun5.core.checks.Findings;
import io.github.thebusybiscuit.slimefun5.core.checks.Stage;
import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.Sandbox;
import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.SandboxPlayer;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;

/**
 * @implNote Clicks and titles are left to the version harness: core is compiled against the 1.8.8 API, where
 *           {@code InventoryView} is a class, and MockBukkit's is an interface without the bytecode rewriting a
 *           real server applies to plugins, so any call on a view fails here.
 */
class MenuChecksTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.load(Slimefun.class);
        Slimefun.getRegistry().getMenuPresets().clear();
    }

    @AfterEach
    void tearDown() {
        Sandbox.closeOpen();
        MockBukkit.unmock();
    }

    @Test
    void everyMachineIsPlacedOnceAndItsStorageFound() {
        preset("MENU_CHECKS_MACHINE", false);
        Findings findings = new Findings(line -> {});
        List<MachinePlacement.PlacedMenu> placed = new ArrayList<>();

        run(new Check() {

            @Override
            public String name() {
                return "placing";
            }

            @Override
            public Stage stage() {
                return Stage.MEDIUM;
            }

            @Override
            public Iterator<CheckStep> steps(CheckContext context) {
                MachinePlacement placement = MachinePlacement.in(context.sandbox());
                StepQueue steps = new StepQueue();
                placement.planInto(steps, context);
                placement.planInto(steps, context);
                return steps.then(() -> {
                    placed.addAll(placement.menus());
                    return 0;
                });
            }
        }, findings);

        Assertions.assertEquals(0, findings.count(), () -> "unexpected findings: " + findings.lines());
        Assertions.assertEquals(1, placed.size());
        Assertions.assertArrayEquals(new int[] {1, 2, 3, 5, 6, 7, 8}, placed.get(0).storageSlots());
        Assertions.assertArrayEquals(new int[] {1, 2, 3, 5, 6, 7, 8}, placed.get(0).fillableSlots());
        Assertions.assertFalse(Sandbox.isOpen());
    }

    @Test
    void universalMenusAreSkippedRatherThanRefused() {
        preset("MENU_CHECKS_UNIVERSAL", true);
        Findings findings = new Findings(line -> {});

        run(new MenuClickCheck(), findings);

        Assertions.assertEquals(0, findings.count(), () -> "a skip must not be a problem: " + findings.lines());
        Assertions.assertTrue(findings.lines().contains("[check:machine-placement] skipped: MENU_CHECKS_UNIVERSAL is a universal menu, shared by every world, which the sandbox refuses"),
            () -> "no skip line: " + findings.lines());
    }

    @Test
    void sandboxPlayersAreOnlineOnlyWhileTheSandboxIsOpen() {
        UUID id;
        try (Sandbox sandbox = Sandbox.open(true)) {
            SandboxPlayer joined = sandbox.join("online_check", "en_us");
            id = joined.player().getUniqueId();

            Assertions.assertEquals(Collections.singletonList(joined.player()), Sandbox.onlinePlayers());
            Assertions.assertSame(joined.player(), Sandbox.onlinePlayer(id));
            Assertions.assertSame(joined.player(), Sandbox.onlinePlayer("ONLINE_CHECK"));
        }

        Assertions.assertTrue(Sandbox.onlinePlayers().isEmpty());
        Assertions.assertNull(Sandbox.onlinePlayer(id));
    }

    private void run(Check check, Findings findings) {
        CheckContext context = new CheckContext(findings, Stage.HIGH, true);
        new CheckRunner(Collections.singletonList(check)).runBlocking(Stage.HIGH, context, ticks -> server.getScheduler().performTicks(ticks));
    }

    /** A one-row menu with buttons in slots 0 and 4 and the last four slots declared to cargo. */
    private static void preset(String id, boolean universal) {
        new BlockMenuPreset(id, "Menu checks test", universal) {

            @Override
            public void init() {
                addItem(0, new ItemStack(Material.STONE), (player, slot, item, action) -> false);
                addItem(4, new ItemStack(Material.GLASS), (player, slot, item, action) -> false);
            }

            @Override
            public boolean canOpen(Block block, Player player) {
                return true;
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                return new int[] {5, 6, 7, 8};
            }
        };
    }
}
