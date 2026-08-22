package io.github.thebusybiscuit.slimefun5.core.guide.variants;

import java.util.Arrays;
import java.util.Collections;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import io.github.thebusybiscuit.slimefun5.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun5.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;

/**
 * Covers the guide's variant grouping: many registered items presented in one slot. The invariants that
 * matter are that exactly one member anchors the slot, that the others are hidden from both the item list
 * and search, and that a display copy carries the position the packet layer renders as "(n/total)".
 */
class VariantGroupTest {

    private static Slimefun plugin;

    @BeforeAll
    static void load() {
        MockBukkit.mock();
        plugin = MockBukkit.load(Slimefun.class);
    }

    @AfterAll
    static void unload() {
        MockBukkit.unmock();
    }

    private static SlimefunItem register(String id) {
        ItemGroup group = new ItemGroup(new NamespacedKey(plugin, id.toLowerCase() + "_group"), new ItemStack(Material.EMERALD));
        SlimefunItem item = new SlimefunItem(group, new SlimefunItemStack(id, Material.PAPER), RecipeType.NULL, new ItemStack[9]);
        item.register(plugin);
        return item;
    }

    private static VariantGroup group(String key, SlimefunItem... members) {
        return new VariantGroup(new NamespacedKey(plugin, key), Arrays.asList(members)).register();
    }

    @Test
    @DisplayName("The first member anchors the slot; the rest are collapsed out of the list and search")
    void testAnchorAndCollapsedMembers() {
        SlimefunItem iron = register("VARIANT_ROD_IRON");
        SlimefunItem gold = register("VARIANT_ROD_GOLD");
        SlimefunItem lead = register("VARIANT_ROD_LEAD");

        VariantGroup rods = group("rods", iron, gold, lead);

        Assertions.assertSame(iron, rods.getAnchor());
        Assertions.assertFalse(Slimefun.getVariantGroups().isCollapsedMember(iron.getId()), "the anchor keeps its slot");
        Assertions.assertTrue(Slimefun.getVariantGroups().isCollapsedMember(gold.getId()));
        Assertions.assertTrue(Slimefun.getVariantGroups().isCollapsedMember(lead.getId()));
    }

    @Test
    @DisplayName("An ungrouped item is never treated as a group member")
    void testUngroupedItemIsUntouched() {
        SlimefunItem standalone = register("VARIANT_STANDALONE");

        Assertions.assertNull(Slimefun.getVariantGroups().getGroup(standalone.getId()));
        Assertions.assertFalse(Slimefun.getVariantGroups().isCollapsedMember(standalone.getId()));
    }

    @Test
    @DisplayName("indexOf gives the 1-based cycle position, 0 for a non-member")
    void testIndexOf() {
        SlimefunItem a = register("VARIANT_PLATE_A");
        SlimefunItem b = register("VARIANT_PLATE_B");
        VariantGroup plates = group("plates", a, b);

        Assertions.assertEquals(1, plates.indexOf(a.getId()));
        Assertions.assertEquals(2, plates.indexOf(b.getId()));
        Assertions.assertEquals(0, plates.indexOf("VARIANT_NOT_A_MEMBER"));
    }

    @Test
    @DisplayName("A marked display copy reports its position, an unmarked stack reports none")
    void testDisplayMarkerRoundTrip() {
        ItemStack marked = new ItemStack(Material.PAPER);
        VariantDisplayMarker.mark(marked, 6, 14);

        Assertions.assertEquals("6/14", VariantDisplayMarker.read(marked.getItemMeta()));
        Assertions.assertNull(VariantDisplayMarker.read(new ItemStack(Material.PAPER).getItemMeta()));
    }

    @Test
    @DisplayName("A second group cannot steal a member already claimed by the first")
    void testMemberIsNotStolen() {
        SlimefunItem shared = register("VARIANT_SHARED");
        SlimefunItem other = register("VARIANT_OTHER");

        VariantGroup first = group("first", shared, other);
        group("second", shared);

        Assertions.assertSame(first, Slimefun.getVariantGroups().getGroup(shared.getId()));
    }

    @Test
    @DisplayName("A group needs a key and at least one variant")
    void testValidation() {
        SlimefunItem only = register("VARIANT_ONLY");

        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new VariantGroup(new NamespacedKey(plugin, "empty"), Collections.<SlimefunItem>emptyList()));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new VariantGroup(null, Arrays.asList(only)));
    }
}
