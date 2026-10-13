package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

class MenuAuthoringGateTest {

    private static final String ENGLISH_TOGGLE =
        "TOGGLE_PRESET:\n"
        + "  '24':\n"
        + "    variants:\n"
        + "    - match: '&7Round-Robin Mode: &4off'\n"
        + "      name: '&7Round-Robin Mode: &4off'\n"
        + "    - match: '&7Round-Robin Mode: &2on'\n"
        + "      name: '&7Round-Robin Mode: &2on'\n";

    private static InputStream yaml(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static MenuTranslationService withEnglishToggle() {
        MenuTranslationService service = new MenuTranslationService();
        service.load("en", yaml(ENGLISH_TOGGLE));
        return service;
    }

    @Test
    void aCorrectlyTranslatedToggleHasNoProblems() {
        MenuTranslationService service = withEnglishToggle();
        service.load("de", yaml(
            "TOGGLE_PRESET:\n"
            + "  '24':\n"
            + "    variants:\n"
            + "    - match: '&7Round-Robin Mode: &4off'\n"
            + "      name: '&7Rundlauf: &4aus'\n"
            + "    - match: '&7Round-Robin Mode: &2on'\n"
            + "      name: '&7Rundlauf: &2ein'\n"));

        Assertions.assertEquals(List.of(), service.findAuthoringProblems());
    }

    @Test
    void refusesAPlainEntryWhereEnglishHasVariants() {
        MenuTranslationService service = withEnglishToggle();
        service.load("de", yaml("TOGGLE_PRESET:\n  '24':\n    name: '&7Rundlauf: &4aus'\n"));

        List<String> problems = service.findAuthoringProblems();

        Assertions.assertEquals(1, problems.size(), problems.toString());
        Assertions.assertTrue(problems.get(0).contains("de TOGGLE_PRESET/24"), problems.get(0));
        Assertions.assertTrue(problems.get(0).contains("plain entry"), problems.get(0));
    }

    @Test
    void refusesTwoVariantsWhoseMatchesDifferOnlyByColour() {
        MenuTranslationService service = new MenuTranslationService();
        service.load("en", yaml(
            "TOGGLE_PRESET:\n"
            + "  '24':\n"
            + "    variants:\n"
            + "    - match: '&7Mode: &4ON'\n"
            + "      name: '&7Mode: &4ON'\n"
            + "    - match: '&7Mode: &2ON'\n"
            + "      name: '&7Mode: &2ON'\n"));

        List<String> problems = service.findAuthoringProblems();

        Assertions.assertEquals(1, problems.size(), problems.toString());
        Assertions.assertTrue(problems.get(0).contains("en TOGGLE_PRESET/24"), problems.get(0));
        Assertions.assertTrue(problems.get(0).contains("differ only by colour"), problems.get(0));
    }

    @Test
    void refusesATranslatedMatchThatEnglishNeverProduces() {
        MenuTranslationService service = withEnglishToggle();
        service.load("de", yaml(
            "TOGGLE_PRESET:\n"
            + "  '24':\n"
            + "    variants:\n"
            + "    - match: '&7Rundlauf: &4aus'\n"
            + "      name: '&7Rundlauf: &4aus'\n"
            + "    - match: '&7Round-Robin Mode: &2on'\n"
            + "      name: '&7Rundlauf: &2ein'\n"));

        List<String> problems = service.findAuthoringProblems();

        Assertions.assertEquals(1, problems.size(), problems.toString());
        Assertions.assertTrue(problems.get(0).contains("de TOGGLE_PRESET/24"), problems.get(0));
        Assertions.assertTrue(problems.get(0).contains("'&7Rundlauf: &4aus'"), problems.get(0));
    }

    @Test
    void refusesVariantsOnASlotEnglishDoesNotVary() {
        MenuTranslationService service = new MenuTranslationService();
        service.load("en", yaml("PLAIN_PRESET:\n  '4':\n    name: '&7Status'\n"));
        service.load("de", yaml(
            "PLAIN_PRESET:\n  '4':\n    variants:\n    - match: '&7Status'\n      name: '&7Zustand'\n"));

        List<String> problems = service.findAuthoringProblems();

        Assertions.assertEquals(1, problems.size(), problems.toString());
        Assertions.assertTrue(problems.get(0).contains("de PLAIN_PRESET/4"), problems.get(0));
    }

    /** The gate that blocks bad content: every shipped language file must pass. */
    @Test
    void everyBundledMenusFileIsClean() throws IOException, URISyntaxException {
        MenuTranslationService service = new MenuTranslationService();
        List<Path> files = bundledMenusFiles();

        Assertions.assertTrue(files.size() > 1, "found no bundled menus.yml beyond English: " + files);

        for (Path file : files) {
            try (InputStream stream = Files.newInputStream(file)) {
                service.load(file.getParent().getFileName().toString(), stream);
            }
        }

        Assertions.assertEquals(List.of(), service.findAuthoringProblems());
    }

    private static List<Path> bundledMenusFiles() throws IOException, URISyntaxException {
        URL english = Slimefun.class.getResource("/languages/en/menus.yml");
        Assertions.assertNotNull(english, "languages/en/menus.yml is not on the test classpath");
        Path languages = Paths.get(english.toURI()).getParent().getParent();

        try (Stream<Path> directories = Files.list(languages)) {
            return directories
                .map(directory -> directory.resolve("menus.yml"))
                .filter(Files::isRegularFile)
                .sorted()
                .collect(Collectors.toList());
        }
    }
}
