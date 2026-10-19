package io.github.thebusybiscuit.slimefun5.core.checks.scenarios;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
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

import io.github.thebusybiscuit.slimefun5.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun5.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun5.core.checks.Check;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckContext;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckRunner;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckStep;
import io.github.thebusybiscuit.slimefun5.core.checks.Findings;
import io.github.thebusybiscuit.slimefun5.core.checks.Stage;
import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.Sandbox;
import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.SandboxPlayer;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.implementation.listeners.BlockListener;
import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;

import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
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
    void aMachineWithATickerIsTickedOnceBeforeItsMenuOpens() {
        List<String> events = new ArrayList<>();
        tickingMachine("MENU_CHECKS_TICKING", events, () -> events.add("tick"));
        Findings findings = new Findings(line -> {});

        run(placingCheck(), findings);

        Assertions.assertEquals(0, findings.count(), () -> "unexpected findings: " + findings.lines());
        Assertions.assertEquals(Arrays.asList("tick", "open"), events);
    }

    @Test
    void theSharedPerTypeTickIsNeverRunNorItsFlagTouched() throws ReflectiveOperationException {
        List<String> events = new ArrayList<>();
        BlockTicker ticker = tickingMachine("MENU_CHECKS_SHARED", events, () -> events.add("tick"));
        Field uniqueFlag = BlockTicker.class.getDeclaredField("unique");
        uniqueFlag.setAccessible(true);
        uniqueFlag.setBoolean(ticker, false);

        run(placingCheck(), new Findings(line -> {}));

        Assertions.assertFalse(events.contains("uniqueTick"), () -> "events: " + events);
        Assertions.assertFalse(uniqueFlag.getBoolean(ticker));
        uniqueFlag.setBoolean(ticker, true);
        run(placingCheck(), new Findings(line -> {}));
        Assertions.assertFalse(events.contains("uniqueTick"), () -> "events: " + events);
        Assertions.assertTrue(uniqueFlag.getBoolean(ticker));
    }

    @Test
    void aTickerThatThrowsBecomesOneFindingAndNoErrorReport() {
        tickingMachine("MENU_CHECKS_THROWING", new ArrayList<>(), () -> {
            throw new IllegalStateException("broken machine");
        });
        Findings findings = new Findings(line -> {});

        run(placingCheck(), findings);

        Assertions.assertEquals(1, findings.count(), () -> "findings: " + findings.lines());
        Assertions.assertTrue(findings.lines().stream().anyMatch(line -> line.startsWith("[check:machine-placement] MENU_CHECKS_THROWING: ticking it the first time threw java.lang.IllegalStateException: broken machine")),
            () -> "findings: " + findings.lines());
        String[] reports = new File(Slimefun.instance().getDataFolder(), "error-reports").list();
        Assertions.assertTrue(reports == null || reports.length == 0, () -> "error reports: " + Arrays.toString(reports));
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

    private static Check placingCheck() {
        return new Check() {

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
                StepQueue steps = new StepQueue();
                MachinePlacement.in(context.sandbox()).planInto(steps, context);
                return steps;
            }
        };
    }

    /**
     * A placeable machine with a menu whose ticker runs {@code onTick}, recording when its menu is opened.
     *
     * @implNote A unit test server starts Slimefun without its listeners and with tickers off, and placing a
     *           ticking machine needs both.
     */
    private static BlockTicker tickingMachine(String id, List<String> events, Runnable onTick) {
        Slimefun plugin = Slimefun.instance();
        new BlockListener(plugin);
        Slimefun.getCfg().setValue("URID.enable-tickers", true);
        ItemGroup itemGroup = new ItemGroup(new NamespacedKey(plugin, id.toLowerCase(Locale.ROOT)), new ItemStack(Material.IRON_BLOCK));
        SlimefunItem item = new SlimefunItem(itemGroup, new SlimefunItemStack(id, Material.IRON_BLOCK), RecipeType.NULL, new ItemStack[9]);
        BlockTicker ticker = new BlockTicker() {

            @Override
            public boolean isSynchronized() {
                return true;
            }

            @Override
            public void uniqueTick() {
                events.add("uniqueTick");
            }

            @Override
            public void tick(Block block, SlimefunItem ticked, Config data) {
                onTick.run();
            }
        };
        item.addItemHandler(ticker);
        item.register(plugin);
        item.load();
        new BlockMenuPreset(id, "Ticking machine", false) {

            @Override
            public void init() {
                addItem(0, new ItemStack(Material.STONE), (player, slot, item, action) -> false);
                addMenuOpeningHandler(player -> events.add("open"));
            }

            @Override
            public boolean canOpen(Block block, Player player) {
                return true;
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                return new int[0];
            }
        };
        return ticker;
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
