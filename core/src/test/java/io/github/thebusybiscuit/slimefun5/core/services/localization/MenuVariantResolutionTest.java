package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MenuVariantResolutionTest {

    private static InputStream yaml(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static final String TWO_VARIANTS =
        "CARGO_NODE_INPUT:\n"
        + "  '24':\n"
        + "    variants:\n"
        + "    - match: 'Round-Robin: off'\n"
        + "      name: 'Rundlauf: aus'\n"
        + "    - match: 'Round-Robin: on'\n"
        + "      name: 'Rundlauf: ein'\n";

    private static final String PLAIN_SLOT =
        "NUCLEAR_REACTOR:\n"
        + "  '1':\n"
        + "    name: 'Brennstoff'\n";

    @Test
    void picksTheVariantMatchingTheLiveName() {
        MenuTranslationService service = new MenuTranslationService();
        service.load("de", yaml(TWO_VARIANTS));

        Assertions.assertEquals("Rundlauf: aus",
            service.resolveSlot("de", "CARGO_NODE_INPUT", 24, "Round-Robin: off").getName());
        Assertions.assertEquals("Rundlauf: ein",
            service.resolveSlot("de", "CARGO_NODE_INPUT", 24, "Round-Robin: on").getName());
    }

    /** An English edit the YAML did not follow must not blank the label. */
    @Test
    void anUnmatchedVariantResolvesToNothingRatherThanAnEmptyName() {
        MenuTranslationService service = new MenuTranslationService();
        service.load("de", yaml(TWO_VARIANTS));

        Assertions.assertNull(service.resolveSlot("de", "CARGO_NODE_INPUT", 24, "Round-Robin: perhaps"));
    }

    @Test
    void aPlainSlotStillResolvesWhateverTheLiveNameIs() {
        MenuTranslationService service = new MenuTranslationService();
        service.load("de", yaml(PLAIN_SLOT));

        Assertions.assertEquals("Brennstoff",
            service.resolveSlot("de", "NUCLEAR_REACTOR", 1, "anything at all").getName());
    }

    @Test
    void anUnknownPresetOrSlotResolvesToNothing() {
        MenuTranslationService service = new MenuTranslationService();
        service.load("de", yaml(PLAIN_SLOT));

        Assertions.assertNull(service.resolveSlot("de", "NO_SUCH_PRESET", 1, "x"));
        Assertions.assertNull(service.resolveSlot("de", "NUCLEAR_REACTOR", 44, "x"));
        Assertions.assertNull(service.resolveSlot("fr", "NUCLEAR_REACTOR", 1, "x"));
    }

    /** Colour codes differ between the authored match and the live item, which has them translated. */
    @Test
    void matchingIgnoresWhetherColourCodesAreAmpersandOrSection() {
        MenuTranslationService service = new MenuTranslationService();
        service.load("de", yaml(
            "P:\n  '2':\n    variants:\n    - match: '&7Mode: &4off'\n      name: '&7Modus: &4aus'\n"));

        Assertions.assertEquals("&7Modus: &4aus",
            service.resolveSlot("de", "P", 2, "\u00a77Mode: \u00a74off").getName());
    }

    @Test
    void malformedVariantsAreSkippedWithoutLosingTheRestOfTheFile() {
        MenuTranslationService service = new MenuTranslationService();
        service.load("de", yaml(
            "P:\n  '2':\n    variants:\n    - name: 'no match key'\n"
            + "Q:\n  '3':\n    name: 'kept'\n"));

        Assertions.assertNull(service.resolveSlot("de", "P", 2, "anything"));
        Assertions.assertEquals("kept", service.resolveSlot("de", "Q", 3, "anything").getName());
    }
}
