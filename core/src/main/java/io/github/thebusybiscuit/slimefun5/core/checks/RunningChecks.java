package io.github.thebusybiscuit.slimefun5.core.checks;

import org.bukkit.scheduler.BukkitTask;

public final class RunningChecks {

    private BukkitTask pendingTask;
    private volatile boolean done;
    private volatile boolean cancelled;

    public boolean isDone() {
        return done;
    }

    public synchronized void cancel() {
        cancelled = true;
        if (pendingTask != null) {
            pendingTask.cancel();
            pendingTask = null;
        }
        done = true;
    }

    boolean isCancelled() {
        return cancelled;
    }

    void markDone() {
        done = true;
    }

    synchronized void track(BukkitTask task) {
        if (cancelled) {
            task.cancel();
            return;
        }
        pendingTask = task;
    }
}
