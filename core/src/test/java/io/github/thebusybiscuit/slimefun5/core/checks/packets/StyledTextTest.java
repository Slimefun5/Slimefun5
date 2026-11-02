package io.github.thebusybiscuit.slimefun5.core.checks.packets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class StyledTextTest {

    @Test
    void redundantCodesDrawTheSame() {
        Assertions.assertEquals(StyledText.canonical("§bReactor"), StyledText.canonical("§b§bRe§bactor"));
        Assertions.assertEquals(StyledText.canonical("§6§lCore"), StyledText.canonical("§a§6§lCore"));
    }

    @Test
    void colourAndBoldDifferencesAreKept() {
        Assertions.assertNotEquals(StyledText.canonical("§bReactor"), StyledText.canonical("§eReactor"));
        Assertions.assertNotEquals(StyledText.canonical("§6Core"), StyledText.canonical("§6§lCore"));
        Assertions.assertNotEquals(StyledText.canonical("§bReactor"), StyledText.canonical("Reactor"));
    }

    @Test
    void colourClearsFormatsAndHexIsOneColour() {
        Assertions.assertEquals(StyledText.canonical("§l§bA"), StyledText.canonical("§bA"));
        Assertions.assertEquals("[#a1b2c3[]]Hex", StyledText.canonical("§x§A§1§b§2§c§3Hex"));
    }
}
