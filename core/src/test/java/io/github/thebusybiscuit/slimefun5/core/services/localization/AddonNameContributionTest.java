package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AddonNameContributionTest {

    private static InputStream yaml(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void addsAnAddonsGroupNameUnderItsOwnNamespace() {
        YamlConfiguration categories = new YamlConfiguration();
        SlimefunLocalization.mergeAddonNames(categories, yaml("foxymachines:\n  machines: 'Maschinen'\n"));

        Assertions.assertEquals("Maschinen", categories.getString("foxymachines.machines"));
    }

    @Test
    void leavesCoresOwnNamesAlone() {
        YamlConfiguration categories = new YamlConfiguration();
        categories.set("slimefun.weapons", "Waffen");
        SlimefunLocalization.mergeAddonNames(categories, yaml("slimefun:\n  weapons: 'Hijacked'\n"));

        Assertions.assertEquals("Waffen", categories.getString("slimefun.weapons"));
    }

    @Test
    void twoAddonsDoNotCollide() {
        YamlConfiguration categories = new YamlConfiguration();
        SlimefunLocalization.mergeAddonNames(categories, yaml("foxymachines:\n  tools: 'Werkzeuge'\n"));
        SlimefunLocalization.mergeAddonNames(categories, yaml("dynatech:\n  tools: 'Geraete'\n"));

        Assertions.assertEquals("Werkzeuge", categories.getString("foxymachines.tools"));
        Assertions.assertEquals("Geraete", categories.getString("dynatech.tools"));
    }

    @Test
    void keepsExistingKeysWhenAJarShipsADuplicate() {
        YamlConfiguration categories = new YamlConfiguration();
        SlimefunLocalization.mergeAddonNames(categories, yaml("foxymachines:\n  tools: 'Werkzeuge'\n"));
        SlimefunLocalization.mergeAddonNames(categories, yaml("foxymachines:\n  tools: 'Zweite'\n"));

        Assertions.assertEquals("Werkzeuge", categories.getString("foxymachines.tools"));
    }

    /**
     * {@code getString} renders a section as its {@code toString}, so a section copied in as a value
     * would surface "MemorySection[...]" as a group name.
     */
    @Test
    void copiesOnlyStringsSoNoGroupNameIsASection() {
        YamlConfiguration categories = new YamlConfiguration();
        SlimefunLocalization.mergeAddonNames(categories, yaml("foxymachines:\n  machines: 'Maschinen'\n  count: 4\n"));

        Assertions.assertFalse(categories.isString("foxymachines"));
        Assertions.assertFalse(categories.isSet("foxymachines.count"));
        Assertions.assertTrue(categories.isString("foxymachines.machines"));
    }

    @Test
    void malformedYamlAddsNothingAndDoesNotThrow() {
        YamlConfiguration categories = new YamlConfiguration();
        categories.set("foxymachines.tools", "Werkzeuge");
        SlimefunLocalization.mergeAddonNames(categories, yaml("foxymachines:\n\ttools: ['unclosed\n"));

        Assertions.assertEquals("Werkzeuge", categories.getString("foxymachines.tools"));
    }

    @Test
    void addsAnAddonsResearchNameUnderItsOwnNamespace() {
        YamlConfiguration researches = new YamlConfiguration();
        SlimefunLocalization.mergeAddonNames(researches, yaml("missilewarfare:\n  guided_missile: 'Lenkrakete'\n"));

        Assertions.assertEquals("Lenkrakete", researches.getString("missilewarfare.guided_missile"));
    }

    @Test
    void leavesCoresOwnResearchNamesAlone() {
        YamlConfiguration researches = new YamlConfiguration();
        researches.set("slimefun.walking_on_clouds", "Auf Wolken wandeln");
        SlimefunLocalization.mergeAddonNames(researches, yaml("slimefun:\n  walking_on_clouds: 'Hijacked'\n"));

        Assertions.assertEquals("Auf Wolken wandeln", researches.getString("slimefun.walking_on_clouds"));
    }
}
