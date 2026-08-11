package io.github.thebusybiscuit.slimefun5.core.guide.installer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

class AddonManifestTest {

    @BeforeAll
    public static void load() {
        MockBukkit.mock();
        // XMaterial's static init calls Bukkit.getServer(); AddonManifest.toEntry references
        // XMaterial constants, so a mocked server must exist before parse() runs.
        MockBukkit.load(Slimefun.class);
    }

    @AfterAll
    public static void unload() {
        MockBukkit.unmock();
    }

    @Test
    void parsesCoreLibraryAndAddon() {
        String json = "{\"core\":{\"id\":\"slimefun\",\"repo\":\"Slimefun5/Slimefun5\",\"name\":\"Slimefun\",\"kind\":\"core\",\"dependencies\":[],\"pluginName\":\"Slimefun\"},"
            + "\"libraries\":[{\"id\":\"infinitylib\",\"repo\":\"Slimefun5/InfinityLib\",\"name\":\"InfinityLib\",\"kind\":\"library\",\"dependencies\":[],\"pluginName\":null}],"
            + "\"addons\":[{\"id\":\"networks\",\"repo\":\"Slimefun5/Networks\",\"name\":\"Networks\",\"kind\":\"addon\",\"dependencies\":[\"infinitylib\"],\"pluginName\":\"Networks\"}]}";
        List<AddonCatalog.Entry> entries = AddonManifest.parse(json);
        assertEquals("slimefun", entries.get(0).getId());
        assertTrue(entries.get(0).isCore());
        assertEquals("InfinityLib", entries.get(1).getRepo());
        assertTrue(entries.get(1).isLibrary());
        AddonCatalog.Entry nw = entries.get(2);
        assertEquals("Networks", nw.getRepo());
        assertEquals(List.of("infinitylib"), nw.getDependencies());
    }
}
