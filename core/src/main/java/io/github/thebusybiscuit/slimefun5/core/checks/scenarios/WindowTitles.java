package io.github.thebusybiscuit.slimefun5.core.checks.scenarios;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;

import io.github.thebusybiscuit.slimefun5.core.checks.CheckContext;
import io.github.thebusybiscuit.slimefun5.core.checks.packets.FedPackets;
import io.github.thebusybiscuit.slimefun5.core.checks.packets.TextProblems;
import io.github.thebusybiscuit.slimefun5.core.services.localization.PacketTranslationService;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * Reads the title a viewer's open window shows and judges it against the title core resolves for that viewer.
 *
 * @implNote Where core hands a viewer's title to the packet layer, the title is read from this server's real
 *           open-window packet after core's translation of it; where core sets it on the open view instead, it is
 *           read from the view.
 */
final class WindowTitles {

    /** Any window id: a fed packet is never sent, so it needs no window of that id. */
    private static final int FED_WINDOW_ID = 1;

    private final Optional<FedPackets> fed = FedPackets.forRunningVersion();

    /** Whether core handed the title of this viewer's open window to the packet layer. */
    static boolean isTitledByPacket(@Nonnull Player viewer) {
        PacketTranslationService packets = Slimefun.getPacketTranslationService();
        return packets != null && packets.hasPendingWindowTitle(viewer);
    }

    /**
     * @param intendedTitle the title core resolves for the viewer, colour codes allowed
     * @return what is wrong with the title the viewer's open window shows, or {@code null} when nothing is
     */
    @Nullable
    String problemWith(@Nonnull Player viewer, @Nullable String intendedTitle) {
        boolean byPacket = isTitledByPacket(viewer);
        if (byPacket && !fed.isPresent()) {
            return "is titled by the open-window packet, which this server's packets cannot be built to check";
        }
        String shown = strip(byPacket ? titleSentBy(viewer) : viewer.getOpenInventory().getTitle());
        Optional<String> textProblem = TextProblems.problemWith(shown);
        if (textProblem.isPresent()) {
            return "its title \"" + shown + "\" is wrong: " + textProblem.get();
        }
        if (!shown.equals(strip(intendedTitle))) {
            return "shows \"" + shown + "\" instead of the title core resolves for the viewer";
        }
        return null;
    }

    /** Closes the viewer's window and drops a title recorded for it, which would otherwise retitle the viewer's next window. */
    static void close(@Nonnull Player viewer) {
        viewer.closeInventory();
        PacketTranslationService packets = Slimefun.getPacketTranslationService();
        if (packets != null) {
            packets.cancelWindowTitle(viewer);
        }
    }

    private String titleSentBy(Player viewer) {
        InventoryView view = viewer.getOpenInventory();
        FedPackets packetsOfThisServer = fed.get();
        Object packet = packetsOfThisServer.openWindow(FED_WINDOW_ID, view.getTitle(), view.getTopInventory().getSize());
        return packetsOfThisServer.titleOf(Slimefun.getPacketTranslationService().translateOutbound(viewer, packet));
    }

    private static String strip(@Nullable String title) {
        return title == null ? "" : ChatColor.stripColor(title).trim();
    }

    /** One subject's title problems, each with the languages it showed in. */
    static final class LanguagesByProblem {

        private final Map<String, List<String>> languagesByProblem = new LinkedHashMap<>();

        void record(@Nullable String problem, @Nonnull String language) {
            if (problem != null) {
                languagesByProblem.computeIfAbsent(problem, ignored -> new ArrayList<>()).add(language);
            }
        }

        /** @return the number of distinct problems reported */
        int report(@Nonnull CheckContext context, @Nonnull String check, @Nonnull String subject, int languages) {
            for (Map.Entry<String, List<String>> problem : languagesByProblem.entrySet()) {
                List<String> shownIn = problem.getValue();
                context.findings().report(check, subject, problem.getKey() + " in " + (shownIn.size() == languages ? "every language" : String.join(", ", shownIn)));
            }
            return languagesByProblem.size();
        }
    }
}
