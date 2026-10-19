package io.github.thebusybiscuit.slimefun5.core.checks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class CheckRunner {

    private static final long MILLIS_PER_TICK = 50L;

    private final List<Check> checks;

    public CheckRunner(@Nonnull Collection<Check> checks) {
        this.checks = new ArrayList<>(checks);
    }

    public void runBlocking(@Nonnull Stage stage, @Nonnull CheckContext context, @Nonnull IntConsumer advanceTicks) {
        try {
            for (Check check : selectChecks(stage, context, null)) {
                CheckExecution execution = new CheckExecution(check, context);
                while (!execution.isFinished()) {
                    int wait = execution.runStep();
                    if (wait > 0) {
                        advanceTicks.accept(wait);
                    }
                }
            }
        } finally {
            context.closeSandbox();
        }
    }

    @Nonnull
    public RunningChecks runSliced(@Nonnull Stage stage, @Nonnull CheckContext context, @Nonnull Plugin plugin,
            long budgetNanos, @Nonnull Runnable onDone) {
        RunningChecks running = new RunningChecks(context::closeSandbox, onDone);
        List<Check> asyncChecks = selectChecks(stage, context, CheckThread.ANY);
        List<Check> mainChecks = selectChecks(stage, context, CheckThread.MAIN);
        MainPhase mainPhase = new MainPhase(mainChecks, context, plugin, budgetNanos, running);
        if (asyncChecks.isEmpty()) {
            running.track(Bukkit.getScheduler().runTask(plugin, mainPhase::tick));
        } else {
            running.track(Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    boolean completed = runAsyncPhase(asyncChecks, context, running);
                    if (completed && !running.isCancelled()) {
                        running.track(Bukkit.getScheduler().runTask(plugin, mainPhase::tick));
                    } else {
                        running.finish();
                    }
                } catch (Throwable failure) {
                    running.finish();
                    throw failure;
                }
            }));
        }
        return running;
    }

    private boolean runAsyncPhase(List<Check> asyncChecks, CheckContext context, RunningChecks running) {
        for (Check check : asyncChecks) {
            CheckExecution execution = new CheckExecution(check, context);
            while (!execution.isFinished() && !running.isCancelled()) {
                int wait = execution.runStep();
                if (wait > 0 && !sleepTicks(wait)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean sleepTicks(int ticks) {
        try {
            Thread.sleep(ticks * MILLIS_PER_TICK);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private List<Check> selectChecks(Stage stage, CheckContext context, CheckThread thread) {
        List<Check> selected = checks.stream()
                .filter(check -> stage.includes(check.stage()))
                .filter(check -> thread == null || threadOf(check) == thread)
                .sorted(Comparator.comparing(Check::stage).thenComparing(Check::name))
                .collect(Collectors.toList());
        List<Check> runnable = new ArrayList<>();
        for (Check check : selected) {
            if (context.live() && !check.liveSafe()) {
                context.findings().recordSkipped(check.name(), "not safe on a live server");
            } else {
                runnable.add(check);
            }
        }
        if (thread == null) {
            runnable.sort(Comparator.comparing(check -> threadOf(check) == CheckThread.MAIN));
        }
        return runnable;
    }

    static CheckThread threadOf(@Nonnull Check check) {
        return check.stage().compareTo(Stage.MEDIUM) >= 0 ? CheckThread.MAIN : check.thread();
    }

    private static final class MainPhase {

        private final Iterator<Check> remaining;
        private final CheckContext context;
        private final Plugin plugin;
        private final long budgetNanos;
        private final RunningChecks running;
        private CheckExecution current;

        MainPhase(List<Check> checks, CheckContext context, Plugin plugin, long budgetNanos, RunningChecks running) {
            this.remaining = checks.iterator();
            this.context = context;
            this.plugin = plugin;
            this.budgetNanos = budgetNanos;
            this.running = running;
        }

        void tick() {
            if (running.isCancelled()) {
                return;
            }
            try {
                runSlice();
            } catch (Throwable failure) {
                running.finish();
                throw failure;
            }
        }

        private void runSlice() {
            long deadline = System.nanoTime() + budgetNanos;
            do {
                if (current == null || current.isFinished()) {
                    if (!remaining.hasNext()) {
                        running.finish();
                        return;
                    }
                    current = new CheckExecution(remaining.next(), context);
                    continue;
                }
                int wait = current.runStep();
                if (wait > 0) {
                    running.track(Bukkit.getScheduler().runTaskLater(plugin, this::tick, wait));
                    return;
                }
            } while (System.nanoTime() < deadline);
            running.track(Bukkit.getScheduler().runTask(plugin, this::tick));
        }
    }
}
