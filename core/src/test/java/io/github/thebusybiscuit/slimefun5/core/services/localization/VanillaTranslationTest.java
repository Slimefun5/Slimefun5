package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import io.github.thebusybiscuit.slimefun5.core.services.localization.VanillaTranslationService.Kind;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * MockBukkit's unit-test {@code LocalizationService} has no languages, so {@code loadBundledEnglish()}
 * would load nothing here. This goes through the {@code loadForTest} seam, exactly as
 * {@link EnchantTranslationTest} does for enchantments.
 */
class VanillaTranslationTest {

    private static Slimefun plugin;

    @BeforeAll
    public static void load() {
        MockBukkit.mock();
        plugin = MockBukkit.load(Slimefun.class);

        VanillaTranslationService svc = Slimefun.getVanillaTranslationService();
        svc.loadForTest("en", yaml("entity:\n  zombie: 'Zombie'\n  chicken: 'Chicken'\n"
                + "material:\n  oak_log: 'Oak Log'\n  chicken: 'Raw Chicken'\n"));
        svc.loadForTest("de", yaml("entity:\n  zombie: 'Zombie'\n  chicken: 'Huhn'\n"
                + "material:\n  oak_log: 'Eichenstamm'\n  chicken: 'Rohes Huhn'\n"));
    }

    private static InputStream yaml(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @AfterAll
    public static void unload() {
        MockBukkit.unmock();
    }

    @Test
    void resolvesPerLanguage() {
        VanillaTranslationService svc = Slimefun.getVanillaTranslationService();
        Assertions.assertEquals("Eichenstamm", svc.material("de", "oak_log"));
        Assertions.assertEquals("Oak Log", svc.material("en", "oak_log"));
    }

    @Test
    void entityAndMaterialAreSeparateTables() {
        VanillaTranslationService svc = Slimefun.getVanillaTranslationService();
        Assertions.assertEquals("Huhn", svc.entity("de", "chicken"));
        Assertions.assertEquals("Rohes Huhn", svc.material("de", "chicken"));
    }

    /** Review Focus 4: an unloaded language falls through to English and must not touch the disk. */
    @Test
    void unloadedLanguageFallsBackToEnglish() {
        VanillaTranslationService svc = Slimefun.getVanillaTranslationService();
        Assertions.assertEquals("Oak Log", svc.material("zz", "oak_log"));
    }

    /** Review Focus 1: a segment that is neither a known entity nor material keeps humanize output. */
    @Test
    void unknownKeyFallsBackToHumanize() {
        VanillaTranslationService svc = Slimefun.getVanillaTranslationService();
        Assertions.assertEquals("Made Up Thing", svc.material("de", "MADE_UP_THING"));
        Assertions.assertEquals("Made Up Thing", svc.entity(null, "made_up_thing"));
    }

    /** Review Focus 3: a legacy 1.8 name resolves through XSeries to its modern path (RAW_CHICKEN is CHICKEN). */
    @Test
    void legacyMaterialNameMapsToModernPath() {
        VanillaTranslationService svc = Slimefun.getVanillaTranslationService();
        Assertions.assertEquals("Rohes Huhn", svc.material("de", "RAW_CHICKEN"));
    }

    /** Review Focus 5: a malformed table must not break boot or later lookups. */
    @Test
    void malformedTableIsIgnored() {
        VanillaTranslationService svc = Slimefun.getVanillaTranslationService();
        svc.loadForTest("fr", yaml("entity:\n  : : :\n  bad indentation\n"));
        Assertions.assertEquals("Oak Log", svc.material("fr", "oak_log"));
    }

    @Test
    void nameDispatchesOnKind() {
        VanillaTranslationService svc = Slimefun.getVanillaTranslationService();
        Assertions.assertEquals("Huhn", svc.name("de", Kind.ENTITY, "chicken"));
        Assertions.assertEquals("Rohes Huhn", svc.name("de", Kind.MATERIAL, "chicken"));
    }
}
