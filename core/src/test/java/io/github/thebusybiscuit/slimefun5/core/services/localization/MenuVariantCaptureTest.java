package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MenuVariantCaptureTest {

    private static InputStream yaml(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static MenuTranslationService german(String content) {
        MenuTranslationService service = new MenuTranslationService();
        service.load("de", yaml(content));
        return service;
    }

    @Test
    void aCaptureInTheNameIsCarriedIntoTheTranslation() {
        MenuTranslationService service = german(
            "CARGO_NODE:\n  '13':\n    variants:\n"
            + "    - match: '&bChannel ID: &3{0}'\n"
            + "      name: '&bKanal-ID: &3{0}'\n");

        Assertions.assertEquals("&bKanal-ID: &3" + 7,
            service.resolveSlot("de", "CARGO_NODE", 13, "§bChannel ID: §37", Collections.emptyList()).getName());
    }

    @Test
    void matchLoreTellsApartStatesThatShareAName() {
        MenuTranslationService service = german(
            "REACTOR:\n  '22':\n    variants:\n"
            + "    - match: '&7Access Port'\n"
            + "      match-lore: '&6Detected'\n"
            + "      name: '&7Zugangsport'\n"
            + "      lore: ['', '&6Erkannt']\n"
            + "    - match: '&7Access Port'\n"
            + "      match-lore: '&cNot detected'\n"
            + "      name: '&7Zugangsport'\n"
            + "      lore: ['', '&cNicht erkannt']\n");

        Assertions.assertEquals("&6Erkannt", service.resolveSlot("de", "REACTOR", 22, "&7Access Port",
            Arrays.asList("", "&6Detected", "", "&7> Click to view Access Port")).getLore().get(1));
        Assertions.assertEquals("&cNicht erkannt", service.resolveSlot("de", "REACTOR", 22, "&7Access Port",
            Arrays.asList("", "&cNot detected")).getLore().get(1));
    }

    @Test
    void aVariantWhoseLoreLineIsAbsentDoesNotMatch() {
        MenuTranslationService service = german(
            "REACTOR:\n  '22':\n    variants:\n"
            + "    - match: '&7Access Port'\n"
            + "      match-lore: '&6Detected'\n"
            + "      name: '&7Zugangsport'\n");

        Assertions.assertNull(service.resolveSlot("de", "REACTOR", 22, "&7Access Port", Arrays.asList("", "&cNot detected")));
    }

    @Test
    void capturesFromSeveralLoreLinesAreCarriedIntoTheTranslatedLore() {
        MenuTranslationService service = german(
            "SOLAR:\n  '4':\n    variants:\n"
            + "    - match: '&aGenerating'\n"
            + "      match-lore: ['&bRate: {0}', '&7Stored: &6{1}']\n"
            + "      name: '&aErzeugt'\n"
            + "      lore: ['&bRate: {0}', '&7Gespeichert: &6{1}']\n");

        List<String> lore = service.resolveSlot("de", "SOLAR", 4, "§aGenerating",
            Arrays.asList("§bRate: 120 J/s", "§7Stored: §65000 J")).getLore();

        Assertions.assertEquals(Arrays.asList("&bRate: 120 J/s", "&7Gespeichert: &65000 J"), lore);
    }

    /** The authored text is matched literally around the captures, including regex metacharacters. */
    @Test
    void literalTextAroundCapturesIsNotARegularExpression() {
        MenuTranslationService service = german(
            "REFINERY:\n  '32':\n    variants:\n"
            + "    - match: '&6Current Recipe: &fGold Ingot &7({0} Carat)'\n"
            + "      name: '&6Aktuelles Rezept: &fGoldbarren &7({0} Karat)'\n");

        Assertions.assertEquals("&6Aktuelles Rezept: &fGoldbarren &7(24 Karat)",
            service.resolveSlot("de", "REFINERY", 32, "&6Current Recipe: &fGold Ingot &7(24 Carat)",
                Collections.emptyList()).getName());
        Assertions.assertNull(service.resolveSlot("de", "REFINERY", 32, "&6Current Recipe: &fGold Ingot &7x24 Carat]",
            Collections.emptyList()));
    }

    @Test
    void aCaptureDoesNotMatchAnEmptyValue() {
        MenuTranslationService service = german(
            "CARGO_NODE:\n  '13':\n    variants:\n"
            + "    - match: 'Channel ID: {0}'\n"
            + "      name: 'Kanal-ID: {0}'\n");

        Assertions.assertNull(service.resolveSlot("de", "CARGO_NODE", 13, "Channel ID: ", Collections.emptyList()));
    }
}
