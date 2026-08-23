package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun5.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun5.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;

import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.interfaces.InventoryBlock;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;

/**
 * Covers {@link MenuTranslationService#resolveColoredTitleFor} - the rule that a machine's GUI title is
 * always deliberately coloured: its own header item's colour (e.g. the Trash Can's item name is aqua but
 * its GUI header reads red, so the title itself must read red instead), else an explicit opt-out colour,
 * else default gray - isolated from Player/locale plumbing, like {@link MenuTranslationServiceTest}.
 * <p>
 * A {@link SlimefunItem}'s baked template name is always its raw id (the "name is always the id" rule),
 * so - matching real machines - every item here is registered id-only and its English name comes from an
 * injected {@code en/items.yml} entry; decorative/header slot items are plain {@link CustomItemStack}s
 * (never a registered {@link SlimefunItem}), exactly as production menu chrome is built.
 */
class MenuTranslationServiceHeaderColorTest {

    /** A minimal preset-backed SlimefunItem, so each test can register its own preset shape. */
    private static final class TestMachine extends SlimefunItem implements InventoryBlock {

        TestMachine(ItemGroup itemGroup, SlimefunItemStack stack, Consumer<BlockMenuPreset> setup) {
            super(itemGroup, stack, RecipeType.NULL, new ItemStack[9]);
            createPreset(this, getItemName(), setup);
        }

        @Override
        public int[] getInputSlots() {
            return new int[0];
        }

        @Override
        public int[] getOutputSlots() {
            return new int[0];
        }
    }

    private static Slimefun plugin;
    private static ItemGroup itemGroup;

    @BeforeAll
    static void load() {
        MockBukkit.mock();
        plugin = MockBukkit.load(Slimefun.class);
        itemGroup = new ItemGroup(new NamespacedKey(plugin, "menu_translation_header_color_test"), new ItemStack(Material.CHEST));
    }

    @AfterAll
    static void unload() {
        MockBukkit.unmock();
    }

    private static InputStream yaml(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    /** Registers {@code id}'s English display name, the same way a real {@code en/items.yml} entry does. */
    private static void registerEnglishName(String id, String name) {
        Slimefun.getItemTranslationService().loadTranslationsForTest("en", yaml(id + ":\n  name: '" + name + "'\n"));
    }

    @Test
    @DisplayName("a header item's colour is applied to the resolved title")
    void headerItemColorIsAppliedToTheTitle() {
        registerEnglishName("MTS_HEADER_COLOR_TEST", "&9Header Color Test");
        SlimefunItemStack stack = new SlimefunItemStack("MTS_HEADER_COLOR_TEST", Material.DISPENSER);
        new TestMachine(itemGroup, stack, preset ->
            preset.addItem(4, CustomItemStack.create(Material.LAVA_BUCKET, "&cHeader Color Test")))
            .register(plugin);

        String result = Slimefun.getMenuTranslationService()
            .resolveColoredTitleFor("en", BlockMenuPreset.getPreset("MTS_HEADER_COLOR_TEST"));

        Assertions.assertEquals(ChatColor.RED + "Header Color Test", result);
    }

    @Test
    @DisplayName("a preset with no header item and no opt-out falls back to default gray")
    void presetWithNoHeaderItemFallsBackToDefaultGray() {
        registerEnglishName("MTS_NO_HEADER_TEST", "&9No Header Test");
        SlimefunItemStack stack = new SlimefunItemStack("MTS_NO_HEADER_TEST", Material.DISPENSER);
        new TestMachine(itemGroup, stack, preset ->
            preset.addItem(0, CustomItemStack.create(Material.PAPER, " ")))
            .register(plugin);

        String result = Slimefun.getMenuTranslationService()
            .resolveColoredTitleFor("en", BlockMenuPreset.getPreset("MTS_NO_HEADER_TEST"));

        // No slot in this preset repeats the item's own name, so there is no header item to take a
        // colour from, and it never explicitly opted out either - the title must never be left
        // uncoloured (inheriting the preset's own raw id title), it must fall back to gray.
        Assertions.assertEquals(ChatColor.GRAY + "No Header Test", result);
    }

    @Test
    @DisplayName("a preset that explicitly opts out uses its declared colour, not the default gray")
    void presetThatOptsOutUsesItsDeclaredColor() {
        registerEnglishName("MTS_OPT_OUT_TEST", "&9Opt Out Test");
        SlimefunItemStack stack = new SlimefunItemStack("MTS_OPT_OUT_TEST", Material.FURNACE);
        new TestMachine(itemGroup, stack, preset -> {
            preset.optOutOfHeaderItem(ChatColor.GOLD);
            preset.addItem(0, CustomItemStack.create(Material.PAPER, " "));
        }).register(plugin);

        String result = Slimefun.getMenuTranslationService()
            .resolveColoredTitleFor("en", BlockMenuPreset.getPreset("MTS_OPT_OUT_TEST"));

        Assertions.assertEquals(ChatColor.GOLD + "Opt Out Test", result);
    }

    @Test
    @DisplayName("an explicit opt-out colour wins even if a slot coincidentally repeats the item's own name")
    void explicitOptOutTakesPrecedenceOverAnAccidentalHeaderMatch() {
        registerEnglishName("MTS_OPT_OUT_OVERRIDE_TEST", "&9Opt Out Override Test");
        SlimefunItemStack stack = new SlimefunItemStack("MTS_OPT_OUT_OVERRIDE_TEST", Material.FURNACE);
        new TestMachine(itemGroup, stack, preset -> {
            preset.optOutOfHeaderItem(ChatColor.GOLD);
            preset.addItem(4, CustomItemStack.create(Material.LAVA_BUCKET, "&cOpt Out Override Test"));
        }).register(plugin);

        String result = Slimefun.getMenuTranslationService()
            .resolveColoredTitleFor("en", BlockMenuPreset.getPreset("MTS_OPT_OUT_OVERRIDE_TEST"));

        Assertions.assertEquals(ChatColor.GOLD + "Opt Out Override Test", result,
            "a deliberate opt-out must not be overridden by the name-matching heuristic");
    }

    @Test
    @DisplayName("an explicitly declared header slot is used even without a name-matching item in it")
    void explicitHeaderSlotIsUsedWithoutNameMatching() {
        registerEnglishName("MTS_EXPLICIT_HEADER_TEST", "&9Explicit Header Test");
        SlimefunItemStack stack = new SlimefunItemStack("MTS_EXPLICIT_HEADER_TEST", Material.FURNACE);
        new TestMachine(itemGroup, stack, preset -> {
            preset.setHeaderItemSlot(4);
            preset.addItem(4, CustomItemStack.create(Material.LAVA_BUCKET, "&bUnrelated Name"));
        }).register(plugin);

        String result = Slimefun.getMenuTranslationService()
            .resolveColoredTitleFor("en", BlockMenuPreset.getPreset("MTS_EXPLICIT_HEADER_TEST"));

        Assertions.assertEquals(ChatColor.AQUA + "Explicit Header Test", result,
            "a declared header slot must be trusted directly, without needing its item's name to match the machine's own");
    }

    @Test
    @DisplayName("the deprecated createPreset overload still resolves a title (falling back to gray)")
    void deprecatedCreatePresetOverloadStillWorks() {
        registerEnglishName("MTS_DEPRECATED_PATH_TEST", "&9Deprecated Path Test");
        SlimefunItemStack stack = new SlimefunItemStack("MTS_DEPRECATED_PATH_TEST", Material.DISPENSER);

        // TestMachine's own constructor uses the deprecated 3-arg createPreset(item, title, setup) - this
        // test only makes that dependency explicit and asserts the whole pipeline still functions.
        new TestMachine(itemGroup, stack, preset ->
            preset.addItem(0, CustomItemStack.create(Material.PAPER, " ")))
            .register(plugin);

        String result = Slimefun.getMenuTranslationService()
            .resolveColoredTitleFor("en", BlockMenuPreset.getPreset("MTS_DEPRECATED_PATH_TEST"));

        Assertions.assertEquals(ChatColor.GRAY + "Deprecated Path Test", result);
    }

    @Test
    @DisplayName("a title that already carries its own colour is replaced, not doubled up, by the header colour")
    void titleWithItsOwnColorIsNotDoubled() {
        registerEnglishName("MTS_PRECOLORED_TITLE_TEST", "&9Precolored Title Test");
        SlimefunItemStack stack = new SlimefunItemStack("MTS_PRECOLORED_TITLE_TEST", Material.DISPENSER);
        new TestMachine(itemGroup, stack, preset ->
            preset.addItem(4, CustomItemStack.create(Material.LAVA_BUCKET, "&cPrecolored Title Test")))
            .register(plugin);

        MenuTranslationService service = new MenuTranslationService();
        service.load("de", yaml("MTS_PRECOLORED_TITLE_TEST:\n  title: '&9Vorgefaerbter Titel Test'\n"));

        String result = service.resolveColoredTitleFor("de", BlockMenuPreset.getPreset("MTS_PRECOLORED_TITLE_TEST"));

        Assertions.assertEquals(ChatColor.RED + "Vorgefaerbter Titel Test", result,
            "the German title's own leading &9 must be replaced by the header colour, not prefixed alongside it");
    }

    @Test
    @DisplayName("a preset that opts out without a colour takes the colour of its own item name")
    void headerlessPresetTakesTheItemNameColor() {
        registerEnglishName("MTS_ITEM_COLOR_TEST", "&2Item Color Test");
        SlimefunItemStack stack = new SlimefunItemStack("MTS_ITEM_COLOR_TEST", Material.FURNACE);
        new TestMachine(itemGroup, stack, preset -> {
            preset.optOutOfHeaderItem();
            preset.addItem(0, CustomItemStack.create(Material.PAPER, " "));
        }).register(plugin);

        String result = Slimefun.getMenuTranslationService()
            .resolveColoredTitleFor("en", BlockMenuPreset.getPreset("MTS_ITEM_COLOR_TEST"));

        Assertions.assertEquals(ChatColor.DARK_GREEN + "Item Color Test", result,
            "a menu with no header item must follow its machine's own colour rather than a hardcoded one");
    }

    @Test
    @DisplayName("opting out without a colour still falls back to gray when the item name has no colour")
    void headerlessPresetFallsBackToGrayForAnUncoloredItemName() {
        registerEnglishName("MTS_ITEM_COLORLESS_TEST", "Item Colorless Test");
        SlimefunItemStack stack = new SlimefunItemStack("MTS_ITEM_COLORLESS_TEST", Material.FURNACE);
        new TestMachine(itemGroup, stack, preset -> {
            preset.optOutOfHeaderItem();
            preset.addItem(0, CustomItemStack.create(Material.PAPER, " "));
        }).register(plugin);

        String result = Slimefun.getMenuTranslationService()
            .resolveColoredTitleFor("en", BlockMenuPreset.getPreset("MTS_ITEM_COLORLESS_TEST"));

        Assertions.assertEquals(ChatColor.GRAY + "Item Colorless Test", result);
    }

    @Test
    @DisplayName("a declared header slot outranks an opt-out colour declared by a base class")
    void declaredHeaderSlotOutranksADeclaredOptOutColor() {
        registerEnglishName("MTS_SLOT_OVER_OPT_OUT_TEST", "&9Slot Over Opt Out Test");
        SlimefunItemStack stack = new SlimefunItemStack("MTS_SLOT_OVER_OPT_OUT_TEST", Material.FURNACE);
        new TestMachine(itemGroup, stack, preset -> {
            preset.optOutOfHeaderItem(ChatColor.GOLD);
            preset.addItem(4, CustomItemStack.create(Material.LAVA_BUCKET, "&dUnrelated Name"));
            preset.setHeaderItemSlot(4);
        }).register(plugin);

        String result = Slimefun.getMenuTranslationService()
            .resolveColoredTitleFor("en", BlockMenuPreset.getPreset("MTS_SLOT_OVER_OPT_OUT_TEST"));

        Assertions.assertEquals(ChatColor.LIGHT_PURPLE + "Slot Over Opt Out Test", result,
            "a subclass declaring a real header item must win over the floor its base class opted out with");
    }
}
