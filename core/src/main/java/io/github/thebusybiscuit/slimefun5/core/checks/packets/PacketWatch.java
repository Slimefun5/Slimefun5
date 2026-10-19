package io.github.thebusybiscuit.slimefun5.core.checks.packets;

import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;

import org.bukkit.ChatColor;

import io.github.thebusybiscuit.slimefun5.core.checks.Findings;

/**
 * Collects raw ids and unfilled placeholders in the text real players are shown.
 *
 * @implNote {@link #observe} runs on the Netty thread for every packet, so it calls nothing but
 *           {@link TextProblems}, blocks on nothing and holds at most {@code cap} findings. Blank text is not
 *           reported: filler panes are named with a space on purpose. A finding is remembered by
 *           {@code kind + subjectKey} until the server stops, even after {@link #drain}, so a problem is
 *           reported once however often it is sent.
 */
public final class PacketWatch {

    static final String CHECK_NAME = "packet-watch";

    private final int cap;
    private final Queue<Finding> queued = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queuedCount = new AtomicInteger();
    private final Set<String> seen = ConcurrentHashMap.newKeySet();

    public PacketWatch(int cap) {
        this.cap = cap;
    }

    public void observe(@Nonnull String kind, @Nonnull String subjectKey, @Nonnull String shownText) {
        String plainText = ChatColor.stripColor(shownText);
        if (plainText == null || plainText.trim().isEmpty()) {
            return;
        }
        Optional<String> problem = TextProblems.problemWith(plainText);
        if (problem.isPresent()) {
            enqueueOnce(kind + " " + subjectKey, problem.get());
        }
    }

    /**
     * A lore line on a Slimefun stack that the packet layer replaces before any player sees it.
     */
    public void reportHiddenLore(@Nonnull String itemId, @Nonnull String hiddenLine) {
        enqueueOnce("hidden-lore " + itemId, "lore line \"" + hiddenLine + "\" is replaced before any player sees it; write it as LiveLore");
    }

    @Nonnull
    public List<String> drain() {
        Findings drained = new Findings(line -> {});
        Finding finding = queued.poll();
        while (finding != null) {
            queuedCount.decrementAndGet();
            drained.report(CHECK_NAME, finding.subject, finding.problem);
            finding = queued.poll();
        }
        return drained.lines();
    }

    private void enqueueOnce(String subject, String problem) {
        if (queuedCount.get() >= cap || !seen.add(subject)) {
            return;
        }
        if (queuedCount.incrementAndGet() > cap) {
            queuedCount.decrementAndGet();
            seen.remove(subject);
            return;
        }
        queued.add(new Finding(subject, problem));
    }

    private static final class Finding {

        private final String subject;
        private final String problem;

        private Finding(String subject, String problem) {
            this.subject = subject;
            this.problem = problem;
        }
    }
}
