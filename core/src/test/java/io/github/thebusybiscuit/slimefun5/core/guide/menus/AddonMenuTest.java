package io.github.thebusybiscuit.slimefun5.core.guide.menus;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import io.github.thebusybiscuit.slimefun5.api.items.groups.FlexItemGroup;
import io.github.thebusybiscuit.slimefun5.api.items.groups.NestedItemGroup;
import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;
import io.github.bakedlibs.dough.items.CustomItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import com.cryptomorin.xseries.XMaterial;

import io.github.thebusybiscuit.slimefun5.core.guide.widgets.GuideWidget;
import io.github.thebusybiscuit.slimefun5.core.guide.widgets.GuideWidgetRegistry;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * Covers how info widgets are routed once they can belong to an addon menu: an addon's widget must stay
 * off the guide's main menu, and must only reach the categorized layout when it declared a category.
 */
class AddonMenuTest {

    @BeforeAll
    static void load() {
        // XMaterial reads the running server version at class-init, so it needs a server even though
        // these tests only exercise registry routing.
        MockBukkit.mock();
        MockBukkit.load(Slimefun.class);
    }

    @AfterAll
    static void unload() {
        MockBukkit.unmock();
    }

    private static GuideWidget widget(String id, String addon, String category) {
        return new GuideWidget(id, id, XMaterial.PAPER, 0, GuideWidget.Position.BOTTOM,
            (player, profile) -> { /* never opened in this test */ }, addon, category);
    }

    @Test
    @DisplayName("A widget with no addon belongs to the guide's own main menu")
    void mainMenuWidgetsAreTheUnboundOnes() {
        GuideWidgetRegistry registry = new GuideWidgetRegistry();
        registry.register(widget("advancements", null, null));
        registry.register(widget("tinker_traits", "SlimeTinker", "tools"));

        Assertions.assertEquals(1, registry.getForMainMenu().size());
        Assertions.assertEquals("advancements", registry.getForMainMenu().get(0).getId());
    }

    @Test
    @DisplayName("An addon's widgets are found by that addon, and not by another")
    void addonWidgetsAreScopedToTheirAddon() {
        GuideWidgetRegistry registry = new GuideWidgetRegistry();
        registry.register(widget("tinker_traits", "SlimeTinker", "tools"));
        registry.register(widget("networks_throughput", "Networks", "machines"));

        Assertions.assertEquals(1, registry.getForAddon("SlimeTinker").size());
        Assertions.assertEquals("tinker_traits", registry.getForAddon("SlimeTinker").get(0).getId());
        Assertions.assertTrue(registry.getForAddon("ExoticGarden").isEmpty());
    }

    @Test
    @DisplayName("An addon widget without a category never reaches the categorized layout")
    void uncategorizedAddonWidgetIsNotShownInCategories() {
        GuideWidgetRegistry registry = new GuideWidgetRegistry();
        registry.register(widget("no_category", "SlimeTinker", null));

        Assertions.assertEquals(1, registry.getForAddon("SlimeTinker").size(), "it still belongs to the addon menu");

        for (String category : new String[] { "tools", "machines", "misc", "resources" }) {
            Assertions.assertTrue(registry.getForCategory(category).isEmpty(),
                "an addon widget with no declared category must not appear under " + category);
        }
    }

    @Test
    @DisplayName("A main-menu widget is not duplicated into a category")
    void mainMenuWidgetIsNotAlsoACategoryWidget() {
        GuideWidgetRegistry registry = new GuideWidgetRegistry();
        registry.register(widget("advancements", null, "misc"));

        Assertions.assertTrue(registry.getForCategory("misc").isEmpty(),
            "a main-menu widget already has its own slot in both layouts");
    }

    @Test
    @DisplayName("Category widgets are grouped by the category they declared")
    void categoryWidgetsResolveByCategory() {
        GuideWidgetRegistry registry = new GuideWidgetRegistry();
        registry.register(widget("tinker_traits", "SlimeTinker", "tools"));
        registry.register(widget("tinker_parts", "SlimeTinker", "resources"));

        Assertions.assertEquals(1, registry.getForCategory("tools").size());
        Assertions.assertEquals("tinker_traits", registry.getForCategory("tools").get(0).getId());
        Assertions.assertEquals("tinker_parts", registry.getForCategory("resources").get(0).getId());
    }

    @Test
    @DisplayName("A built menu is recorded, so a declared root still takes precedence")
    void builtMenusAreRecorded() {
        AddonMenuRegistry registry = new AddonMenuRegistry();

        Assertions.assertFalse(registry.hasDeclaredRoot("SlimeTinker"));

        // Recording is silent: folding is the standard path, so it is state rather than a boot warning.
        registry.recordAutoWrapped("SlimeTinker", 11);
        registry.recordAutoWrapped("Networks", 4);

        Assertions.assertEquals(2, registry.getAutoWrappedAddons().size());
        Assertions.assertNull(registry.getDeclaredRoot("SlimeTinker"));
    }

    @Test
    @DisplayName("An addon menu opens like a category, not as a deprecated custom screen")
    void addonMenuIsAnOpenableGuideScreen() {
        // The guide only lets CategoryItemGroup, NestedItemGroup and AddonItemGroup open; every other
        // FlexItemGroup is treated as a deprecated addon UI and bounced back to the main menu. Missing
        // this made every folded addon's main-menu tile a dead button.
        AddonItemGroup menu = new AddonItemGroup(
            new NamespacedKey("testaddon", "guide_menu"), new ItemStack(Material.CHEST), "TestAddon");

        Assertions.assertTrue(FlexItemGroup.class.isAssignableFrom(menu.getClass()),
            "a FlexItemGroup is bounced unless the guide names its type explicitly");
        Assertions.assertFalse(NestedItemGroup.class.isAssignableFrom(menu.getClass()),
            "AddonItemGroup must be handled on its own, not by passing for a NestedItemGroup");
    }

    @Test
    @DisplayName("An addon menu is labelled after its addon, not after the icon it borrowed")
    void addonMenuNameFollowsTheConvention() {
        // A generated menu inherits its icon from the addon's first group, and that icon carries that
        // group's name - which is how the FluffyMachines entry ended up reading "Generators".
        ItemStack borrowed = CustomItemStack.create(new ItemStack(Material.CHEST), "&aGenerators");
        AddonItemGroup menu = new AddonItemGroup(
            new NamespacedKey("fluffymachines", "guide_menu"), borrowed, "FluffyMachines");

        Assertions.assertEquals("FluffyMachines", menu.getAddonName());
        Assertions.assertEquals(Material.CHEST, borrowed.getType(), "the icon material is still inherited");
    }
}
