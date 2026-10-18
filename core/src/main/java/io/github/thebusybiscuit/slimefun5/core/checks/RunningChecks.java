package io.github.thebusybiscuit.slimefun5.core.checks;

import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.scheduler.BukkitTask;

/**
 * @implNote {@code onDone} runs before {@link #isDone()} turns true, and at most once. {@link #cancel()} ends the
 *           run without calling it.
 */
public final class RunningChecks {

    private final Runnable onDone;
    private final AtomicBoolean finished = new AtomicBoolean();
    private BukkitTask pendingTask;
    private volatile boolean done;
    private volatile boolean cancelled;

    RunningChecks(Runnable onDone) {
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
        done = true;
    }

    boolean isCancelled() {
        return cancelled;
    }

    void finish() {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        try {
            onDone.run();
        } finally {
            done = true;
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
