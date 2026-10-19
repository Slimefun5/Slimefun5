package io.github.thebusybiscuit.slimefun5.core.checks;

import java.util.Iterator;

/**
 * One in-server check, run by {@code /sf check}, on startup and by the version harness.
 * <p>
 * A check runs on the main thread unless {@link #thread()} returns {@link CheckThread#ANY}, which only a check
 * that calls no Bukkit or Slimefun state outside thread-safe reads may do. A check at {@link Stage#MEDIUM} or
 * above always runs on the main thread, because it works in the sandbox. A check whose {@link #liveSafe()} is
 * false is skipped on a live server and runs only in the harness.
 * <p>
 * {@link #steps(CheckContext)} is called once, on the check's thread. Each {@link CheckStep} should stay near
 * 5 ms, because main-thread steps share the tick with players, and returns how many ticks to wait before the
 * next one. A step that throws ends the check with one crash finding; the other checks still run.
 */
public interface Check {

    String name();

    Stage stage();

    default CheckThread thread() {
        return CheckThread.MAIN;
    }

    default boolean liveSafe() {
        return true;
    }

    Iterator<CheckStep> steps(CheckContext context) throws Exception;
}
