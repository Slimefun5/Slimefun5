package io.github.thebusybiscuit.slimefun5.core.checks;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * The entry the version harness calls reflectively, so its signature uses JDK types only.
 *
 * @implNote Skipped checks are logged at INFO because a skip is not a failure, and the harness fails the run on
 *           any SEVERE line.
 */
public final class HarnessEntry {

    private static final String SKIPPED_MARKER = "] skipped: ";

    private HarnessEntry() {}

    /**
     * Runs every registered check up to the named stage on a non-live context.
     *
     * @return the number of problems found
     */
    public static int run(@Nonnull String stageName, @Nonnull IntConsumer advanceTicks) {
        return run(stageName, advanceTicks, ignored -> {});
    }

    /**
     * Runs every registered check up to the named stage on a non-live context.
     *
     * @param activity told what a check is doing as it does it, and null between checks, so the harness can name
     *                 it beside whatever is logged meanwhile; never told while a finding is logged, since a
     *                 finding names its own subject
     * @return the number of problems found
     */
    public static int run(@Nonnull String stageName, @Nonnull IntConsumer advanceTicks, @Nonnull Consumer<String> activity) {
        Stage stage = Stage.parse(stageName).orElseThrow(() -> new IllegalArgumentException("Unknown check stage " + stageName));
        Logger logger = Slimefun.logger();
        Activity current = new Activity(activity);
        Findings findings = new Findings(line -> current.whileUnnamed(() -> logger.log(isSkipped(line) ? Level.INFO : Level.SEVERE, line)));
        new CheckRunner(Slimefun.getChecks().all()).runBlocking(stage, new CheckContext(findings, stage, false, current::set), advanceTicks);
        return findings.count();
    }

    private static boolean isSkipped(String line) {
        return line.contains(SKIPPED_MARKER);
    }

    private static final class Activity {

        private final Consumer<String> target;
        private String described;

        Activity(Consumer<String> target) {
            this.target = target;
        }

        synchronized void set(String what) {
            described = what;
            target.accept(what);
        }

        synchronized void whileUnnamed(Runnable action) {
            target.accept(null);
            try {
                action.run();
            } finally {
                target.accept(described);
            }
        }
    }
}
