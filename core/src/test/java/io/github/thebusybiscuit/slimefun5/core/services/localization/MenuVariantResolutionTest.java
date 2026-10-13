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
     * applyToMenu writes into the SHARED inventory, so it must never touch a variant slot: the variant
     * match keys are English, and writing one viewer's language there is what makes the next viewer of
     * the same block match nothing and see the first viewer's language.
     */
    @Test
    void theOpenTimeTargetsExcludeVariantSlotsSoSharedStateStaysEnglish() {
        MenuTranslationService service = new MenuTranslationService();
        service.load("de", yaml(TWO_VARIANTS));
        service.load("de", yaml(PLAIN_SLOT));

        Assertions.assertFalse(service.targetSlotsFor("de", "CARGO_NODE_INPUT").contains(24));
        Assertions.assertTrue(service.targetSlotsFor("de", "NUCLEAR_REACTOR").contains(1));
        Assertions.assertTrue(service.targetSlotsFor("de", "NO_SUCH_PRESET").isEmpty());
    }

    /** A plain slot translated only in English must still be visited for any other language. */
    @Test
    void theOpenTimeTargetsUnionTheViewerLanguageAndEnglish() {
        MenuTranslationService service = new MenuTranslationService();
        service.load("en", yaml(PLAIN_SLOT));

        Assertions.assertTrue(service.targetSlotsFor("fr", "NUCLEAR_REACTOR").contains(1));
    }

    /**
     * C2: a plain entry is the translation of the slot's ENGLISH BASELINE, so it must not be applied to
     * an item a machine rewrote at runtime. en/menus.yml ships ADVANCED_STORAGE slot 4 as "&bStatus"
     * while InfinityExpansion's StorageCache replaces that slot with live stored-item text; without
     * this gate the packet path froze every storage unit on "Status" forever, in every language.
     */
    @Test
    void aPlainEntryIsNotAppliedToAnItemTheMachineRewrote() {
        MenuTranslationService service = new MenuTranslationService();
        service.load("en", yaml("ADVANCED_STORAGE:\n  '4':\n    name: '&bStatus'\n"));
        service.load("de", yaml("ADVANCED_STORAGE:\n  '4':\n    name: '&bZustand'\n"));

        Assertions.assertEquals("&bZustand",
            service.resolveSlot("de", "ADVANCED_STORAGE", 4, "&bStatus").getName());
        Assertions.assertNull(service.resolveSlot("de", "ADVANCED_STORAGE", 4, "&bStored: 64 Cobblestone"));
    }

    /** With no English name to compare against there is no baseline, so a lore-only entry still applies. */
    @Test
    void aLoreOnlyEntryStillAppliesBecauseItReplacesNoName() {
        MenuTranslationService service = new MenuTranslationService();
        service.load("en", yaml("P:\n  '3':\n    lore:\n    - 'english line'\n"));
        service.load("de", yaml("P:\n  '3':\n    lore:\n    - 'deutsche Zeile'\n"));

        Assertions.assertEquals("deutsche Zeile",
            service.resolveSlot("de", "P", 3, "whatever the machine wrote").getLore().get(0));
    }

    /** I4: the spec's documented chain is variant, then the slot's own entry, then nothing. */
    @Test
    void aVariantMissFallsBackToTheSlotsOwnBaselineEntry() {
        MenuTranslationService service = new MenuTranslationService();
        service.load("en", yaml(
            "P:\n  '5':\n    name: 'Baseline'\n    variants:\n    - match: 'State A'\n"
            + "      name: 'State A'\n"));
        service.load("de", yaml(
            "P:\n  '5':\n    name: 'Grundlinie'\n    variants:\n    - match: 'State A'\n"
            + "      name: 'Zustand A'\n"));

        Assertions.assertEquals("Zustand A", service.resolveSlot("de", "P", 5, "State A").getName());
        Assertions.assertEquals("Grundlinie", service.resolveSlot("de", "P", 5, "Baseline").getName());
        Assertions.assertNull(service.resolveSlot("de", "P", 5, "something else entirely"));
    }

    /** I7: a variant-only slot must still count towards the i18n coverage audit. */
    @Test
    void coverageCountsVariantOnlySlots() {
        MenuTranslationService service = new MenuTranslationService();
        service.load("en", yaml(TWO_VARIANTS));

        Assertions.assertTrue(service.getKeyCoverage("en")[1] > 0);
    }

    /**
     * The real shipped entry that the packet path froze: en/menus.yml names ADVANCED_STORAGE slot 4
     * "Status" while InfinityExpansion's StorageCache replaces that slot with the live stored-item
     * line on every update. Pinned against the shipped file, not a synthetic copy of it.
     */
    @Test
    void theShippedStorageStatusEntryLeavesLiveTextAlone() throws Exception {
        MenuTranslationService service = new MenuTranslationService();

        try (InputStream stream = MenuTranslationService.class
                .getResourceAsStream("/languages/en/menus.yml")) {
            Assertions.assertNotNull(stream, "en/menus.yml is not on the test classpath");
            service.load("en", stream);
        }

        Assertions.assertNotNull(service.resolveSlot("en", "ADVANCED_STORAGE", 4, "&bStatus"));
        Assertions.assertNull(service.resolveSlot("en", "ADVANCED_STORAGE", 4, "&bStored: 4096 Cobblestone"));
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
