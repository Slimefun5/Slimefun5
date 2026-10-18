package io.github.thebusybiscuit.slimefun5.core.checks.scenarios;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import io.github.thebusybiscuit.slimefun5.core.checks.Check;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckContext;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckStep;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckThread;
import io.github.thebusybiscuit.slimefun5.core.checks.Stage;
import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.InventoryClicks;
import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.ItemTally;
import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.Sandbox;
import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.SandboxPlayer;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * Clicks every slot of every machine menu with every click type, once with the machine's storage full and
 * the cursor empty and once the other way round, and fails when a click changes how many items exist: what
 * the player holds, the cursor, what was dropped, and the machine's own storage.
 *
 * @implNote Decorative and button slots are left out of the count because a button may re-render itself;
 *           an item taken out of one still shows, as a gain on the player's side. The totals are logged only
 *           off a live server, as the harness's record of how much ran; a live run reports through its findings
 *           alone.
 */
public final class MenuClickCheck implements Check {

    private static final String NAME = "menu-click";
    private static final int HOTBAR_BUTTON = 1;
    private static final int REPORTS_PER_MENU = 5;
    private static final int TICKS_BETWEEN_MENUS = 1;
    private static final List<ClickType> CLICKS = Arrays.asList(
        ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.NUMBER_KEY, ClickType.DROP, ClickType.DOUBLE_CLICK);

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Stage stage() {
        return Stage.HIGH;
    }

    @Override
    public CheckThread thread() {
        return CheckThread.MAIN;
    }

    @Override
    public Iterator<CheckStep> steps(CheckContext context) {
        Sandbox sandbox = context.sandbox();
        MachinePlacement placement = MachinePlacement.in(sandbox);
        StepQueue steps = new StepQueue();
        placement.planInto(steps, context);
        AtomicReference<SandboxPlayer> clicker = new AtomicReference<>();
        AtomicInteger clicks = new AtomicInteger();
        AtomicInteger problems = new AtomicInteger();
        steps.then(() -> {
            clicker.set(sandbox.join("clicker", "en_us"));
            return 0;
        });
        steps.thenPlanned(() -> {
            List<CheckStep> planned = new ArrayList<>();
            for (MachinePlacement.PlacedMenu placed : placement.menus()) {
                MenuClicks menu = new MenuClicks(placed, clicker.get(), sandbox, context);
                int[] rawSlots = rawSlotsToClick(placed.menu.toInventory().getSize());
                for (boolean storageFull : new boolean[] {true, false}) {
                    for (int rawSlot : rawSlots) {
                        planned.add(() -> {
                            clicks.addAndGet(menu.clickEveryType(rawSlot, storageFull));
                            return 0;
                        });
                    }
                }
                planned.add(() -> {
                    problems.addAndGet(menu.finish());
                    return TICKS_BETWEEN_MENUS;
                });
            }
            return planned;
        });
        steps.then(() -> {
            sandbox.quit(clicker.get());
            if (!context.live()) {
                Slimefun.logger().info("Menu clicks checked: " + placement.menus().size() + " menus, " + clicks + " clicks, " + problems + " problem(s)");
            }
            return 0;
        });
        return steps;
    }

    /** Every slot of the menu, then one of the player's own slots and one hotbar slot. */
    private static int[] rawSlotsToClick(int topSize) {
        int[] slots = new int[topSize + 2];
        for (int i = 0; i < topSize; i++) {
            slots[i] = i;
        }
        slots[topSize] = topSize;
        slots[topSize + 1] = topSize + 27;
        return slots;
    }

    /** One menu's clicks, reporting the first few problems and then how many more there were. */
    private static final class MenuClicks {

        private final MachinePlacement.PlacedMenu placed;
        private final SandboxPlayer clicker;
        private final Sandbox sandbox;
        private final CheckContext context;
        private final int[] storage;
        private int problems;

        MenuClicks(MachinePlacement.PlacedMenu placed, SandboxPlayer clicker, Sandbox sandbox, CheckContext context) {
            this.placed = placed;
            this.clicker = clicker;
            this.sandbox = sandbox;
            this.context = context;
            this.storage = placed.storageSlots();
        }

        int clickEveryType(int rawSlot, boolean storageFull) {
            String id = placed.preset.getID();
            for (ClickType click : CLICKS) {
                String step = click + " on slot " + rawSlot + (storageFull ? " with the storage full" : " with a stack on the cursor");
                context.describe(NAME + " " + id + ": " + step);
                String problem = clickOnce(storageFull, rawSlot, click);
                if (problem != null && problems++ < REPORTS_PER_MENU) {
                    context.findings().report(NAME, id, step + " " + problem);
                }
            }
            return CLICKS.size();
        }

        /** @return how many findings this menu reported */
        int finish() {
            clicker.player().closeInventory();
            if (problems > REPORTS_PER_MENU) {
                context.findings().report(NAME, placed.preset.getID(), (problems - REPORTS_PER_MENU) + " more click(s) with problems like the ones above");
                return REPORTS_PER_MENU + 1;
            }
            return problems;
        }

        private String clickOnce(boolean storageFull, int rawSlot, ClickType click) {
            Player player = clicker.player();
            try {
                reset(player, storageFull);
                if (!openForClicker()) {
                    return "could not run: the menu did not open";
                }
                ItemTally before = tally(player);
                InventoryClicks.click(player, rawSlot, click, HOTBAR_BUTTON);
                player.closeInventory();
                List<String> changes = tally(player).changesSince(before);
                return changes.isEmpty() ? null : "changed how many items exist: " + String.join(", ", changes);
            } catch (RuntimeException | LinkageError crash) {
                return "threw " + crash;
            }
        }

        private boolean openForClicker() {
            placed.menu.open(clicker.player());
            return clicker.hasWindowOpen() && clicker.player().getOpenInventory().getTopInventory() == placed.menu.toInventory();
        }

        private void reset(Player player, boolean storageFull) {
            player.closeInventory();
            sandbox.world().drops().clear();
            PlayerInventory inventory = player.getInventory();
            inventory.clear();
            inventory.setItem(HOTBAR_BUTTON, new ItemStack(Material.COBBLESTONE, 8));
            inventory.setItem(9, new ItemStack(Material.IRON_INGOT, 8));
            for (int slot : storage) {
                placed.menu.replaceExistingItem(slot, null);
            }
            if (storageFull) {
                for (int slot : placed.fillableSlots()) {
                    placed.menu.replaceExistingItem(slot, new ItemStack(Material.IRON_INGOT, 8));
                }
            }
            player.setItemOnCursor(storageFull ? null : new ItemStack(Material.IRON_INGOT, 8));
        }

        /**
         * @implNote The cursor goes back into the inventory when the window closes, so after a click the count
         *           is taken with the window closed and the cursor is already part of the inventory.
         */
        private ItemTally tally(Player player) {
            ItemTally tally = new ItemTally()
                .addAll(player.getInventory().getContents())
                .add(player.getItemOnCursor())
                .addAll(sandbox.world().drops());
            for (int slot : storage) {
                tally.add(placed.menu.getItemInSlot(slot));
            }
            return tally;
        }
    }
}
