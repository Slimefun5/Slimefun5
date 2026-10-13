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

    @BeforeAll
    public static void load() {
        MockBukkit.mock();
        MockBukkit.load(Slimefun.class);

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

    /** An unloaded language falls through to English. */
    @Test
    void unloadedLanguageFallsBackToEnglish() {
        VanillaTranslationService svc = Slimefun.getVanillaTranslationService();
        Assertions.assertEquals("Oak Log", svc.material("zz", "oak_log"));
    }

    /** A key in neither table keeps the humanize output. */
    @Test
    void unknownKeyFallsBackToHumanize() {
        VanillaTranslationService svc = Slimefun.getVanillaTranslationService();
        Assertions.assertEquals("Made Up Thing", svc.material("de", "MADE_UP_THING"));
        Assertions.assertEquals("Made Up Thing", svc.entity(null, "made_up_thing"));
    }

    /** A legacy 1.8 name resolves through XSeries to its modern path (RAW_CHICKEN is CHICKEN). */
    @Test
    void legacyMaterialNameMapsToModernPath() {
        VanillaTranslationService svc = Slimefun.getVanillaTranslationService();
        Assertions.assertEquals("Rohes Huhn", svc.material("de", "RAW_CHICKEN"));
    }

    /** A malformed table must not break later lookups. */
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

    @Test
    void nullLanguageWithMaterialKeyUsesEnglish() {
        Assertions.assertEquals("Oak Log", Slimefun.getVanillaTranslationService().material(null, "oak_log"));
    }

    @Test
    void keyMissingEverywhereIsHumanized() {
        Assertions.assertEquals("Not A Block", Slimefun.getVanillaTranslationService().material("en", "NOT_A_BLOCK"));
    }

    @Test
    void absentLanguageResourceIsHarmless() {
        VanillaTranslationService svc = new VanillaTranslationService();
        svc.ensureLoaded("si-LK");
        svc.ensureLoaded("si-LK");
        Assertions.assertEquals("Made Up", svc.material("si-LK", "made_up"));
    }

    @Test
    void bundledEnglishTableHasExpectedValues() {
        VanillaTranslationService svc = new VanillaTranslationService();
        svc.loadBundledEnglish();
        Assertions.assertEquals("Zombie", svc.entity("en", "zombie"));
        Assertions.assertEquals("Chicken", svc.name("en", Kind.ENTITY, "chicken"));
        Assertions.assertEquals("Raw Chicken", svc.name("en", Kind.MATERIAL, "chicken"));
        Assertions.assertEquals("Wheat", svc.material("en", "wheat"));
    }

    @Test
    void modernNamesXSeriesWouldRemapKeepTheirOwnNames() {
        VanillaTranslationService svc = new VanillaTranslationService();
        svc.loadBundledEnglish();
        Assertions.assertEquals("Brick", svc.material("en", "brick"));
        Assertions.assertEquals("Bricks", svc.material("en", "bricks"));
        Assertions.assertEquals("Melon", svc.material("en", "melon"));
        Assertions.assertEquals("Empty Map", svc.material("en", "map"));
        Assertions.assertEquals("Map", svc.material("en", "filled_map"));
        Assertions.assertEquals("Grass", svc.material("en", "grass"));
        Assertions.assertEquals("Sign", svc.material("en", "sign"));
        Assertions.assertNotEquals(svc.material("en", "nether_brick"), svc.material("en", "nether_bricks"));
    }

    /**
     * Review Focus 2: CHICKEN is both an EntityType and a Material, and it is in SoulJars' configured
     * mob list. A %MOB% family must resolve it as the ENTITY; only %MATERIAL% may see "Raw Chicken".
     */
    @Test
    void collidingNameResolvesAsTheDeclaredKind() {
        VanillaTranslationService svc = Slimefun.getVanillaTranslationService();
        Assertions.assertEquals("Huhn", svc.name("de", Kind.ENTITY, "CHICKEN"));
        Assertions.assertEquals("Rohes Huhn", svc.name("de", Kind.MATERIAL, "CHICKEN"));
        Assertions.assertNotEquals(svc.name("de", Kind.ENTITY, "CHICKEN"),
            svc.name("de", Kind.MATERIAL, "CHICKEN"));
    }
}
