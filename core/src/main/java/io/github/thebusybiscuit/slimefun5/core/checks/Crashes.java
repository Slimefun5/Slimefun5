package io.github.thebusybiscuit.slimefun5.core.checks;

import javax.annotation.Nonnull;

/**
 * One finding line for an exception a check caught: the exception and the frame that threw it, so a finding
 * without a stack trace still names the line to fix.
 */
public final class Crashes {

    private Crashes() {}

    @Nonnull
    public static String describe(@Nonnull Throwable crash) {
        StackTraceElement[] frames = crash.getStackTrace();
        return frames.length == 0 ? crash.toString() : crash + " at " + frames[0];
    }
}
