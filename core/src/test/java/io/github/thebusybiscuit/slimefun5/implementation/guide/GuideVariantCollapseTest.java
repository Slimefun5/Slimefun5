package io.github.thebusybiscuit.slimefun5.implementation.guide;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import io.github.thebusybiscuit.slimefun5.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun5.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun5.core.guide.variants.VariantGroup;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;

/**
 * The guide's listing filter, which every listing path shares.
 *
 * @implNote Exists because the collapse was originally applied in {@code openItemGroup} only, leaving the
 *           categorized view ({@code openCategoryItemsFlat}) drawing one tile per variant - 27 pages of
 *           Resources in practice. Testing the shared filter covers both paths at once.
 */
class GuideVariantCollapseTest {

    private static ServerMock server;
    private static Slimefun plugin;

    @BeforeAll
    static void load() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Slimefun.class);
    }

    @AfterAll
    static void unload() {
        MockBukkit.unmock();
    }

    private static SlimefunItem register(String id) {
        ItemGroup group = new ItemGroup(new NamespacedKey(plugin, id.toLowerCase() + "_g"), new ItemStack(Material.EMERALD));
        SlimefunItem item = new SlimefunItem(group, new SlimefunItemStack(id, Material.PAPER), RecipeType.NULL, new ItemStack[9]);
        item.register(plugin);
        return item;
    }

    @Test
    @DisplayName("A grouped set collapses to its anchor; ungrouped items are untouched")
    void testCollapse() {
        SlimefunItem anchor = register("COLLAPSE_ROD_IRON");
        SlimefunItem second = register("COLLAPSE_ROD_GOLD");
        SlimefunItem third = register("COLLAPSE_ROD_LEAD");
        SlimefunItem loose = register("COLLAPSE_UNGROUPED");

        new VariantGroup(new NamespacedKey(plugin, "collapse_rods"), Arrays.asList(anchor, second, third)).register();

        Player p = server.addPlayer();
        List<SlimefunItem> source = new ArrayList<>(Arrays.asList(anchor, second, third, loose));

        List<SlimefunItem> shown = new SurvivalSlimefunGuide(false, false).collapseForDisplay(p, source);

        Assertions.assertEquals(2, shown.size(), "three variants plus one loose item must render as two tiles");
        Assertions.assertTrue(shown.contains(anchor));
        Assertions.assertTrue(shown.contains(loose));
        Assertions.assertFalse(shown.contains(second), "a collapsed member must not get its own tile");
        Assertions.assertFalse(shown.contains(third));
    }
}
