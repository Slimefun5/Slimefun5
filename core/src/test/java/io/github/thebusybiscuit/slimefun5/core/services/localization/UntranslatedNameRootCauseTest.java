package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * Reproduces the "149 items report an untranslated name" bug (2026-08) using the REAL SlimeTinker
 * {@code en/items.yml} pulled from the deployed jar ({@code SlimeTinker-1.1.3.2.13-UNOFFICIAL-d810b92.jar}),
 * copied verbatim into {@code core/src/test/resources/fixtures/slimetinker-en-items.yml}. If this test
 * fails, {@link ItemTranslationService#load} genuinely cannot parse a subset of this real file; if it
 * passes, the bug is elsewhere (ordering / addon-side, not parsing).
 */
class UntranslatedNameRootCauseTest {

    private static final String FIXTURE = "fixtures/slimetinker-en-items.yml";

    @BeforeAll
    public static void load() {
        MockBukkit.mock();
        MockBukkit.load(Slimefun.class);
    }

    @AfterAll
    public static void unload() {
        MockBukkit.unmock();
    }

    /** Every id that has its own top-level {@code name:} entry in the raw fixture, derived independently
     *  of {@link ItemTranslationService} (a second, from-scratch parse) so a bug in the service's own id
     *  derivation cannot mask itself here. */
    private static Set<String> namedIdsInRawFixture() throws IOException {
        try (InputStream stream = new FileInputStream("src/test/resources/" + FIXTURE)) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            Set<String> named = new HashSet<>();

            for (String key : config.getKeys(false)) {
                if (config.isConfigurationSection(key) && config.getConfigurationSection(key).getString("name") != null) {
                    named.add(key);
                }
            }

            return named;
        }
    }

    @Test
    void everyNamedIdInTheRealFixtureResolvesThroughLoad() throws IOException, FileNotFoundException {
        ItemTranslationService svc = Slimefun.getItemTranslationService();

        try (InputStream stream = new FileInputStream("src/test/resources/" + FIXTURE)) {
            svc.loadTranslationsForTest("en", stream);
        }

        Set<String> expected = namedIdsInRawFixture();
        List<String> unresolved = new ArrayList<>();

        for (String id : expected) {
            if (svc.resolveNameForTest("en", id) == null) {
                unresolved.add(id);
            }
        }

        Assertions.assertTrue(unresolved.isEmpty(),
            "expected " + expected.size() + " named ids to resolve; " + unresolved.size()
                + " did not (first 20): " + unresolved.subList(0, Math.min(20, unresolved.size())));
    }

    @Test
    void specificKnownFailingIdsResolve() throws IOException {
        ItemTranslationService svc = Slimefun.getItemTranslationService();

        try (InputStream stream = new FileInputStream("src/test/resources/" + FIXTURE)) {
            svc.loadTranslationsForTest("en", stream);
        }

        String[] reportedFailing = { "GROUT", "SEARED_BRICK", "SMELTERY_CONTROLLER", "NUGGET_CAST_COPPER", "MOD_PLATE" };

        for (String id : reportedFailing) {
            Assertions.assertNotNull(svc.resolveNameForTest("en", id), id + " must resolve a name");
        }
    }
}
