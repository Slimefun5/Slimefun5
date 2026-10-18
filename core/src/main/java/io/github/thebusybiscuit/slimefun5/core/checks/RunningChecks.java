package io.github.thebusybiscuit.slimefun5.core.checks;

import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.scheduler.BukkitTask;

/**
 * @implNote {@code onDone} runs before {@link #isDone()} turns true, and at most once. {@link #cancel()} ends the
 *           run without calling it. {@code onEnd} runs once however the run ends, and before {@code onDone}, so
 *           what it reports is in the findings {@code onDone} sees.
 */
public final class RunningChecks {

    private final Runnable onEnd;
    private final Runnable onDone;
    private final AtomicBoolean ended = new AtomicBoolean();
    private final AtomicBoolean finished = new AtomicBoolean();
    private BukkitTask pendingTask;
    private volatile boolean done;
    private volatile boolean cancelled;

    RunningChecks(Runnable onEnd, Runnable onDone) {
        this.onEnd = onEnd;
        this.onDone = onDone;
    }

    public boolean isDone() {
        return done;
    }

    public synchronized void cancel() {
        cancelled = true;
        finished.set(true);
        if (pendingTask != null) {
            pendingTask.cancel();
            pendingTask = null;
        }
        try {
            end();
        } finally {
            done = true;
        }
    }

    boolean isCancelled() {
        return cancelled;
    }

    void finish() {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        try {
            end();
        } finally {
            try {
                onDone.run();
            } finally {
                done = true;
            }
        }
    }

    private void end() {
        if (ended.compareAndSet(false, true)) {
            onEnd.run();
        }
    }

    synchronized void track(BukkitTask task) {
        if (cancelled) {
            task.cancel();
            return;
        }
        pendingTask = task;
    }
}
