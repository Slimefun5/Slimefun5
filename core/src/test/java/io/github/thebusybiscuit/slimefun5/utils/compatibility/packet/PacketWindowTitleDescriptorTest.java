package io.github.thebusybiscuit.slimefun5.utils.compatibility.packet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * MockBukkit has no real NMS open-window packet class on its classpath, so {@link PacketWindowTitleDescriptor#resolve()}
 * must degrade to {@code null} here exactly as it would on a real server version whose packet shape this
 * fork cannot resolve - never throwing, never returning a half-usable descriptor. The actual per-version
 * class/field resolution (and the packet rewrite itself) can only be confirmed on a real server; see this
 * class's own report for what still needs an in-game check.
 */
class PacketWindowTitleDescriptorTest {

    @BeforeAll
    static void mock() {
        MockBukkit.mock();
    }

    @AfterAll
    static void unmock() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("resolve() degrades to null when no real NMS open-window packet class is on the classpath")
    void resolveReturnsNullWithoutRealNms() {
        Assertions.assertNull(PacketWindowTitleDescriptor.resolve());
    }
}
