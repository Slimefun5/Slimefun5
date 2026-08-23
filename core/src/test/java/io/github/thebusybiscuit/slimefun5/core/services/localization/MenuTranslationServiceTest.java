package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * Covers the machine-GUI title resolution ({@link MenuTranslationService#resolveTitleFor}) that backs
 * per-viewer translated inventory titles - including its fallback to a preset's item name (via the real
 * {@link Slimefun#getItemTranslationService()} singleton) when the language has no explicit {@code title:}
 * override - isolated from Player/locale plumbing (see {@link GuideBookDisplayTest}'s note on MockBukkit's
 * unit-test LocalizationService having no default language).
 */
class MenuTranslationServiceTest {

    @BeforeAll
    public static void load() {
        MockBukkit.mock();
        MockBukkit.load(Slimefun.class);
    }

    @AfterAll
    public static void unload() {
        MockBukkit.unmock();
    }

    private static InputStream yaml(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a language with a title override returns the translated title")
    void languageWithOverrideReturnsTranslatedTitle() {
        MenuTranslationService service = new MenuTranslationService();
        service.load("de", yaml("ENHANCED_CRAFTING_TABLE:\n  title: '&9Verbesserter Handwerkstisch'\n"));

        String result = service.resolveTitleFor("de", "ENHANCED_CRAFTING_TABLE", "&9Enhanced Crafting Table");
        Assertions.assertEquals("§9Verbesserter Handwerkstisch", result);
    }

    @Test
    @DisplayName("a language without any menus.yml entry keeps the existing (English) title")
    void languageWithNoDataReturnsNull() {
        MenuTranslationService service = new MenuTranslationService();
        Assertions.assertNull(service.resolveTitleFor("fr", "ENHANCED_CRAFTING_TABLE", "&9Enhanced Crafting Table"));
    }

    @Test
    @DisplayName("a preset with no title override in an otherwise-loaded language keeps the existing title")
    void presetWithoutOverrideReturnsNull() {
        MenuTranslationService service = new MenuTranslationService();
        service.load("de", yaml("SOME_OTHER_MACHINE:\n  title: '&9Andere Maschine'\n"));

        Assertions.assertNull(service.resolveTitleFor("de", "ENHANCED_CRAFTING_TABLE", "&9Enhanced Crafting Table"));
    }

    @Test
    @DisplayName("a null (unresolved) language keeps the existing title")
    void nullLanguageReturnsNull() {
        MenuTranslationService service = new MenuTranslationService();
        service.load("de", yaml("ENHANCED_CRAFTING_TABLE:\n  title: '&9Verbesserter Handwerkstisch'\n"));

        Assertions.assertNull(service.resolveTitleFor(null, "ENHANCED_CRAFTING_TABLE", "&9Enhanced Crafting Table"));
    }

    @Test
    @DisplayName("a translated title identical to the current one is treated as no override (no needless resend)")
    void identicalTranslationReturnsNull() {
        MenuTranslationService service = new MenuTranslationService();
        service.load("de", yaml("ENHANCED_CRAFTING_TABLE:\n  title: '&9Enhanced Crafting Table'\n"));

        Assertions.assertNull(service.resolveTitleFor("de", "ENHANCED_CRAFTING_TABLE", "&9Enhanced Crafting Table"));
    }

    @Test
    @DisplayName("per-slot name/lore translations still load correctly alongside a title entry")
    void titleEntryDoesNotBreakSlotLoading() {
        MenuTranslationService service = new MenuTranslationService();
        service.load("de", yaml("ENHANCED_CRAFTING_TABLE:\n"
            + "  title: '&9Verbesserter Handwerkstisch'\n"
            + "  4:\n"
            + "    name: '&7Ein Slot'\n"));

        String result = service.resolveTitleFor("de", "ENHANCED_CRAFTING_TABLE", "&9Enhanced Crafting Table");
        Assertions.assertEquals("§9Verbesserter Handwerkstisch", result);
    }

    @Test
    @DisplayName("an explicit title override wins over the item-name fallback")
    void explicitOverrideWinsOverItemNameFallback() {
        Slimefun.getItemTranslationService().loadTranslationsForTest("de",
            yaml("MTS_OVERRIDE_WINS:\n  name: '&aItem Name Should Lose'\n"));

        MenuTranslationService service = new MenuTranslationService();
        service.load("de", yaml("MTS_OVERRIDE_WINS:\n  title: '&9Menu Title Should Win'\n"));

        String result = service.resolveTitleFor("de", "MTS_OVERRIDE_WINS", "Original Title");
        Assertions.assertEquals("§9Menu Title Should Win", result);
    }

    @Test
    @DisplayName("the item's translated name is used when the language has no title override")
    void itemNameFallbackUsedWhenNoTitleOverrideExists() {
        Slimefun.getItemTranslationService().loadTranslationsForTest("de",
            yaml("MTS_ITEM_NAME_FALLBACK:\n  name: '&aFallback Item Name'\n"));

        MenuTranslationService service = new MenuTranslationService();

        String result = service.resolveTitleFor("de", "MTS_ITEM_NAME_FALLBACK", "Original Title");
        Assertions.assertEquals(ChatColor.translateAlternateColorCodes('&', "&aFallback Item Name"), result);
    }

    @Test
    @DisplayName("with neither a title override nor a translated item name, the preset keeps its English title")
    void neitherOverrideNorItemNameKeepsEnglishPresetTitle() {
        MenuTranslationService service = new MenuTranslationService();

        String result = service.resolveTitleFor("de", "MTS_COMPLETELY_UNKNOWN_ID", "Original Title");
        Assertions.assertNull(result);
    }

    @Test
    @DisplayName("a German viewer sees the German item name as the Trash Can menu title, with no hand-authored menu title needed")
    void germanViewerGetsGermanItemNameForTrashCan() throws Exception {
        // Reads the real shipped de/items.yml (rather than a hand-typed copy, which could drift from it),
        // but re-feeds only the TRASH_CAN_BLOCK entry into the singleton - loading the whole file would
        // also register every other id's German name and break the ENHANCED_CRAFTING_TABLE-based tests
        // above, which rely on that preset having no "de" item translation loaded in this test class.
        String realGermanName;

        try (InputStream stream = Slimefun.class.getResourceAsStream("/languages/de/items.yml")) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            realGermanName = config.getString("TRASH_CAN_BLOCK.name");
        }

        Assertions.assertNotNull(realGermanName, "TRASH_CAN_BLOCK must have a German name in the real bundled resource");
        Slimefun.getItemTranslationService().loadTranslationsForTest("de",
            yaml("TRASH_CAN_BLOCK:\n  name: '" + realGermanName.replace("'", "''") + "'\n"));

        MenuTranslationService service = new MenuTranslationService();

        String result = service.resolveTitleFor("de", "TRASH_CAN_BLOCK", "Trash Can");
        Assertions.assertEquals(ChatColor.translateAlternateColorCodes('&', realGermanName), result);
    }
}
