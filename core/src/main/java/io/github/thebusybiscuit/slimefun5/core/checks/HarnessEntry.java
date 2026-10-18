package io.github.thebusybiscuit.slimefun5.core.checks;

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
        Stage stage = Stage.parse(stageName).orElseThrow(() -> new IllegalArgumentException("Unknown check stage " + stageName));
        Logger logger = Slimefun.logger();
        Findings findings = new Findings(line -> logger.log(isSkipped(line) ? Level.INFO : Level.SEVERE, line));
        new CheckRunner(Slimefun.getChecks().all()).runBlocking(stage, new CheckContext(findings, stage, false), advanceTicks);
        return findings.count();
    }

    private static boolean isSkipped(String line) {
        return line.contains(SKIPPED_MARKER);
    }
}
