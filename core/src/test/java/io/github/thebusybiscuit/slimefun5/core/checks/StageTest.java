package io.github.thebusybiscuit.slimefun5.core.checks;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class StageTest {

    @Test
    void parseIsCaseInsensitiveAndRejectsUnknown() {
        Assertions.assertEquals(Stage.HIGH, Stage.parse("high").get());
        Assertions.assertEquals(Stage.MAX, Stage.parse("Max").get());
        Assertions.assertFalse(Stage.parse("nonsense").isPresent());
        Assertions.assertFalse(Stage.parse(null).isPresent());
    }

    @Test
    void higherStageIncludesLowerOnes() {
        Assertions.assertTrue(Stage.MEDIUM.includes(Stage.LOW));
        Assertions.assertTrue(Stage.MEDIUM.includes(Stage.MEDIUM));
        Assertions.assertFalse(Stage.MEDIUM.includes(Stage.HIGH));
    }

    @Test
    void offIncludesNothing() {
        for (Stage stage : Stage.values()) {
            Assertions.assertFalse(Stage.OFF.includes(stage));
            Assertions.assertFalse(stage.includes(Stage.OFF));
        }
    }
}
