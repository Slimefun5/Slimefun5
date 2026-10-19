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
    void withoutFramesItIsTheExceptionAlone() {
        IllegalStateException crash = new IllegalStateException("bare");
        crash.setStackTrace(new StackTraceElement[0]);

        Assertions.assertEquals("java.lang.IllegalStateException: bare", Crashes.describe(crash));
    }
}
