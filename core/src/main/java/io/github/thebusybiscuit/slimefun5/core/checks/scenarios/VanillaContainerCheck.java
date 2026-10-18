package io.github.thebusybiscuit.slimefun5.core.checks.scenarios;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.core.attributes.NotPlaceable;
import io.github.thebusybiscuit.slimefun5.core.checks.Check;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckContext;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckStep;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckThread;
import io.github.thebusybiscuit.slimefun5.core.checks.Stage;
import io.github.thebusybiscuit.slimefun5.core.checks.packets.FedPackets;
import io.github.thebusybiscuit.slimefun5.core.checks.packets.TextProblems;
import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.Sandbox;
import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.SandboxPlayer;
import io.github.thebusybiscuit.slimefun5.core.multiblocks.MultiBlockMachine;
import io.github.thebusybiscuit.slimefun5.core.services.localization.Language;
import io.github.thebusybiscuit.slimefun5.core.services.localization.PacketTranslationService;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.implementation.listeners.VanillaContainerTitleListener;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.PdcCompat;

import me.mrCookieSlime.Slimefun.api.BlockStorage;

/**
 * Places every Slimefun machine whose block is a vanilla container (the Enhanced Furnace family, an output
 * chest) as a real block in the sandbox's {@link io.github.thebusybiscuit.slimefun5.core.checks.sandbox.VoidWorld},
 * opens its real inventory for one viewer per language and reads the title their window shows: never blank,
 * never a raw id, never an unfilled placeholder, and always the title core resolves for that viewer.
 *
 * @implNote Creating the world, and unloading and deleting it, are each one step that takes far longer than the
 *           5 ms a sliced live step is given (seconds the first time); no other step of the run does. The harness
 *           has no running server to create a real world in, so there the check records a skip. The inventory
 *           is opened directly rather than by a right-click, so a machine that handles the click itself is
 *           checked as vanilla shows it.
 */
public final class VanillaContainerCheck implements Check {

    private static final String NAME = "vanilla-container";

    private static final Set<String> CONTAINER_ITEMS = new HashSet<>(Arrays.asList("FURNACE", "BLAST_FURNACE", "SMOKER", "DISPENSER",
            "DROPPER", "HOPPER", "CHEST", "TRAPPED_CHEST", "BARREL", "BREWING_STAND", "BREWING_STAND_ITEM"));
    private static final String LEGACY_BREWING_STAND_ITEM = "BREWING_STAND_ITEM";

    private static final int Y = 64;
    private static final int SPACING = 2;
    /** The title listener's fallback retitles one tick after the window opens. */
    private static final int RETITLE_TICKS = 2;
    private static final int FED_WINDOW_ID = 1;

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
        StepQueue steps = new StepQueue();
        List<SlimefunItem> machines = vanillaContainerMachines();
        if (machines.isEmpty()) {
            return steps;
        }
        if (!context.live()) {
            context.findings().recordSkipped(NAME, "a real world can only be created on a running server");
            return steps;
        }
        new Run(context, machines).planInto(steps);
        return steps;
    }

    /** Every enabled machine a player places as a vanilla container that opens as one, in id order. */
    @Nonnull
    static List<SlimefunItem> vanillaContainerMachines() {
        Map<String, ?> menus = Slimefun.getRegistry().getMenuPresets();
        List<SlimefunItem> machines = new ArrayList<>();
        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            if (item != null && !item.isDisabled() && !(item instanceof NotPlaceable) && !(item instanceof MultiBlockMachine)
                    && !menus.containsKey(item.getId()) && CONTAINER_ITEMS.contains(item.getItem().getType().name())) {
                machines.add(item);
            }
        }
        machines.sort(Comparator.comparing(SlimefunItem::getId));
        return machines;
    }

    private static Material blockTypeOf(Material itemType) {
        Material standBlock = Material.getMaterial("BREWING_STAND");
        return itemType.name().equals(LEGACY_BREWING_STAND_ITEM) && standBlock != null ? standBlock : itemType;
    }

    /** One run of the check: the world, the players and what each machine showed. */
    private static final class Run {

        private final CheckContext context;
        private final Sandbox sandbox;
        private final List<SlimefunItem> machines;
        private final Optional<FedPackets> fed = FedPackets.forRunningVersion();
        private final Map<String, SandboxPlayer> viewers = new LinkedHashMap<>();
        private final Map<SlimefunItem, Block> placed = new LinkedHashMap<>();
        private ResearchedPlayer placer;

        Run(CheckContext context, List<SlimefunItem> machines) {
            this.context = context;
            this.sandbox = context.sandbox();
            this.machines = machines;
        }

        void planInto(StepQueue steps) {
            steps.then(() -> {
                sandbox.openVoidWorld();
                return 0;
            });
            steps.then(() -> {
                placer = ResearchedPlayer.join(sandbox, "container_placer", "en_us");
                placer.player().setOp(true);
                return 0;
            });
            steps.thenWaitUntil(() -> placer.isReady());
            for (int index = 0; index < machines.size(); index++) {
                SlimefunItem machine = machines.get(index);
                int position = index;
                steps.then(() -> {
                    placeOne(machine, position);
                    return 0;
                });
            }
            steps.then(() -> {
                sandbox.quit(placer.player());
                joinViewerPerLanguage();
                return 0;
            });
            steps.thenPlanned(this::titleSteps);
            steps.then(() -> {
                viewers.values().forEach(sandbox::quit);
                for (String failure : sandbox.closeVoidWorld()) {
                    context.findings().report(NAME, "the checks' world", "could not be removed: " + failure);
                }
                return 0;
            });
        }

        private void placeOne(SlimefunItem machine, int position) {
            String id = machine.getId();
            context.describe(NAME + " " + id + ": placing");
            Block block = sandbox.openVoidWorld().world().getBlockAt(position * SPACING, Y, 0);
            try {
                SandboxPlayer player = placer.player();
                int messagesBefore = player.messages().size();
                if (!player.place(block, BlockFace.WEST, machine.getItem().clone(), blockTypeOf(machine.getItem().getType()))) {
                    List<String> told = player.messages().subList(messagesBefore, player.messages().size());
                    context.findings().report(NAME, id, "its place event was cancelled" + (told.isEmpty() ? "" : ", telling the player " + told));
                } else if (BlockStorage.check(block) != machine) {
                    context.findings().report(NAME, id, "placing it did not record it as a Slimefun block");
                } else {
                    placed.put(machine, block);
                }
            } catch (RuntimeException | LinkageError crash) {
                context.findings().report(NAME, id, "placing it threw " + crash);
            }
        }

        private void joinViewerPerLanguage() {
            for (Language language : Slimefun.getLocalization().getLanguages()) {
                SandboxPlayer viewer = sandbox.join("container_" + language.getId(), language.getId());
                PdcCompat.set(viewer.player(), Slimefun.getLocalization().getKey(), "STRING", language.getId());
                viewers.put(language.getId(), viewer);
            }
        }

        private List<CheckStep> titleSteps() {
            List<CheckStep> planned = new ArrayList<>();
            for (Map.Entry<SlimefunItem, Block> machine : placed.entrySet()) {
                ContainerTitles titles = new ContainerTitles(machine.getKey().getId(), machine.getValue());
                for (Map.Entry<String, SandboxPlayer> viewer : viewers.entrySet()) {
                    planned.add(() -> {
                        context.describe(NAME + " " + titles.id + " for " + viewer.getKey());
                        titles.open(viewer.getKey(), viewer.getValue());
                        return 0;
                    });
                }
                planned.add(() -> titles.awaitsRetitle() ? RETITLE_TICKS : 0);
                planned.add(() -> {
                    titles.readRetitled();
                    titles.report(viewers.size());
                    return 0;
                });
            }
            return planned;
        }

        /** One machine's title problems, each with the languages it showed in. */
        private final class ContainerTitles {

            private final String id;
            private final Block block;
            private final Map<String, SandboxPlayer> awaitingRetitle = new LinkedHashMap<>();
            private final Map<String, List<String>> languagesByProblem = new LinkedHashMap<>();

            ContainerTitles(String id, Block block) {
                this.id = id;
                this.block = block;
            }

            boolean awaitsRetitle() {
                return !awaitingRetitle.isEmpty();
            }

            /**
             * Opens the container for the viewer and reads the title at once when core retitles it by packet;
             * otherwise the window stays open until the listener's tick-later retitle has run.
             */
            void open(String language, SandboxPlayer viewer) {
                Player player = viewer.player();
                boolean keepOpen = false;
                try {
                    Inventory inventory = inventoryOf(block);
                    if (inventory == null) {
                        record("is no container once placed", language);
                        return;
                    }
                    player.openInventory(inventory);
                    if (!viewer.hasWindowOpen()) {
                        record("does not open", language);
                        return;
                    }
                    PacketTranslationService packets = Slimefun.getPacketTranslationService();
                    if (packets != null && packets.hasPendingWindowTitle(player)) {
                        record(fed.isPresent() ? problemWith(titleSentBy(packets, player), language) : "is titled by the open-window packet, which this server's packets cannot be built to check", language);
                    } else {
                        awaitingRetitle.put(language, viewer);
                        keepOpen = true;
                    }
                } catch (RuntimeException | LinkageError crash) {
                    record("threw " + crash + " when opened", language);
                } finally {
                    if (!keepOpen) {
                        close(player);
                    }
                }
            }

            void readRetitled() {
                for (Map.Entry<String, SandboxPlayer> waiting : awaitingRetitle.entrySet()) {
                    Player player = waiting.getValue().player();
                    try {
                        record(problemWith(player.getOpenInventory().getTitle(), waiting.getKey()), waiting.getKey());
                    } catch (RuntimeException | LinkageError crash) {
                        record("threw " + crash + " when its title was read", waiting.getKey());
                    } finally {
                        close(player);
                    }
                }
                awaitingRetitle.clear();
            }

            void report(int languages) {
                for (Map.Entry<String, List<String>> problem : languagesByProblem.entrySet()) {
                    List<String> shownIn = problem.getValue();
                    context.findings().report(NAME, id, problem.getKey() + " in " + (shownIn.size() == languages ? "every language" : String.join(", ", shownIn)));
                }
            }

            @Nullable
            private String problemWith(String title, String language) {
                String shown = strip(title);
                Optional<String> textProblem = TextProblems.problemWith(shown);
                if (textProblem.isPresent()) {
                    return "its title \"" + shown + "\" is wrong: " + textProblem.get();
                }
                if (!shown.equals(strip(VanillaContainerTitleListener.resolveTitle(id, language)))) {
                    return "shows \"" + shown + "\" instead of the title core resolves for the viewer";
                }
                return null;
            }

            private void record(@Nullable String problem, String language) {
                if (problem != null) {
                    languagesByProblem.computeIfAbsent(problem, ignored -> new ArrayList<>()).add(language);
                }
            }

            /** The title the open-window packet carries once core has translated it for this viewer. */
            private String titleSentBy(PacketTranslationService packets, Player viewer) {
                InventoryView view = viewer.getOpenInventory();
                FedPackets packetsOfThisServer = fed.get();
                Object packet = packetsOfThisServer.openWindow(FED_WINDOW_ID, view.getTitle(), view.getTopInventory().getSize());
                return packetsOfThisServer.titleOf(packets.translateOutbound(viewer, packet));
            }
        }
    }

    @Nullable
    private static Inventory inventoryOf(Block block) {
        BlockState state = block.getState();
        return state instanceof InventoryHolder ? ((InventoryHolder) state).getInventory() : null;
    }

    /** A title recorded for a window whose packet was never fed would otherwise retitle this viewer's next window. */
    private static void close(Player viewer) {
        viewer.closeInventory();
        PacketTranslationService packets = Slimefun.getPacketTranslationService();
        if (packets != null) {
            packets.cancelWindowTitle(viewer);
        }
    }

    private static String strip(@Nullable String title) {
        return title == null ? "" : ChatColor.stripColor(title).trim();
    }
}
