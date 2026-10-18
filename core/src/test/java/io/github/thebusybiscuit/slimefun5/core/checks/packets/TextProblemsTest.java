package io.github.thebusybiscuit.slimefun5.core.checks.packets;

import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TextProblemsTest {

    @Test
    void blankTextIsReported() {
        Assertions.assertEquals(Optional.of("blank"), TextProblems.problemWith("  "));
    }

    @Test
    void rawIdIsReported() {
        Assertions.assertEquals(Optional.of("shows a raw id"), TextProblems.problemWith("ENERGY_REGULATOR"));
    }

    @Test
    void unfilledPlaceholderIsReported() {
        Assertions.assertEquals(Optional.of("shows an unfilled placeholder"), TextProblems.problemWith("Soul Jar (%mob%)"));
        Assertions.assertEquals(Optional.of("shows an unfilled placeholder"), TextProblems.problemWith("Page {0}"));
    }

    @Test
    void readableTextHasNoProblem() {
        Assertions.assertEquals(Optional.empty(), TextProblems.problemWith("Energy Regulator"));
        Assertions.assertEquals(Optional.empty(), TextProblems.problemWith("Chest"));
    }
}
