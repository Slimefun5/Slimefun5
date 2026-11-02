package io.github.thebusybiscuit.slimefun5.core.checks.scenarios;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.core.checks.Check;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckContext;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckStep;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckThread;
import io.github.thebusybiscuit.slimefun5.core.checks.Stage;
import io.github.thebusybiscuit.slimefun5.core.checks.packets.FedItemPacket;
import io.github.thebusybiscuit.slimefun5.core.checks.packets.FedPackets;
import io.github.thebusybiscuit.slimefun5.core.checks.packets.TextProblems;
import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.Sandbox;
import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.SandboxPlayer;
import io.github.thebusybiscuit.slimefun5.core.services.localization.Language;
import io.github.thebusybiscuit.slimefun5.core.services.localization.PacketTranslationService;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.PdcCompat;

/**
 * Feeds every enabled item through each of this server's real item packets for one viewer per language, and
 * compares what the client would be shown with what core's rewrite produces for that viewer without a packet.
 * What it tests is the packet layer alone: finding the packet, reaching its stacks and converting them back.
 */
public final class ItemPacketCheck implements Check {

    private static final String NAME = "item-packet";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Stage stage() {
        return Stage.MEDIUM;
    }

    @Override
    public CheckThread thread() {
        return CheckThread.MAIN;
    }

    @Override
    public Iterator<CheckStep> steps(CheckContext context) {
        StepQueue steps = new StepQueue();
        Optional<FedPackets> fed = FedPackets.forRunningVersion();
        PacketTranslationService translation = Slimefun.getPacketTranslationService();
        if (!fed.isPresent() || translation == null) {
            steps.then(() -> {
                context.findings().report(NAME, "packets", "this server's item packets cannot be built, so no item name is checked through them");
                return 0;
            });
            return steps;
        }

        Sandbox sandbox = context.sandbox();
        Map<String, SandboxPlayer> viewers = new LinkedHashMap<>();
        Set<String> unbuildable = new HashSet<>();
        AtomicInteger problems = new AtomicInteger();
        List<SlimefunItem> items = new ArrayList<>(Slimefun.getRegistry().getEnabledSlimefunItems());
        steps.then(() -> {
            joinViewerPerLanguage(sandbox, viewers);
            return 0;
        });
        steps.thenPlanned(() -> {
            List<CheckStep> planned = new ArrayList<>();
            for (SlimefunItem item : items) {
                planned.add(() -> {
                    if (!FedItemPacket.hasItemForm(item.getItem())) {
                        context.findings().report(NAME, item.getId(), "is made of " + item.getItem().getType()
                            + ", which has no item form on this server, so every client is sent an empty slot instead");
                        problems.incrementAndGet();
                    }
                    return 0;
                });
                for (FedItemPacket packet : fed.get().itemPackets()) {
                    planned.add(() -> {
                        if (!unbuildable.contains(packet.name()) && FedItemPacket.hasItemForm(item.getItem())) {
                            context.describe(NAME + " " + item.getId() + " through " + packet.name());
                            problems.addAndGet(checkItem(context, translation, packet, item, viewers, unbuildable));
                        }
                        return 0;
                    });
                }
            }
            return planned;
        });
        steps.then(() -> {
            viewers.values().forEach(sandbox::quit);
            if (!context.live()) {
                Slimefun.logger().info("Item packets checked: " + items.size() + " items x " + viewers.size() + " languages x "
                    + fed.get().itemPackets().size() + " packets, " + problems + " problem(s)");
            }
            return 0;
        });
        return steps;
    }

    private static void joinViewerPerLanguage(Sandbox sandbox, Map<String, SandboxPlayer> viewers) {
        for (Language language : Slimefun.getLocalization().getLanguages()) {
            SandboxPlayer viewer = sandbox.join("packet_" + language.getId(), language.getId());
            PdcCompat.set(viewer.player(), Slimefun.getLocalization().getKey(), "STRING", language.getId());
            viewers.put(language.getId(), viewer);
        }
    }

    /** @return the number of distinct problems reported for this item and packet */
    private static int checkItem(CheckContext context, PacketTranslationService translation, FedItemPacket packet, SlimefunItem item,
            Map<String, SandboxPlayer> viewers, Set<String> unbuildable) {
        ShownStacks problems = new ShownStacks(packet.name());
        for (Map.Entry<String, SandboxPlayer> viewer : viewers.entrySet()) {
            Player player = viewer.getValue().player();
            ItemStack intended = translation.shownWithoutPacket(player, item.getItem());
            List<ItemStack> fed = packet.carriesSeveral()
                ? Arrays.asList(item.getItem().clone(), new ItemStack(Material.STONE), new ItemStack(Material.AIR))
                : Arrays.asList(item.getItem().clone());
            List<ItemStack> shown;
            try {
                shown = packet.itemsOf(translation.translateOutbound(player, packet.carrying(fed)));
            } catch (IllegalStateException unbuilt) {
                unbuildable.add(packet.name());
                context.findings().report(NAME, packet.name(), unbuilt.getMessage() + " (from " + item.getId() + "), so this packet is not checked");
                return 1;
            }
            problems.compare(viewer.getKey(), intended, fed, shown);
        }
        return problems.report(context, item.getId(), viewers.size());
    }

    /** One item's problems through one packet, each with the languages it showed in and one example. */
    static final class ShownStacks {

        private final String packet;
        private final Map<String, List<String>> languagesByProblem = new LinkedHashMap<>();
        private final Map<String, String> exampleByProblem = new LinkedHashMap<>();

        ShownStacks(@Nonnull String packet) {
            this.packet = packet;
        }

        void compare(@Nonnull String language, @Nonnull ItemStack intended, @Nonnull List<ItemStack> fed, @Nonnull List<ItemStack> shown) {
            if (shown.size() != fed.size()) {
                record("comes back with " + shown.size() + " stacks instead of " + fed.size(), language, null);
                return;
            }
            ShownDisplay want = ShownDisplay.of(intended);
            ShownDisplay got = ShownDisplay.of(shown.get(0));
            String plainName = ChatColor.stripColor(got.rawName).trim();
            Optional<String> textProblem = want.rawName.isEmpty() ? Optional.empty() : TextProblems.problemWith(plainName);
            if (textProblem.isPresent()) {
                record("its name " + textProblem.get(), language, plainName);
            } else if (!got.name.equals(want.name)) {
                record("shows a different name than core intends", language, "shows \"" + got.rawName + "\", intended \"" + want.rawName + "\"");
            }
            if (!got.lore.equals(want.lore)) {
                record("shows different lore than core intends", language, "shows " + got.rawLore + ", intended " + want.rawLore);
            }
            for (int slot = 1; slot < fed.size(); slot++) {
                if (!isUntouched(fed.get(slot), shown.get(slot))) {
                    record("changes the vanilla stack beside it in slot " + slot, language, String.valueOf(shown.get(slot)));
                }
            }
        }

        private static boolean isUntouched(ItemStack fed, ItemStack shown) {
            if (fed.getType() == Material.AIR) {
                return shown.getType() == Material.AIR;
            }
            return shown.getType() == fed.getType() && (!shown.hasItemMeta() || (!shown.getItemMeta().hasDisplayName() && !shown.getItemMeta().hasLore()));
        }

        private void record(String problem, String language, String example) {
            languagesByProblem.computeIfAbsent(problem, ignored -> new ArrayList<>()).add(language);
            if (example != null) {
                exampleByProblem.putIfAbsent(problem, language + ": " + example);
            }
        }

        /** @return the number of distinct problems reported */
        int report(@Nonnull CheckContext context, @Nonnull String itemId, int languages) {
            for (Map.Entry<String, List<String>> problem : languagesByProblem.entrySet()) {
                List<String> shownIn = problem.getValue();
                String where = shownIn.size() == languages ? "every language" : String.join(", ", shownIn);
                String example = exampleByProblem.get(problem.getKey());
                context.findings().report(NAME, itemId, "through " + packet + " " + problem.getKey() + " in " + where + (example == null ? "" : " (" + example + ")"));
            }
            return languagesByProblem.size();
        }
    }
}
