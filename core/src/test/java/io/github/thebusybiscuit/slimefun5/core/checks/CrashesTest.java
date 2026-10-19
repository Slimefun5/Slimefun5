package io.github.thebusybiscuit.slimefun5.core.checks;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CrashesTest {

    @Test
    void namesTheFrameThatThrew() {
        NullPointerException crash = new NullPointerException("boom");
        crash.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("dev.example.Machine", "tick", "Machine.java", 42),
            new StackTraceElement("dev.example.Caller", "run", "Caller.java", 7)
        });

        Assertions.assertEquals("java.lang.NullPointerException: boom at dev.example.Machine.tick(Machine.java:42)", Crashes.describe(crash));
    }

    @Test
    void aServerFrameAlsoNamesThePluginLineThatCalledIt() {
        NullPointerException crash = new NullPointerException("boom");
        crash.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("org.bukkit.craftbukkit.inventory.CraftMetaSkull", "equals", "CraftMetaSkull.java", 9),
            new StackTraceElement("java.util.Objects", "equals", "Objects.java", 64),
            new StackTraceElement("dev.example.Crafter", "tick", "Crafter.java", 30)
        });

        Assertions.assertEquals("java.lang.NullPointerException: boom at org.bukkit.craftbukkit.inventory.CraftMetaSkull.equals(CraftMetaSkull.java:9)"
            + " via dev.example.Crafter.tick(Crafter.java:30)", Crashes.describe(crash));
    }

    @Test
    void withoutFramesItIsTheExceptionAlone() {
        IllegalStateException crash = new IllegalStateException("bare");
        crash.setStackTrace(new StackTraceElement[0]);

        Assertions.assertEquals("java.lang.IllegalStateException: bare", Crashes.describe(crash));
    }
}
