package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AddonItemGroupNameTest {

    private static InputStream yaml(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void addsAnAddonsGroupNameUnderItsOwnNamespace() {
        YamlConfiguration categories = new YamlConfiguration();
        SlimefunLocalization.mergeItemGroupNames(categories, yaml("foxymachines:\n  machines: 'Maschinen'\n"));

        Assertions.assertEquals("Maschinen", categories.getString("foxymachines.machines"));
    }

    @Test
    void leavesCoresOwnNamesAlone() {
        YamlConfiguration categories = new YamlConfiguration();
        categories.set("slimefun.weapons", "Waffen");
        SlimefunLocalization.mergeItemGroupNames(categories, yaml("slimefun:\n  weapons: 'Hijacked'\n"));

        Assertions.assertEquals("Waffen", categories.getString("slimefun.weapons"));
    }

    @Test
    void twoAddonsDoNotCollide() {
        YamlConfiguration categories = new YamlConfiguration();
        SlimefunLocalization.mergeItemGroupNames(categories, yaml("foxymachines:\n  tools: 'Werkzeuge'\n"));
        SlimefunLocalization.mergeItemGroupNames(categories, yaml("dynatech:\n  tools: 'Geraete'\n"));

        Assertions.assertEquals("Werkzeuge", categories.getString("foxymachines.tools"));
        Assertions.assertEquals("Geraete", categories.getString("dynatech.tools"));
    }

    @Test
    void keepsExistingKeysWhenAJarShipsADuplicate() {
        YamlConfiguration categories = new YamlConfiguration();
        SlimefunLocalization.mergeItemGroupNames(categories, yaml("foxymachines:\n  tools: 'Werkzeuge'\n"));
        SlimefunLocalization.mergeItemGroupNames(categories, yaml("foxymachines:\n  tools: 'Zweite'\n"));

        Assertions.assertEquals("Werkzeuge", categories.getString("foxymachines.tools"));
    }

    /**
     * {@code getString} renders a section as its {@code toString}, so a section copied in as a value
     * would surface "MemorySection[...]" as a group name.
     */
    @Test
    void copiesOnlyStringsSoNoGroupNameIsASection() {
        YamlConfiguration categories = new YamlConfiguration();
        SlimefunLocalization.mergeItemGroupNames(categories, yaml("foxymachines:\n  machines: 'Maschinen'\n  count: 4\n"));

        Assertions.assertFalse(categories.isString("foxymachines"));
        Assertions.assertFalse(categories.isSet("foxymachines.count"));
        Assertions.assertTrue(categories.isString("foxymachines.machines"));
    }

    @Test
    void malformedYamlAddsNothingAndDoesNotThrow() {
        YamlConfiguration categories = new YamlConfiguration();
        categories.set("foxymachines.tools", "Werkzeuge");
        SlimefunLocalization.mergeItemGroupNames(categories, yaml("foxymachines:\n\ttools: ['unclosed\n"));

        Assertions.assertEquals("Werkzeuge", categories.getString("foxymachines.tools"));
    }
}
