package io.github.thebusybiscuit.slimefun5.core.services.localization;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.thebusybiscuit.slimefun5.api.items.ItemTier;

class ItemNameStyleTest {

    @Test
    @DisplayName("The authored codes are replaced by the tier's style")
    void authoredCodesGiveWayToTheTier() {
        Assertions.assertEquals(ChatColor.AQUA + "Electric Furnace", ItemNameStyle.render(ItemTier.RARE, "&c&lElectric &9Furnace"));
    }

    @Test
    @DisplayName("Section-sign codes are stripped as well as ampersand codes")
    void sectionSignCodesAreStripped() {
        Assertions.assertEquals(ChatColor.WHITE + "Copper Wire", ItemNameStyle.render(ItemTier.COMMON, "§6Copper §lWire"));
    }

    @Test
    @DisplayName("A hex colour is stripped whole, in either marker and either case")
    void hexColoursAreStripped() {
        Assertions.assertEquals("Molten Steel", ItemNameStyle.plainText("&x&A&A&a&a&A&AMolten Steel"));
        Assertions.assertEquals("Axe", ItemNameStyle.plainText("§x§C§FAxe"));
    }

    @Test
    @DisplayName("A trailing bracket qualifier renders grey after the name")
    void qualifierRendersGrey() {
        Assertions.assertEquals(ChatColor.YELLOW + "Gold Ingot" + ChatColor.GRAY + " (12-Carat)",
            ItemNameStyle.render(ItemTier.UNCOMMON, "&rGold Ingot &7(12-Carat)"));
    }

    @Test
    @DisplayName("Only the endgame tier is bold, and its qualifier is not")
    void endgameIsBoldButItsQualifierIsNot() {
        String rendered = ItemNameStyle.render(ItemTier.ENDGAME, "Infinity Blade (Charged)");

        Assertions.assertEquals(ChatColor.GOLD.toString() + ChatColor.BOLD + "Infinity Blade" + ChatColor.GRAY + " (Charged)", rendered);
        Assertions.assertFalse(ItemTier.EPIC.getStyle().contains(ChatColor.BOLD.toString()));
    }

    @Test
    @DisplayName("Brackets inside the name are not a qualifier")
    void innerBracketsStayInTheName() {
        Assertions.assertEquals(ChatColor.WHITE + "Rune (Air) Shard", ItemNameStyle.render(ItemTier.COMMON, "Rune (Air) Shard"));
    }

    @Test
    @DisplayName("Spacing left behind by removed codes collapses to one space")
    void spacingCollapses() {
        Assertions.assertEquals("Basic Virtual Farm", ItemNameStyle.plainText("&9Basic  &aVirtual Farm "));
    }

    @Test
    @DisplayName("A tier parses in any case and an unknown word parses to null")
    void tierParsing() {
        Assertions.assertEquals(ItemTier.EPIC, ItemTier.parse(" Epic "));
        Assertions.assertNull(ItemTier.parse("legendary"));
    }
}
