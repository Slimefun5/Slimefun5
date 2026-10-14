package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The per-viewer menus (android script editor, GPS panels, hologram projector, elevator and
 * teleporter) resolve these keys with the viewer in hand, so a language that lacks one shows English.
 */
class PerViewerMenuMessagesTest {

    private static final List<String> KEYS = Arrays.asList(
        "android.scripts.gui.return-lore", "android.scripts.gui.add-command", "android.scripts.gui.instruction-lore",
        "android.scripts.gui.upload", "android.scripts.gui.upload-lore", "android.scripts.gui.back",
        "android.scripts.gui.back-lore", "android.scripts.gui.edit", "android.scripts.gui.edit-lore",
        "android.scripts.gui.create", "android.scripts.gui.create-lore", "android.scripts.gui.download",
        "android.scripts.gui.download-lore", "android.scripts.gui.do-nothing", "android.scripts.gui.author",
        "android.scripts.gui.downloads", "android.scripts.gui.rating", "android.scripts.gui.download-hint",
        "android.scripts.gui.rate-hint",
        "gps.gui.network-info", "gps.gui.status", "gps.gui.complexity", "gps.gui.transmitter", "gps.gui.world",
        "gps.gui.signal-strength", "gps.gui.ping", "gps.gui.delete",
        "machines.HOLOGRAM_PROJECTOR.edit-text", "machines.HOLOGRAM_PROJECTOR.offset",
        "machines.HOLOGRAM_PROJECTOR.offset-controls", "machines.ELEVATOR.edit-name",
        "machines.TELEPORTER.gui.menu-title");

    private static YamlConfiguration messages(String language) throws Exception {
        try (InputStream stream = PerViewerMenuMessagesTest.class.getResourceAsStream("/languages/" + language + "/messages.yml")) {
            Assertions.assertNotNull(stream, language + "/messages.yml is not on the test classpath");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    private static List<String> languages() {
        URL english = PerViewerMenuMessagesTest.class.getResource("/languages/en");
        Assertions.assertNotNull(english, "languages/en is not on the test classpath");
        List<String> found = new ArrayList<>();

        for (File folder : new File(english.getPath().replace("%20", " ")).getParentFile().listFiles(File::isDirectory)) {
            if (new File(folder, "messages.yml").isFile()) {
                found.add(folder.getName());
            }
        }

        return found;
    }

    @Test
    void everyLanguageTranslatesEveryPerViewerMenuKey() throws Exception {
        YamlConfiguration english = messages("en");
        List<String> languages = languages();
        Assertions.assertTrue(languages.size() > 40, "expected every bundled language, found " + languages);
        List<String> problems = new ArrayList<>();

        for (String language : languages) {
            YamlConfiguration translated = messages(language);

            for (String key : KEYS) {
                if (!translated.isSet(key)) {
                    problems.add(language + ": " + key + " is missing");
                } else if (!language.equals("en") && String.valueOf(translated.get(key)).equals(String.valueOf(english.get(key)))) {
                    problems.add(language + ": " + key + " is still English");
                }
            }
        }

        Assertions.assertEquals(new ArrayList<String>(), problems);
    }
}
