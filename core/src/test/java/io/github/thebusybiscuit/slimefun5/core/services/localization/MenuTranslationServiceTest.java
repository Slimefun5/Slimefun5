package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * Covers the machine-GUI title resolution ({@link MenuTranslationService#resolveTitleFor}) that backs
 * per-viewer translated inventory titles, isolated from Player/locale plumbing (see
 * {@link GuideBookDisplayTest}'s note on MockBukkit's unit-test LocalizationService having no default
 * language).
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
}
