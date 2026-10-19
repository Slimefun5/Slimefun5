package io.github.thebusybiscuit.slimefun5.core.checks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * One finding line for an exception a check caught: the exception and the frame that threw it, so a finding
 * without a stack trace still names the line to fix. When that frame is inside the JDK or the server, the first
 * frame outside them is named too, because that is the plugin line that made the call.
 */
public final class Crashes {

    private static final String[] PLATFORM_PACKAGES = {
        "java.", "javax.", "jdk.", "sun.", "org.bukkit.", "net.minecraft.", "com.mojang.", "io.papermc.", "org.spigotmc."
    };

    private Crashes() {}

    @Nonnull
    public static String describe(@Nonnull Throwable crash) {
        StackTraceElement[] frames = crash.getStackTrace();
        if (frames.length == 0) {
            return crash.toString();
        }
        String line = crash + " at " + frames[0];
        StackTraceElement caller = isPlatform(frames[0]) ? firstOutsidePlatform(frames) : null;
        return caller == null ? line : line + " via " + caller;
    }

    @Nullable
    private static StackTraceElement firstOutsidePlatform(StackTraceElement[] frames) {
        for (StackTraceElement frame : frames) {
            if (!isPlatform(frame)) {
                return frame;
            }
        }
        return null;
    }

    private static boolean isPlatform(StackTraceElement frame) {
        for (String prefix : PLATFORM_PACKAGES) {
            if (frame.getClassName().startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
