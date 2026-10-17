package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.PdcCompat;

class ShownOriginalTest {

    @BeforeAll
    public static void load() {
        MockBukkit.mock();
        MockBukkit.load(Slimefun.class);
    }

    @AfterAll
    public static void unload() {
        MockBukkit.unmock();
    }

    private static ItemStack stack(String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack translate(ItemStack original, String name, List<String> lore) {
        ItemStack shown = original.clone();
        ItemMeta meta = shown.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        shown.setItemMeta(meta);
        return shown;
    }

    private static String stash(ItemStack item) {
        return PdcCompat.getString(item.getItemMeta(), Slimefun.getRegistry().getShownOriginalKey());
    }

    @Test
    void encodingSurvivesEveryDelimiterItUses() {
        List<String> lore = Arrays.asList("", "3:x", "~", "#2#", "§7DNA: 12:34");
        ShownOriginal decoded = ShownOriginal.decode(ShownOriginal.encode("~1:#", lore));

        Assertions.assertEquals("~1:#", decoded.name());
        Assertions.assertEquals(lore, decoded.lore());
    }

    @Test
    void absentNameAndLoreStayAbsentRatherThanEmpty() {
        ShownOriginal absent = ShownOriginal.decode(ShownOriginal.encode(null, null));
        ShownOriginal empty = ShownOriginal.decode(ShownOriginal.encode("", Collections.emptyList()));

        Assertions.assertNull(absent.name());
        Assertions.assertNull(absent.lore());
        Assertions.assertEquals("", empty.name());
        Assertions.assertEquals(Collections.emptyList(), empty.lore());
    }

    @Test
    void malformedValuesDecodeToNothing() {
        Assertions.assertNull(ShownOriginal.decode(null));
        Assertions.assertNull(ShownOriginal.decode(""));
        Assertions.assertNull(ShownOriginal.decode("9:short~"));
        Assertions.assertNull(ShownOriginal.decode("~~trailing"));
    }

    @Test
    void aTranslatedStackSentBackIsRestoredExactly() {
        ItemStack server = stack("POCKET_CHICKEN", Arrays.asList("§7DNA: 010110"));
        ItemStack shown = translate(server, "Taschenhuhn", Arrays.asList("Beschreibung", "§7DNA: 010110"));
        ShownOriginal.of(server).stashOnto(shown);

        Assertions.assertTrue(ShownOriginal.restore(shown));
        Assertions.assertEquals("POCKET_CHICKEN", shown.getItemMeta().getDisplayName());
        Assertions.assertEquals(Arrays.asList("§7DNA: 010110"), shown.getItemMeta().getLore());
        Assertions.assertNull(stash(shown));
    }

    @Test
    void anOriginalWithoutLoreIsRestoredWithoutLore() {
        ItemStack server = stack("RAW_ID", null);
        ItemStack shown = translate(server, "Name", Arrays.asList("Lore"));
        ShownOriginal.of(server).stashOnto(shown);

        ShownOriginal.restore(shown);

        Assertions.assertFalse(shown.getItemMeta().hasLore());
    }

    @Test
    void aStackWithoutAStashIsLeftAlone() {
        ItemStack renamed = stack("My own name", Arrays.asList("written by a player"));

        Assertions.assertFalse(ShownOriginal.restore(renamed));
        Assertions.assertEquals("My own name", renamed.getItemMeta().getDisplayName());
    }

    @Test
    void anUntranslatedStackGetsNoStash() {
        ItemStack server = stack("SAME", Arrays.asList("same"));
        ItemStack shown = server.clone();
        ShownOriginal.of(server).stashOnto(shown);

        Assertions.assertNull(stash(shown));
    }

    @Test
    void anExistingStashIsKeptBecauseItHoldsTheTrueOriginal() {
        ItemStack server = stack("REAL_ID", null);
        ItemStack leaked = translate(server, "Translated", null);
        ShownOriginal.of(server).stashOnto(leaked);
        ItemStack shownAgain = translate(leaked, "Translated again", null);

        ShownOriginal.of(leaked).stashOnto(shownAgain);
        ShownOriginal.restore(shownAgain);

        Assertions.assertEquals("REAL_ID", shownAgain.getItemMeta().getDisplayName());
    }
}
