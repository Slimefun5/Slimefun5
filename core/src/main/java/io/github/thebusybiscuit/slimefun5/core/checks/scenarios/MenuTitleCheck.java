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

import io.github.thebusybiscuit.slimefun5.core.checks.Check;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckContext;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckStep;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckThread;
import io.github.thebusybiscuit.slimefun5.core.checks.Stage;
import io.github.thebusybiscuit.slimefun5.core.checks.packets.TextProblems;
import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.Sandbox;
import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.SandboxPlayer;
import io.github.thebusybiscuit.slimefun5.core.services.localization.Language;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.PdcCompat;

import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;

/**
 * Opens every machine menu for one viewer per language and reads the title their window shows: never blank,
 * never a raw id, never an unfilled placeholder, and always the title core resolves for that viewer.
 *
 * @implNote Only {@code InventoryView#setTitle} reaches a sandbox window, and it exists from 1.19.4; a title core
 *           writes into the open-window packet is not seen here, so below 1.19.4 a correct server still fails
 *           this check until the check reads the packet. The totals are logged only off a live server, as
 *           the harness's record of how much ran; a live run reports through its findings alone.
 */
public final class MenuTitleCheck implements Check {

    private static final String NAME = "menu-title";

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
        steps.then(() -> {
            joinViewerPerLanguage(sandbox, viewers);
            return 0;
        });
        steps.thenPlanned(() -> {
            List<CheckStep> planned = new ArrayList<>();
            for (MachinePlacement.PlacedMenu placed : placement.menus()) {
                MenuTitles titles = new MenuTitles(placed);
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
        private final Map<String, List<String>> languagesByProblem = new LinkedHashMap<>();

        MenuTitles(MachinePlacement.PlacedMenu placed) {
            this.placed = placed;
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
                String shown = strip(player.getOpenInventory().getTitle());
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
            }
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
