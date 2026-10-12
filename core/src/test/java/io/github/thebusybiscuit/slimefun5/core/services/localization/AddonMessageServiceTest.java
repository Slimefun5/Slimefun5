package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AddonMessageServiceTest {

    private static InputStream yaml(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static JavaPlugin addon() {
        JavaPlugin addon = Mockito.mock(JavaPlugin.class);
        Mockito.when(addon.getName()).thenReturn("Networks");
        return addon;
    }

    @Test
    void flattensNestedKeysToDottedPaths() {
        AddonMessageService service = new AddonMessageService();
        service.load("de", "networks", yaml("crafting:\n  success: '&aFertig!'\n"));

        Assertions.assertEquals("&aFertig!", service.lookup("de", "networks", "crafting.success"));
    }

    @Test
    void firstRegistrationOfAKeyWins() {
        AddonMessageService service = new AddonMessageService();
        service.load("de", "networks", yaml("crafting:\n  success: '&aFertig!'\n"));
        service.load("de", "networks", yaml("crafting:\n  success: '&aShaded default'\n"));

        Assertions.assertEquals("&aFertig!", service.lookup("de", "networks", "crafting.success"));
    }

    @Test
    void aShadedLibraryStillContributesItsOwnKeys() {
        AddonMessageService service = new AddonMessageService();
        service.load("de", "networks", yaml("crafting:\n  success: '&aFertig!'\n"));
        service.load("de", "networks", yaml("commands:\n  help: '&7Hilfe'\n"));

        Assertions.assertEquals("&aFertig!", service.lookup("de", "networks", "crafting.success"));
        Assertions.assertEquals("&7Hilfe", service.lookup("de", "networks", "commands.help"));
    }

    @Test
    void twoAddonsMayUseTheSameKey() {
        AddonMessageService service = new AddonMessageService();
        service.load("de", "networks", yaml("title: '&aNetzwerke'\n"));
        service.load("de", "dynatech", yaml("title: '&aDynaTech'\n"));

        Assertions.assertEquals("&aNetzwerke", service.lookup("de", "networks", "title"));
        Assertions.assertEquals("&aDynaTech", service.lookup("de", "dynatech", "title"));
    }

    @Test
    void languagesAreKeptApart() {
        AddonMessageService service = new AddonMessageService();
        service.load("de", "networks", yaml("title: '&aNetzwerke'\n"));

        Assertions.assertNull(service.lookup("fr", "networks", "title"));
    }

    @Test
    void keepsListValuesForMultiLineLore() {
        AddonMessageService service = new AddonMessageService();
        service.load("de", "networks", yaml("lore:\n  - '&7Zeile eins'\n  - '&7Zeile zwei'\n"));

        Assertions.assertEquals(Arrays.asList("&7Zeile eins", "&7Zeile zwei"), service.lookup("de", "networks", "lore"));
    }

    @Test
    void dropsSectionsAndScalarsThatAreNotTextOrLists() {
        AddonMessageService service = new AddonMessageService();
        service.load("de", "networks", yaml("crafting:\n  success: '&aFertig!'\n  attempts: 3\n"));

        Assertions.assertNull(service.lookup("de", "networks", "crafting"));
        Assertions.assertNull(service.lookup("de", "networks", "crafting.attempts"));
    }

    @Test
    void malformedYamlLeavesEarlierKeysIntact() {
        AddonMessageService service = new AddonMessageService();
        service.load("de", "networks", yaml("crafting:\n  success: '&aFertig!'\n"));
        service.load("de", "networks", yaml("crafting:\n\tsuccess: ['unclosed\n"));

        Assertions.assertEquals("&aFertig!", service.lookup("de", "networks", "crafting.success"));
    }

    @Test
    void aConsoleRecipientGetsTheEnglishLiteral() {
        AddonMessageService service = new AddonMessageService();
        service.load("de", "networks", yaml("crafting:\n  success: '&aFertig!'\n"));

        Assertions.assertEquals("&aDone!", service.getMessage(null, addon(), "crafting.success", "&aDone!"));
    }

    @Test
    void aConsoleRecipientGetsTheEnglishLines() {
        AddonMessageService service = new AddonMessageService();
        List<String> english = Collections.singletonList("&7Line one");

        Assertions.assertEquals(english, service.getMessages(null, addon(), "lore", english));
    }

    @Test
    void anEmptyTranslatedListFallsBackRatherThanBlankingTheLore() {
        AddonMessageService service = new AddonMessageService();
        service.load("de", "networks", yaml("lore: []\n"));
        List<String> english = Collections.singletonList("&7Line one");

        Assertions.assertEquals(english, service.getMessages(null, addon(), "lore", english));
    }
}
