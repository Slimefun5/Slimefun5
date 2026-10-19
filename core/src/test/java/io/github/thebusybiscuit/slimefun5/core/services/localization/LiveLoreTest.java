package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.Mockito;

import io.github.thebusybiscuit.slimefun5.api.items.LiveLore;

class LiveLoreTest {

    private static JavaPlugin networks;
    private static LiveLoreRenderer renderer;

    @BeforeAll
    static void setUp() {
        MockBukkit.mock();
        networks = Mockito.mock(JavaPlugin.class);
        Mockito.when(networks.getName()).thenReturn("Networks");
        AddonMessageService messages = new AddonMessageService();
        messages.load("en", "networks", yaml("storage:\n  amount: '&7Amount: &f{0}'\n  voiding-on: '&7Voiding: &fyes'\n"));
        messages.load("de", "networks", yaml("storage:\n  amount: '&7Menge: &f{0}'\n  holding: '&7Enthält: &f{0}'\n"));
        renderer = new LiveLoreRenderer(messages, (id, language) -> id + "@" + language, (material, language) -> material.toLowerCase() + "@" + language);
    }

    @AfterAll
    static void tearDown() {
        MockBukkit.unmock();
    }

    private static ByteArrayInputStream yaml(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static ItemMeta metaWith(LiveLore.Line... lines) {
        ItemMeta meta = new ItemStack(Material.DIAMOND).getItemMeta();
        LiveLore.set(meta, Arrays.asList(lines));
        return meta;
    }

    private static List<String> plain(List<String> lore) {
        String[] lines = new String[lore.size()];
        for (int index = 0; index < lines.length; index++) {
            lines[index] = ChatColor.stripColor(lore.get(index));
        }
        return Arrays.asList(lines);
    }

    @Test
    void linesSurviveTheItemMeta() {
        ItemMeta meta = metaWith(LiveLore.Line.of(networks, "storage.amount", 1234), LiveLore.Line.of(networks, "storage.voiding-on"));

        List<LiveLore.Line> lines = LiveLore.get(meta);

        Assertions.assertEquals(2, lines.size());
        Assertions.assertEquals("networks", lines.get(0).getAddonId());
        Assertions.assertEquals("storage.amount", lines.get(0).getKey());
        Assertions.assertEquals(Collections.singletonList("1234"), lines.get(0).getArguments());
        Assertions.assertTrue(lines.get(1).getArguments().isEmpty());
    }

    @Test
    void anEmptyListRemovesTheLines() {
        ItemMeta meta = metaWith(LiveLore.Line.of(networks, "storage.amount", 1));

        LiveLore.set(meta, Collections.<LiveLore.Line>emptyList());

        Assertions.assertTrue(LiveLore.get(meta).isEmpty());
    }

    @Test
    void separatorsInsideAnArgumentCannotSplitALine() {
        ItemMeta meta = metaWith(LiveLore.Line.of(networks, "storage.amount", "a\u001eb\u001fc"));

        Assertions.assertEquals(Collections.singletonList("a b c"), LiveLore.get(meta).get(0).getArguments());
    }

    @Test
    void rendersInTheViewersLanguageThenEnglishThenTheKey() {
        LiveLore.Line amount = LiveLore.Line.of(networks, "storage.amount", 42);

        Assertions.assertEquals("Menge: 42", ChatColor.stripColor(renderer.render(amount, "de")));
        Assertions.assertEquals("Voiding: yes", ChatColor.stripColor(renderer.render(LiveLore.Line.of(networks, "storage.voiding-on"), "de")));
        Assertions.assertEquals("Amount: 42", ChatColor.stripColor(renderer.render(amount, null)));
        Assertions.assertEquals("storage.missing", renderer.render(LiveLore.Line.of(networks, "storage.missing"), "de"));
    }

    @Test
    void appendsBelowExistingLoreAfterOneBlankLine() {
        ItemMeta meta = metaWith(LiveLore.Line.of(networks, "storage.amount", 7));

        List<String> lore = renderer.appendTo(Arrays.asList("Tier 2"), meta, "en");

        Assertions.assertEquals(Arrays.asList("Tier 2", "", "Amount: 7"), plain(lore));
        Assertions.assertEquals(Arrays.asList("Amount: 7"), plain(renderer.appendTo(Collections.<String>emptyList(), meta, "en")));
    }

    @Test
    void itemArgumentsAreNamedInTheViewersLanguage() {
        LiveLore.Line material = LiveLore.Line.of(networks, "storage.holding", "\u0002mc:DIAMOND_BLOCK");
        LiveLore.Line slimefun = LiveLore.Line.of(networks, "storage.holding", "\u0002sf:ELECTRIC_MOTOR");
        LiveLore.Line renamed = LiveLore.Line.of(networks, "storage.holding", "My Motor");

        Assertions.assertEquals("Enthält: diamond_block@de", ChatColor.stripColor(renderer.render(material, "de")));
        Assertions.assertEquals("Enthält: ELECTRIC_MOTOR@de", ChatColor.stripColor(renderer.render(slimefun, "de")));
        Assertions.assertEquals("Enthält: My Motor", ChatColor.stripColor(renderer.render(renamed, "de")));
    }

    @Test
    void aColourCodeInsideAnArgumentIsNotReinterpreted() {
        LiveLore.Line line = LiveLore.Line.of(networks, "storage.amount", "&c5");

        Assertions.assertEquals("Menge: &c5", ChatColor.stripColor(renderer.render(line, "de")));
    }

    @Test
    void anItemWithoutLinesKeepsItsLore() {
        List<String> lore = Arrays.asList("Tier 2");

        Assertions.assertSame(lore, renderer.appendTo(lore, new ItemStack(Material.DIAMOND).getItemMeta(), "en"));
    }
}
