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

    /**
     * The open-time path must choose the same variant the packet path would, or reopening a toggled
     * menu would relabel it with the other state.
     */
    @Test
    void theOpenTimePathAgreesWithTheVariantMatch() {
        MenuTranslationService service = new MenuTranslationService();
        service.load("de", yaml(TWO_VARIANTS));
        service.load("en", yaml(
            "CARGO_NODE_INPUT:\n"
            + "  '24':\n"
            + "    variants:\n"
            + "    - match: 'Round-Robin: off'\n"
            + "      name: 'Round-Robin: off'\n"));

        Assertions.assertEquals("Rundlauf: ein",
            service.resolveSlot("de", "CARGO_NODE_INPUT", 24, "Round-Robin: on").getName());
        Assertions.assertNull(service.resolveSlot("en", "CARGO_NODE_INPUT", 24, "Round-Robin: on"));
    }

    /**
     * applyToMenu visits exactly the slots this returns, so a slot that exists ONLY as a variant list
     * must appear here or the open-time rewrite never reaches it at all.
     */
    @Test
    void theOpenTimeTargetsIncludeSlotsThatExistOnlyAsVariants() {
        MenuTranslationService service = new MenuTranslationService();
        service.load("de", yaml(TWO_VARIANTS));
        service.load("de", yaml(PLAIN_SLOT));

        Assertions.assertTrue(service.targetSlotsFor("de", "CARGO_NODE_INPUT").contains(24));
        Assertions.assertTrue(service.targetSlotsFor("de", "NUCLEAR_REACTOR").contains(1));
        Assertions.assertTrue(service.targetSlotsFor("de", "NO_SUCH_PRESET").isEmpty());
    }

    /** An English-only variant must still be visited for a player whose language has none. */
    @Test
    void theOpenTimeTargetsUnionTheViewerLanguageAndEnglish() {
        MenuTranslationService service = new MenuTranslationService();
        service.load("en", yaml(TWO_VARIANTS));

        Assertions.assertTrue(service.targetSlotsFor("fr", "CARGO_NODE_INPUT").contains(24));
    }

    /**
     * The shipped en/menus.yml must describe both states of both cargo toggles, and its match lines
     * must be the ones CargoInputNode actually writes.
     */
    @Test
    void theShippedEnglishFileCoversBothCargoToggleStates() throws Exception {
        MenuTranslationService service = new MenuTranslationService();

        try (InputStream stream = MenuTranslationService.class
                .getResourceAsStream("/languages/en/menus.yml")) {
            Assertions.assertNotNull(stream, "en/menus.yml is not on the test classpath");
            service.load("en", stream);
        }

        Assertions.assertNotNull(service.resolveSlot("en", "CARGO_NODE_INPUT", 24,
            "&7Round-Robin Mode: &4\u2718"));
        Assertions.assertNotNull(service.resolveSlot("en", "CARGO_NODE_INPUT", 24,
            "&7Round-Robin Mode: &2\u2714"));
        Assertions.assertNotNull(service.resolveSlot("en", "CARGO_NODE_INPUT", 16,
            "&7\"Smart-Filling\" Mode: &4\u2718"));
        Assertions.assertNotNull(service.resolveSlot("en", "CARGO_NODE_INPUT", 16,
            "&7\"Smart-Filling\" Mode: &2\u2714"));

        Assertions.assertNull(service.resolveSlot("en", "CARGO_NODE_INPUT", 24, "&7Round-Robin Mode: &emaybe"));
    }
}
