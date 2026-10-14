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

    /** Pinned against the shipped files: the live text below is exactly what the call sites write. */
    @Test
    void theShippedCoreTogglesResolveFromTheLiveEnglishText() throws Exception {
        MenuTranslationService service = new MenuTranslationService();

        for (String language : Arrays.asList("en", "de", "ja")) {
            try (InputStream stream = MenuTranslationService.class.getResourceAsStream("/languages/" + language + "/menus.yml")) {
                Assertions.assertNotNull(stream, language + "/menus.yml is not on the test classpath");
                service.load(language, stream);
            }
        }

        Assertions.assertEquals("&bKanal-ID: &312", service.resolveSlot("de", "CARGO_NODE_OUTPUT", 13,
            "§bChannel ID: §312", Collections.emptyList()).getName());
        Assertions.assertEquals("&cNicht erkannt", service.resolveSlot("de", "NUCLEAR_REACTOR", 49, "§7Access Port",
            Arrays.asList("", "§cNot detected", "", "§7Access Port must be")).getLore().get(1));
        Assertions.assertEquals("&6Erkannt", service.resolveSlot("de", "NUCLEAR_REACTOR", 49, "§7Access Port",
            Arrays.asList("", "§6Detected", "", "§7> Click to view Access Port")).getLore().get(1));
        Assertions.assertEquals("&7オフセット: &30.5 ブロック", service.resolveSlot("ja", "WITHER_ASSEMBLER", 31,
            "§7Offset: §30.5 Block(s)", Collections.emptyList()).getName());
    }

    /** Pinned against the shipped files: the live text below is exactly what the addon call sites write. */
    @Test
    void theShippedAddonTogglesResolveFromTheLiveEnglishText() throws Exception {
        MenuTranslationService service = new MenuTranslationService();

        for (String language : Arrays.asList("en", "de")) {
            try (InputStream stream = MenuTranslationService.class.getResourceAsStream("/languages/" + language + "/menus.yml")) {
                Assertions.assertNotNull(stream, language + "/menus.yml is not on the test classpath");
                service.load(language, stream);
            }
        }

        MenuTranslationService.MenuItemTranslation keyItem = service.resolveSlot("de", "AUTO_CRAFTING_TABLE", 16, "",
            Arrays.asList("§eSneak and Right Click the", "§eAuto Crafting Table with an item", "§eto change the target recipe"));
        Assertions.assertNull(keyItem.getName());
        Assertions.assertEquals("&eSchleiche und rechtsklicke die", keyItem.getLore().get(0));

        Assertions.assertEquals("&e&lInaktiv", service.resolveSlot("de", "AUTO_CRAFTING_TABLE", 23, "§e§lIdle").getName());
        Assertions.assertEquals("&a&lHerstellung läuft", service.resolveSlot("de", "AUTO_CRAFTING_TABLE", 23, "§a§lCrafting").getName());

        MenuTranslationService.MenuItemTranslation barrel = service.resolveSlot("de", "SMALL_FLUFFY_BARREL", 22, "§6Items Stored: §e12 / 64",
            Arrays.asList("§b0.19 Stacks §8| §718.75§7%"));
        Assertions.assertEquals("&6Gelagerte Gegenstände: &e12 / 64", barrel.getName());
        Assertions.assertEquals("&b0.19 Stapel &8| &718.75&7%", barrel.getLore().get(0));

        MenuTranslationService.MenuItemTranslation solar = service.resolveSlot("de", "ADVANCED_SOLAR_PANEL", 4, "§aGenerating",
            Arrays.asList("", "§bRate: §aOverworld §e(Day)", "§7Generating at §6120 J/s §8(6 J/t)", "", "§7Stored: §6126 J"));
        Assertions.assertEquals("&bLeistung: &aOberwelt &e(Tag)", solar.getLore().get(1));
        Assertions.assertEquals("&7Gespeichert: &6126 J", solar.getLore().get(4));

        Assertions.assertEquals("&7Typ: &fWhitelist", service.resolveSlot("de", "CT_IMPORT_BUS", 23, "§7Type: §fWhitelist",
            Arrays.asList("", "§e> Click to change it to Blacklist")).getName());
        Assertions.assertEquals("&aZeit bis Leere-Barren nötig: 40", service.resolveSlot("de", "INFINITY_REACTOR", 13,
            "§aGenerating...", Arrays.asList("§aTime until infinity ingot needed: 900", "§aTime until void ingot needed: 40"))
            .getLore().get(1));
    }

    /** Pinned against the shipped files: the live text below is exactly what the status call sites write. */
    @Test
    void theShippedMachineStatusesResolveFromTheLiveEnglishText() throws Exception {
        MenuTranslationService service = new MenuTranslationService();

        for (String language : Arrays.asList("en", "de")) {
            try (InputStream stream = MenuTranslationService.class.getResourceAsStream("/languages/" + language + "/menus.yml")) {
                Assertions.assertNotNull(stream, language + "/menus.yml is not on the test classpath");
                service.load(language, stream);
            }
        }

        Assertions.assertEquals("&8Inaktiv", service.resolveSlot("de", "COBBLE_PRESS", 22, "§8Idle").getName());
        Assertions.assertEquals("&aVerarbeite...", service.resolveSlot("de", "COBBLE_PRESS", 22, "§aProcessing...").getName());

        MenuTranslationService.MenuItemTranslation panel = service.resolveSlot("de", "BASIC_PANEL", 4, "§aGeneration",
            Arrays.asList("§7Type: §6Day", "§7Generating: §610 J/s ", "§7Stored: §6420 J"));
        Assertions.assertEquals("&7Typ: &6Tag", panel.getLore().get(0));
        Assertions.assertEquals("&7Gespeichert: &6420 J", panel.getLore().get(2));

        MenuTranslationService.MenuItemTranslation constructor = service.resolveSlot("de", "SINGULARITY_CONSTRUCTOR", 13,
            "§aConstructing §fIron Singularity...", Arrays.asList("§712 / 64"));
        Assertions.assertEquals("&aBaue Iron Singularity...", constructor.getName());
        Assertions.assertEquals("&712 / 64", constructor.getLore().get(0));

        Assertions.assertEquals("&7Versuche: &e3 &7/ &e10", service.resolveSlot("de", "SUPREME_FOUNDRY_II", 23,
            "§cLoad more material to start", Arrays.asList("", "§7Attempts: §e3 §7/ §e10", "§7Progress: §e1 §7/ §e4", ""))
            .getLore().get(1));
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
