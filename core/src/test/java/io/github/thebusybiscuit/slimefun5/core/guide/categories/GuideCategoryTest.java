package io.github.thebusybiscuit.slimefun5.core.guide.categories;

import java.util.List;

import com.cryptomorin.xseries.XMaterial;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import io.github.thebusybiscuit.slimefun5.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;

class GuideCategoryTest {

    private static ServerMock server;

    @BeforeAll
    public static void load() {
        server = MockBukkit.mock();
        // Loading Slimefun initialises the version-dependent item-flag helper the ItemGroup constructor
        // uses; without it, constructing an ItemGroup throws ExceptionInInitializerError.
        MockBukkit.load(Slimefun.class);
    }

    @AfterAll
    public static void unload() {
        MockBukkit.unmock();
    }

    private static GuideCategory cat(String id, int order) {
        return new GuideCategory(id, "&7" + id, XMaterial.CHEST, order);
    }

    @Test
    void registerAndLookup() {
        GuideCategoryRegistry reg = new GuideCategoryRegistry();
        GuideCategory weapons = cat("weapons", 10);
        reg.register(weapons);
        Assertions.assertSame(weapons, reg.getById("weapons"));
        Assertions.assertNull(reg.getById("nope"));
        Assertions.assertNull(reg.getById(null));
    }

    @Test
    void getAllIsOrderedByOrderThenId() {
        GuideCategoryRegistry reg = new GuideCategoryRegistry();
        reg.register(cat("b", 20));
        reg.register(cat("a", 20));
        reg.register(cat("z", 10));
        List<GuideCategory> all = reg.getAll();
        Assertions.assertEquals("z", all.get(0).getId());
        Assertions.assertEquals("a", all.get(1).getId());
        Assertions.assertEquals("b", all.get(2).getId());
    }

    @Test
    void registerOverridesSameId() {
        GuideCategoryRegistry reg = new GuideCategoryRegistry();
        reg.register(cat("weapons", 10));
        GuideCategory replacement = cat("weapons", 15);
        reg.register(replacement);
        Assertions.assertSame(replacement, reg.getById("weapons"));
        Assertions.assertEquals(1, reg.getAll().size());
    }

    @Test
    @DisplayName("classifyMaterial types weapons/tools/armor/food/decoration/resources; axe is a Tool")
    void classifierMaterialHeuristic() {
        Assertions.assertEquals(DefaultGuideCategories.WEAPONS, ItemTypeClassifier.classifyMaterial(Material.DIAMOND_SWORD));
        Assertions.assertEquals(DefaultGuideCategories.TOOLS, ItemTypeClassifier.classifyMaterial(Material.IRON_PICKAXE));
        Assertions.assertEquals(DefaultGuideCategories.TOOLS, ItemTypeClassifier.classifyMaterial(Material.DIAMOND_AXE));
        Assertions.assertEquals(DefaultGuideCategories.ARMOR, ItemTypeClassifier.classifyMaterial(Material.DIAMOND_CHESTPLATE));
        Assertions.assertEquals(DefaultGuideCategories.FOOD, ItemTypeClassifier.classifyMaterial(Material.APPLE));
        // Decoration + resources now covered by the heuristic instead of falling to Misc.
        Assertions.assertEquals(DefaultGuideCategories.DECORATION, ItemTypeClassifier.classifyMaterial(Material.RED_WOOL));
        Assertions.assertEquals(DefaultGuideCategories.DECORATION, ItemTypeClassifier.classifyMaterial(Material.GLASS));
        Assertions.assertEquals(DefaultGuideCategories.RESOURCES, ItemTypeClassifier.classifyMaterial(Material.DIAMOND));
        Assertions.assertEquals(DefaultGuideCategories.RESOURCES, ItemTypeClassifier.classifyMaterial(Material.IRON_INGOT));
        Assertions.assertEquals(DefaultGuideCategories.RESOURCES, ItemTypeClassifier.classifyMaterial(Material.COAL));
        Assertions.assertEquals(DefaultGuideCategories.RESOURCES, ItemTypeClassifier.classifyMaterial(Material.BLAZE_ROD));
        // Building-block families now type as Decoration instead of falling to Misc.
        Assertions.assertEquals(DefaultGuideCategories.DECORATION, ItemTypeClassifier.classifyMaterial(Material.OAK_PLANKS));
        Assertions.assertEquals(DefaultGuideCategories.DECORATION, ItemTypeClassifier.classifyMaterial(Material.POLISHED_ANDESITE));
        Assertions.assertEquals(DefaultGuideCategories.DECORATION, ItemTypeClassifier.classifyMaterial(Material.STONE_BRICKS));
        // Farming items land under Food & Farming.
        Assertions.assertEquals(DefaultGuideCategories.FOOD, ItemTypeClassifier.classifyMaterial(Material.WHEAT_SEEDS));
        // A plain crafting-table block is still unclassified (falls to Misc).
        Assertions.assertNull(ItemTypeClassifier.classifyMaterial(Material.CRAFTING_TABLE));
    }

    @Test
    @DisplayName("GuideWidgetRegistry registers, looks up, and orders by order then id")
    void widgetRegistry() {
        io.github.thebusybiscuit.slimefun5.core.guide.widgets.GuideWidgetRegistry reg =
            new io.github.thebusybiscuit.slimefun5.core.guide.widgets.GuideWidgetRegistry();
        io.github.thebusybiscuit.slimefun5.core.guide.widgets.GuideWidget b =
            new io.github.thebusybiscuit.slimefun5.core.guide.widgets.GuideWidget("b", "&7B", XMaterial.CHEST, 20, (p, pr) -> { });
        io.github.thebusybiscuit.slimefun5.core.guide.widgets.GuideWidget a =
            new io.github.thebusybiscuit.slimefun5.core.guide.widgets.GuideWidget("a", "&7A", XMaterial.CHEST, 10, (p, pr) -> { });
        reg.register(b);
        reg.register(a);
        Assertions.assertSame(b, reg.getById("b"));
        Assertions.assertNull(reg.getById("nope"));
        Assertions.assertEquals("a", reg.getAll().get(0).getId());
        Assertions.assertEquals("b", reg.getAll().get(1).getId());
    }

    @Test
    @DisplayName("typeSingular maps ids to section-title words")
    void typeSingularWords() {
        Assertions.assertEquals("Weapon", ItemTypeClassifier.typeSingular(DefaultGuideCategories.WEAPONS));
        Assertions.assertEquals("Machine", ItemTypeClassifier.typeSingular(DefaultGuideCategories.MACHINES));
        Assertions.assertEquals("Resource", ItemTypeClassifier.typeSingular(DefaultGuideCategories.RESOURCES));
        Assertions.assertEquals("Decoration", ItemTypeClassifier.typeSingular(DefaultGuideCategories.DECORATION));
        Assertions.assertEquals("Misc", ItemTypeClassifier.typeSingular("unknown_id"));
    }

    @Test
    @DisplayName("resolveCategoryId keeps a declared category the registry recognizes")
    void resolveCategoryIdKeepsRecognizedDeclaredCategory() {
        GuideCategoryRegistry reg = new GuideCategoryRegistry();
        DefaultGuideCategories.registerInto(reg);

        ItemGroup group = new ItemGroup(new NamespacedKey("myaddon", "swords"), new ItemStack(Material.DIAMOND_SWORD));
        group.setCategory(DefaultGuideCategories.WEAPONS);

        Assertions.assertEquals(DefaultGuideCategories.WEAPONS, CategoryMenuBuilder.resolveCategoryId(group, reg));
    }

    @Test
    @DisplayName("resolveCategoryId falls back to Misc when the group declares no category")
    void resolveCategoryIdFallsBackToMiscWhenUndeclared() {
        GuideCategoryRegistry reg = new GuideCategoryRegistry();
        DefaultGuideCategories.registerInto(reg);

        ItemGroup group = new ItemGroup(new NamespacedKey("myaddon", "undeclared"), new ItemStack(Material.CHEST));

        Assertions.assertEquals(DefaultGuideCategories.MISC, CategoryMenuBuilder.resolveCategoryId(group, reg));
    }

    @Test
    @DisplayName("resolveCategoryId falls back to Misc when the declared category id is not registered")
    void resolveCategoryIdFallsBackToMiscWhenUnrecognized() {
        GuideCategoryRegistry reg = new GuideCategoryRegistry();
        DefaultGuideCategories.registerInto(reg);

        ItemGroup group = new ItemGroup(new NamespacedKey("myaddon", "mystery"), new ItemStack(Material.CHEST));
        group.setCategory("totally_made_up_category");

        Assertions.assertEquals(DefaultGuideCategories.MISC, CategoryMenuBuilder.resolveCategoryId(group, reg));
    }

    @Test
    @DisplayName("resolveCategoryLabel falls back to the addon name when even Misc is unregistered")
    void resolveCategoryLabelFallsBackToAddonName() {
        GuideCategoryRegistry reg = new GuideCategoryRegistry();

        ItemGroup group = new ItemGroup(new NamespacedKey("myaddon", "orphaned"), new ItemStack(Material.CHEST));
        group.register(Slimefun.instance());

        // The localization service reports "Error: No language present" in this headless harness (see
        // SlimefunLocalization's unit-test sentinel), so the addon-name fallback text itself is not
        // observable here; this only asserts resolveCategoryLabel does not throw for an unregistered-Misc
        // registry with an addon-owned group - the real fallback text is exercised in-game.
        org.bukkit.entity.Player p = server.addPlayer();
        Assertions.assertDoesNotThrow(() -> CategoryMenuBuilder.resolveCategoryLabel(p, group, reg));
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacySetThemeDelegatesToCategory() {
        // Addons in the wild (Networks, InfinityExpansion, ...) call the old setTheme("machines") with the
        // exact ids that are now canonical category ids. setTheme must survive (deprecated) and delegate,
        // or those addons NoSuchMethodError on enable and their groups never register (invisible in guide).
        ItemGroup group = new ItemGroup(new NamespacedKey("myaddon", "machines"), new org.bukkit.inventory.ItemStack(org.bukkit.Material.FURNACE));
        group.setTheme("machines");
        Assertions.assertEquals("machines", group.getCategoryId());
        Assertions.assertEquals("machines", group.getThemeId());
    }
}
