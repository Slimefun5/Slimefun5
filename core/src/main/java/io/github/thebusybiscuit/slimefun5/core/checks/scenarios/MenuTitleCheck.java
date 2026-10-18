package io.github.thebusybiscuit.slimefun5.core.checks.scenarios;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;

import io.github.thebusybiscuit.slimefun5.core.checks.Check;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckContext;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckStep;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckThread;
import io.github.thebusybiscuit.slimefun5.core.checks.Stage;
import io.github.thebusybiscuit.slimefun5.core.checks.packets.FedPackets;
import io.github.thebusybiscuit.slimefun5.core.checks.packets.TextProblems;
import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.Sandbox;
import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.SandboxPlayer;
import io.github.thebusybiscuit.slimefun5.core.services.localization.Language;
import io.github.thebusybiscuit.slimefun5.core.services.localization.PacketTranslationService;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.PdcCompat;

import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;

/**
 * Opens every machine menu for one viewer per language and reads the title their window shows: never blank,
 * never a raw id, never an unfilled placeholder, and always the title core resolves for that viewer.
 *
 * @implNote Where core hands a viewer's title to the packet layer, the title is read from this server's real
 *           open-window packet after core's translation of it; where core sets it on the open view instead, it is
 *           read from the view. The totals are logged only off a live server, as the harness's record of how
 *           much ran; a live run reports through its findings alone.
 */
public final class MenuTitleCheck implements Check {

    private static final String NAME = "menu-title";

    /** Any window id: a fed packet is never sent, so it needs no window of that id. */
    private static final int FED_WINDOW_ID = 1;

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
        Sandbox sandbox = context.sandbox();
        MachinePlacement placement = MachinePlacement.in(sandbox);
        StepQueue steps = new StepQueue();
        placement.planInto(steps, context);
        Map<String, SandboxPlayer> viewers = new LinkedHashMap<>();
        AtomicInteger problems = new AtomicInteger();
        Optional<FedPackets> fed = FedPackets.forRunningVersion();
        steps.then(() -> {
            joinViewerPerLanguage(sandbox, viewers);
            return 0;
        });
        steps.thenPlanned(() -> {
            List<CheckStep> planned = new ArrayList<>();
            for (MachinePlacement.PlacedMenu placed : placement.menus()) {
                MenuTitles titles = new MenuTitles(placed, fed);
                for (Map.Entry<String, SandboxPlayer> viewer : viewers.entrySet()) {
                    planned.add(() -> {
                        context.describe(NAME + " " + placed.preset.getID() + " for " + viewer.getKey());
                        titles.check(viewer.getKey(), viewer.getValue());
                        return 0;
                    });
                }
                planned.add(() -> {
                    problems.addAndGet(titles.report(context, viewers.size()));
                    return 0;
                });
            }
            return planned;
        });
        steps.then(() -> {
            viewers.values().forEach(sandbox::quit);
            if (!context.live()) {
                Slimefun.logger().info("Menu titles checked: " + placement.menus().size() + " menus x " + viewers.size() + " languages, " + problems + " problem(s)");
            }
            return 0;
        });
        return steps;
    }

    private static void joinViewerPerLanguage(Sandbox sandbox, Map<String, SandboxPlayer> viewers) {
        for (Language language : Slimefun.getLocalization().getLanguages()) {
            SandboxPlayer viewer = sandbox.join("title_" + language.getId(), language.getId());
            PdcCompat.set(viewer.player(), Slimefun.getLocalization().getKey(), "STRING", language.getId());
            viewers.put(language.getId(), viewer);
        }
    }

    /** One menu's title problems, each with the languages it showed in. */
    private static final class MenuTitles {

        private final MachinePlacement.PlacedMenu placed;
        private final Optional<FedPackets> fed;
        private final Map<String, List<String>> languagesByProblem = new LinkedHashMap<>();

        MenuTitles(MachinePlacement.PlacedMenu placed, Optional<FedPackets> fed) {
            this.placed = placed;
            this.fed = fed;
        }

        void check(String language, SandboxPlayer viewer) {
            String problem = problemWith(viewer);
            if (problem != null) {
                languagesByProblem.computeIfAbsent(problem, ignored -> new ArrayList<>()).add(language);
            }
        }

        int report(CheckContext context, int languages) {
            for (Map.Entry<String, List<String>> problem : languagesByProblem.entrySet()) {
                context.findings().report(NAME, placed.preset.getID(), problem.getKey() + " in " + describe(problem.getValue(), languages));
            }
            return languagesByProblem.size();
        }

        private String problemWith(SandboxPlayer viewer) {
            Player player = viewer.player();
            try {
                String intended = strip(intendedTitle(player));
                placed.menu.open(player);
                if (!viewer.hasWindowOpen()) {
                    return "does not open";
                }
                PacketTranslationService packets = Slimefun.getPacketTranslationService();
                boolean byPacket = packets != null && packets.hasPendingWindowTitle(player);
                if (byPacket && !fed.isPresent()) {
                    return "is titled by the open-window packet, which this server's packets cannot be built to check";
                }
                String shown = strip(byPacket ? titleSentBy(packets, player) : player.getOpenInventory().getTitle());
                Optional<String> textProblem = TextProblems.problemWith(shown);
                if (textProblem.isPresent()) {
                    return "its title \"" + shown + "\" is wrong: " + textProblem.get();
                }
                if (!shown.equals(intended)) {
                    return "shows \"" + shown + "\" instead of the title core resolves for the viewer";
                }
                return null;
            } catch (RuntimeException | LinkageError crash) {
                return "threw " + crash + " when opened";
            } finally {
                player.closeInventory();
                forgetUnsentTitle(player);
            }
        }

        /** A title recorded for a window whose packet was never fed would otherwise retitle this viewer's next menu. */
        private static void forgetUnsentTitle(Player viewer) {
            PacketTranslationService packets = Slimefun.getPacketTranslationService();
            if (packets != null) {
                packets.cancelWindowTitle(viewer);
            }
        }

        /** The title the open-window packet carries once core has translated it for this viewer. */
        private String titleSentBy(PacketTranslationService packets, Player viewer) {
            InventoryView view = viewer.getOpenInventory();
            FedPackets packetsOfThisServer = fed.get();
            Object packet = packetsOfThisServer.openWindow(FED_WINDOW_ID, view.getTitle(), view.getTopInventory().getSize());
            return packetsOfThisServer.titleOf(packets.translateOutbound(viewer, packet));
        }

        /** What core resolves this viewer's title to; the shared inventory's own title when it needs no change. */
        private String intendedTitle(Player viewer) {
            String perViewer = placed.menu instanceof BlockMenu ? Slimefun.getMenuTranslationService().getTitleFor((BlockMenu) placed.menu, viewer) : null;
            return perViewer != null ? perViewer : ChatColor.translateAlternateColorCodes('&', placed.preset.getTitle());
        }

        private static String strip(String title) {
            return title == null ? "" : ChatColor.stripColor(title).trim();
        }

        private static String describe(List<String> languages, int total) {
            return languages.size() == total ? "every language" : String.join(", ", languages);
        }
    }
}
