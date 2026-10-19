package io.github.thebusybiscuit.slimefun5.core.checks.scenarios;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.annotation.Nonnull;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckContext;
import io.github.thebusybiscuit.slimefun5.core.checks.Findings;
import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.Sandbox;
import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.SandboxPlayer;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;

/**
 * Places every machine that has a menu in the sandbox the way a player does, once per sandbox, and keeps the
 * menus Slimefun made for them for every check that needs one.
 */
final class MachinePlacement {

    static final String CHECK = "machine-placement";

    private static final int Y = 64;
    private static final int ROW = 32;
    /** Slimefun fills a new machine's menu one tick after placing it ({@code BlockMenuPreset#newInstance} runs through {@code runSync}). */
    private static final int MENU_FILL_TICKS = 2;
    private static final long PROFILE_POLL_MILLIS = 5;

    private final Sandbox sandbox;
    private final Map<BlockMenuPreset, DirtyChestMenu> created = new LinkedHashMap<>();
    private final Map<BlockMenuPreset, Block> placedBlocks = new LinkedHashMap<>();
    private final List<PlacedMenu> menus = new ArrayList<>();
    private ResearchedPlayer placer;
    private boolean planned;

    private MachinePlacement(Sandbox sandbox) {
        this.sandbox = sandbox;
    }

    @Nonnull
    static MachinePlacement in(@Nonnull Sandbox sandbox) {
        return sandbox.shared(MachinePlacement.class, () -> new MachinePlacement(sandbox));
    }

    /** A machine standing in the sandbox with the menu Slimefun made for it. */
    static final class PlacedMenu {

        final BlockMenuPreset preset;
        final DirtyChestMenu menu;
        private final int[] storageSlots;
        private final int[] fillableSlots;

        PlacedMenu(BlockMenuPreset preset, DirtyChestMenu menu) {
            this.preset = preset;
            this.menu = menu;
            TreeSet<Integer> declared = declaredSlots(preset, menu);
            TreeSet<Integer> storage = new TreeSet<>(declared);
            TreeSet<Integer> fillable = new TreeSet<>(declared);
            for (int slot : preset.getInventorySlots()) {
                ItemStack item = menu.getItemInSlot(slot);
                if (item == null || item.getType() == Material.AIR) {
                    storage.add(slot);
                    if (menu.getMenuClickHandler(slot) == null) {
                        fillable.add(slot);
                    }
                }
            }
            this.storageSlots = toArray(storage);
            this.fillableSlots = toArray(fillable);
        }

        /**
         * Where the machine's own items can be: every slot outside the preset's layout that is empty once the
         * machine has been placed and opened, plus whatever it declares to cargo.
         *
         * @implNote Emptiness is what tells storage from a button the machine draws per block (an assembler's
         *           offset, a cargo node's channel), since both sit outside the preset's own layout.
         */
        int[] storageSlots() {
            return storageSlots;
        }

        /**
         * The storage slots an item can really get into: those with no click handler, which a player fills,
         * and those declared to cargo, which machines and cargo fill. A take-only slot such as a result preview
         * is only ever filled by the machine itself.
         */
        int[] fillableSlots() {
            return fillableSlots;
        }

        private static TreeSet<Integer> declaredSlots(BlockMenuPreset preset, DirtyChestMenu menu) {
            TreeSet<Integer> slots = new TreeSet<>();
            ItemStack sample = new ItemStack(Material.IRON_INGOT);
            for (ItemTransportFlow flow : ItemTransportFlow.values()) {
                addAll(slots, preset.getSlotsAccessedByItemTransport(flow));
                addAll(slots, preset.getSlotsAccessedByItemTransport(menu, flow, sample));
            }
            return slots;
        }

        private static void addAll(TreeSet<Integer> slots, int[] declared) {
            if (declared != null) {
                for (int slot : declared) {
                    slots.add(slot);
                }
            }
        }

        private static int[] toArray(TreeSet<Integer> slots) {
            return slots.stream().mapToInt(Integer::intValue).toArray();
        }
    }

    /** Every machine menu placed so far, in id order; all of them once the planned steps have run. */
    @Nonnull
    List<PlacedMenu> menus() {
        return Collections.unmodifiableList(menus);
    }

    /**
     * Adds the steps that place every machine, one per machine, unless an earlier check in this sandbox has.
     * A universal menu is skipped: it is one inventory shared by every block of its kind in every world, so
     * the sandbox refuses it.
     */
    void planInto(@Nonnull StepQueue steps, @Nonnull CheckContext context) {
        if (planned) {
            return;
        }
        planned = true;
        List<BlockMenuPreset> placeable = new ArrayList<>();
        for (Map.Entry<String, BlockMenuPreset> entry : new TreeMap<>(Slimefun.getRegistry().getMenuPresets()).entrySet()) {
            if (entry.getValue().isUniversal()) {
                context.findings().recordSkipped(CHECK, entry.getKey() + " is a universal menu, shared by every world, which the sandbox refuses");
            } else {
                placeable.add(entry.getValue());
            }
        }
        steps.then(() -> {
            placer = ResearchedPlayer.join(sandbox, "placer", "en_us");
            placer.player().setOp(true);
            return 0;
        });
        steps.thenWaitUntil(() -> isPlacerReady(context));
        for (int index = 0; index < placeable.size(); index++) {
            BlockMenuPreset preset = placeable.get(index);
            int position = index;
            steps.then(() -> {
                placeOne(context, preset, position);
                return 0;
            });
        }
        steps.then(() -> MENU_FILL_TICKS);
        for (BlockMenuPreset preset : placeable) {
            steps.then(() -> {
                tickOnce(context, preset);
                return 0;
            });
        }
        for (BlockMenuPreset preset : placeable) {
            steps.then(() -> {
                openFirst(context, preset);
                return 0;
            });
        }
        steps.then(() -> {
            sandbox.quit(placer.player());
            return 0;
        });
    }

    /**
     * @implNote On the harness's virtual clock a tick takes no time while the profile loads in real time, so each
     *           try there pauses briefly instead of racing through ticks.
     */
    private boolean isPlacerReady(CheckContext context) throws InterruptedException {
        if (placer.isReady()) {
            return true;
        }
        if (!context.live()) {
            Thread.sleep(PROFILE_POLL_MILLIS);
        }
        return false;
    }

    private void placeOne(CheckContext context, BlockMenuPreset preset, int position) {
        String id = preset.getID();
        context.describe(CHECK + " " + id);
        Findings findings = context.findings();
        try {
            Block block = blockAt(position);
            DirtyChestMenu menu = place(placer.player(), id, block);
            if (menu == null) {
                findings.report(CHECK, id, "placing it created no menu");
            } else {
                created.put(preset, menu);
                placedBlocks.put(preset, block);
            }
        } catch (IllegalStateException refused) {
            findings.report(CHECK, id, refused.getMessage());
        } catch (RuntimeException | LinkageError crash) {
            findings.report(CHECK, id, "placing it threw " + crash);
        }
    }

    /**
     * Runs the machine's ticker once, as one ticker cycle would, so its menu is in the state a ticked machine
     * shows before it is opened.
     *
     * @implNote The real ticker never ticks a sandbox block, so the tick is called here, on the main thread even
     *           for an asynchronous ticker, and a failure becomes a finding rather than an error report on disk.
     */
    private void tickOnce(CheckContext context, BlockMenuPreset preset) {
        Block block = placedBlocks.get(preset);
        SlimefunItem item = block == null ? null : SlimefunItem.getById(preset.getID());
        BlockTicker ticker = item == null ? null : item.getBlockTicker();
        if (ticker == null) {
            return;
        }
        context.describe(CHECK + " " + preset.getID() + ": first tick");
        try {
            ticker.update();
            ticker.tick(block, item, BlockStorage.getLocationInfo(block.getLocation()));
        } catch (Throwable crash) {
            context.findings().report(CHECK, preset.getID(), "ticking it the first time threw " + crash);
        } finally {
            ticker.startNewTick();
        }
    }

    private void openFirst(CheckContext context, BlockMenuPreset preset) {
        DirtyChestMenu menu = created.get(preset);
        if (menu == null) {
            return;
        }
        context.describe(CHECK + " " + preset.getID() + ": first open");
        try {
            menu.open(placer.player().player());
            placer.player().player().closeInventory();
            menus.add(new PlacedMenu(preset, menu));
        } catch (RuntimeException | LinkageError crash) {
            context.findings().report(CHECK, preset.getID(), "opening it the first time threw " + crash);
        }
    }

    private Block blockAt(int position) {
        return sandbox.world().blockAt((position % ROW) * 2, Y, (position / ROW) * 2);
    }

    /**
     * @implNote A preset with no item of its own (an addon's internal menu) cannot be placed by a player, so it
     *           is recorded directly, which is what placing does once the place event has passed.
     */
    private DirtyChestMenu place(SandboxPlayer player, String id, Block block) {
        SlimefunItem item = SlimefunItem.getById(id);
        if (item == null) {
            block.setType(Material.STONE);
            BlockStorage.addBlockInfo(block, "id", id, true);
        } else {
            int messagesBefore = player.messages().size();
            if (!player.place(block, BlockFace.WEST, item.getItem().clone(), blockTypeOf(item.getItem().getType()))) {
                List<String> told = player.messages().subList(messagesBefore, player.messages().size());
                throw new IllegalStateException("its place event was cancelled" + (told.isEmpty() ? "" : ", telling the player " + told));
            }
        }
        return BlockStorage.getInventory(block);
    }

    /** What placing an item of this material puts in the world. */
    private static Material blockTypeOf(Material itemType) {
        if (itemType.isBlock()) {
            return itemType;
        }
        Material legacySkull = Material.getMaterial("SKULL");
        return itemType.name().equals("SKULL_ITEM") && legacySkull != null ? legacySkull : Material.STONE;
    }
}
