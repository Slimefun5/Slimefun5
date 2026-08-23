package io.github.thebusybiscuit.slimefun5.core.guide.menus;

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
    @DisplayName("An addon is warned only once about its menu being built for it")
    void autoWrapWarningIsOncePerAddon() {
        AddonMenuRegistry registry = new AddonMenuRegistry();

        Assertions.assertFalse(registry.hasDeclaredRoot("SlimeTinker"));

        // Warning twice must not throw or double-register; the once-only guard lives in the registry.
        registry.warnAutoWrapped("SlimeTinker", 12);
        registry.warnAutoWrapped("SlimeTinker", 12);

        Assertions.assertNull(registry.getDeclaredRoot("SlimeTinker"));
    }
}
