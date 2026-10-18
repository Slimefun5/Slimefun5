package io.github.thebusybiscuit.slimefun5.core.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

/**
 * @implNote Checks on {@link CheckThread#ANY} report from an async thread, so every access is synchronized.
 */
public final class Findings {

    private final Consumer<String> sink;
    private final List<String> lines = new ArrayList<>();
    private int count;

    public Findings(@Nonnull Consumer<String> sink) {
        this.sink = sink;
    }

    public void report(@Nonnull String check, @Nonnull String subject, @Nonnull String problem) {
        String line = "[check:" + check + "] " + subject + ": " + problem;
        synchronized (this) {
            lines.add(line);
            count++;
        }
        sink.accept(line);
    }

    public void recordSkipped(@Nonnull String check, @Nonnull String reason) {
        String line = "[check:" + check + "] skipped: " + reason;
        synchronized (this) {
            lines.add(line);
        }
        sink.accept(line);
    }

    public synchronized int count() {
        return count;
    }

    @Nonnull
    public synchronized List<String> lines() {
        return new ArrayList<>(lines);
    }
}
