package io.github.thebusybiscuit.slimefun5.implementation.guide;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.HandCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import org.apache.commons.lang.Validate;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import com.cryptomorin.xseries.XMaterial;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.RecipeChoice.MaterialChoice;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.bakedlibs.dough.chat.ChatInput;
import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.bakedlibs.dough.recipes.MinecraftRecipe;
import io.github.thebusybiscuit.slimefun5.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun5.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.api.items.groups.FlexItemGroup;
import io.github.thebusybiscuit.slimefun5.api.items.groups.NestedItemGroup;
import io.github.thebusybiscuit.slimefun5.api.items.groups.LockedItemGroup;
import io.github.thebusybiscuit.slimefun5.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun5.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun5.api.researches.Research;
import io.github.thebusybiscuit.slimefun5.core.attributes.RecipeDisplayItem;
import io.github.thebusybiscuit.slimefun5.core.guide.menus.AddonItemGroup;
import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;
import io.github.thebusybiscuit.slimefun5.core.guide.AddonVisibility;
import io.github.thebusybiscuit.slimefun5.core.guide.GuideHistory;
import io.github.thebusybiscuit.slimefun5.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun5.core.guide.SlimefunGuideImplementation;
import io.github.thebusybiscuit.slimefun5.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun5.core.guide.options.AddonVisibilityMenu;
import io.github.thebusybiscuit.slimefun5.core.guide.options.SlimefunGuideSettings;
import io.github.thebusybiscuit.slimefun5.core.guide.categories.CategoryItemGroup;
import io.github.thebusybiscuit.slimefun5.core.guide.categories.CategoryMenuBuilder;
import io.github.thebusybiscuit.slimefun5.core.multiblocks.MultiBlock;
import io.github.thebusybiscuit.slimefun5.core.multiblocks.MultiBlockMachine;
import io.github.thebusybiscuit.slimefun5.core.services.localization.ItemTranslationService;
import io.github.thebusybiscuit.slimefun5.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.core.guide.variants.VariantDisplayMarker;
import io.github.thebusybiscuit.slimefun5.core.guide.variants.VariantGroup;
import io.github.thebusybiscuit.slimefun5.implementation.tasks.AsyncRecipeChoiceTask;
import io.github.thebusybiscuit.slimefun5.implementation.tasks.AsyncVariantDisplayTask;
import io.github.thebusybiscuit.slimefun5.utils.ChatUtils;
import io.github.thebusybiscuit.slimefun5.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.MaterialCompat;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.VersionedItemFlag;
import io.github.thebusybiscuit.slimefun5.utils.itemstack.SlimefunGuideItem;

import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu.MenuClickHandler;

/**
 * The {@link SurvivalSlimefunGuide} is the standard version of our {@link SlimefunGuide}.
 * It uses an {@link Inventory} to display {@link SlimefunGuide} contents.
 *
 * @author TheBusyBiscuit
 *
 * @see SlimefunGuide
 * @see SlimefunGuideImplementation
 * @see CheatSheetSlimefunGuide
 *
 */
public class SurvivalSlimefunGuide implements SlimefunGuideImplementation {

    private static final int MAX_ITEM_GROUPS = 36;

    private final int[] recipeSlots = { 3, 4, 5, 12, 13, 14, 21, 22, 23 };
    // Built lazily: the guide is constructed during startup before the localization service exists, so the
    // localized name/lore can only be resolved on first access (by which time a player can request it).
    private ItemStack item;
    private final boolean showVanillaRecipes;
    private final boolean showHiddenItemGroupsInSearch;

    public SurvivalSlimefunGuide(boolean showVanillaRecipes, boolean showHiddenItemGroupsInSearch) {
        this.showVanillaRecipes = showVanillaRecipes;
        this.showHiddenItemGroupsInSearch = showHiddenItemGroupsInSearch;
    }

    @Override
    public @Nonnull SlimefunGuideMode getMode() {
        return SlimefunGuideMode.SURVIVAL_MODE;
    }

    @Override
    public @Nonnull ItemStack getItem() {
        if (item == null && Slimefun.getLocalization() != null) {
            item = new SlimefunGuideItem(this, Slimefun.getLocalization().getMessage("guide.item.name"));
        }

        // Localization not ready yet (very early access): a transient English copy, not cached.
        return item != null ? item : new SlimefunGuideItem(this, "&aSlimefun Guide &7(Chest GUI)");
    }

    protected final boolean isSurvivalMode() {
        return getMode() != SlimefunGuideMode.CHEAT_MODE;
    }

    /**
     * Returns a {@link List} of visible {@link ItemGroup} instances that the {@link SlimefunGuide} would display.
     *
     * @param p
     *            The {@link Player} who opened his {@link SlimefunGuide}
     * @param profile
     *            The {@link PlayerProfile} of the {@link Player}
     *
     * @return a {@link List} of visible {@link ItemGroup} instances
     */
    protected @Nonnull List<ItemGroup> getVisibleItemGroups(@Nonnull Player p, @Nonnull PlayerProfile profile) {
        List<ItemGroup> tiles = buildMainMenuTiles(p, profile);

        if (tiles.isEmpty()) {
            // The player's addon-visibility set hid everything (a corrupt/stale set that hides all content -
            // unreachable via the menu, which keeps at least one shown). The guide must never be blank
            // because of visibility, so re-render once with filtering disabled, then repair the set.
            List<ItemGroup> fallback = new ArrayList<>();
            AddonVisibility.runWithoutFiltering(() -> fallback.addAll(buildMainMenuTiles(p, profile)));

            if (!fallback.isEmpty()) {
                AddonVisibility.clear(p);
                Slimefun.logger().log(Level.WARNING, "Addon visibility hid all guide content for {0}; reset it and showing everything.", p.getName());
                return fallback;
            }
        }

        return tiles;
    }

    /**
     * The main-menu tiles for the current layout. Classic mirrors the old upstream main menu: the raw
     * {@link ItemGroup} tiles (Slimefun's own groups plus every addon's own groups), honoring addon
     * visibility. Categorized uses the shared-category system, where each category opens into
     * {@code <Addon> <Type>} sections.
     */
    @Nonnull
    private List<ItemGroup> buildMainMenuTiles(@Nonnull Player p, @Nonnull PlayerProfile profile) {
        List<ItemGroup> visible = collectVisibleCategories(p, profile);

        boolean categorized;
        try {
            categorized = SlimefunGuideSettings.isMainMenuCategorized(p);
        } catch (Exception | LinkageError x) {
            // Reading the per-player layout option can fail if the guide/localization isn't fully ready;
            // default to the richer categorized view rather than let the menu fail to build.
            categorized = true;
        }

        if (!categorized) {
            return foldAddonMenus(p, visible);
        }

        return CategoryMenuBuilder.build(p, visible, Slimefun.getGuideCategories());
    }

    /**
     * Collapses each addon's top-level groups into the single menu it gets in the classic layout: the root
     * it declared, or one built around its groups. Slimefun's own groups are left alone, and an addon that
     * already registers exactly one top-level group keeps it as-is rather than gaining a menu that holds
     * one tile.
     *
     * @param visible
     *            The visible groups, in registration order
     *
     * @return The main-menu tiles, preserving the order each addon first appears in
     */
    @Nonnull
    private List<ItemGroup> foldAddonMenus(@Nonnull Player p, @Nonnull List<ItemGroup> visible) {
        Map<String, List<ItemGroup>> byAddon = new LinkedHashMap<>();
        List<ItemGroup> tiles = new ArrayList<>();

        for (ItemGroup group : visible) {
            String addon = group.getAddon() != null ? group.getAddon().getName() : null;

            if (addon == null || "slimefun".equals(group.getKey().getNamespace())) {
                tiles.add(group);
                continue;
            }

            // A declared root is itself a tile; its members are reached through it, not from the main menu.
            if (group instanceof AddonItemGroup) {
                continue;
            }

            byAddon.computeIfAbsent(addon, k -> new ArrayList<>()).add(group);
        }

        for (Map.Entry<String, List<ItemGroup>> entry : byAddon.entrySet()) {
            tiles.add(menuFor(p, entry.getKey(), entry.getValue()));
        }

        return tiles;
    }

    @Nonnull
    private ItemGroup menuFor(@Nonnull Player p, @Nonnull String addon, @Nonnull List<ItemGroup> groups) {
        AddonItemGroup declared = Slimefun.getAddonMenus().getDeclaredRoot(addon);

        if (declared != null) {
            for (ItemGroup group : groups) {
                if (!declared.getMembers().contains(group)) {
                    declared.addMember(group);
                }
            }

            return declared;
        }

        if (groups.size() == 1) {
            return groups.get(0);
        }

        Slimefun.getAddonMenus().warnAutoWrapped(addon, groups.size());

        AddonItemGroup generated = new AddonItemGroup(
            new NamespacedKey(addon.toLowerCase(Locale.ROOT), "guide_menu"),
            groups.get(0).getItem(p),
            addon);

        for (ItemGroup group : groups) {
            generated.addMember(group);
        }

        Slimefun.getAddonMenus().declareRoot(generated);
        return generated;
    }

    protected @Nonnull List<ItemGroup> collectVisibleCategories(@Nonnull Player p, @Nonnull PlayerProfile profile) {
        List<ItemGroup> groups = new LinkedList<>();

        for (ItemGroup group : Slimefun.getRegistry().getAllItemGroups()) {
            if (group instanceof CategoryItemGroup) {
                continue;
            }

            String namespace = group.getKey().getNamespace();

            // Respect the player's per-addon visibility in EVERY layout. Previously only the categorized
            // path filtered hidden addons, so switching the guide to the classic (flat) layout made hidden
            // addons reappear.
            if (AddonVisibility.isHidden(p, namespace)) {
                continue;
            }

            // Deprecated addon custom guide screens are taken OUT of the guide entirely - not shown, not
            // interactive (rendering them let cheat-mode players pull infinite free items). Their items
            // still appear via the shared categories. Standard NestedItemGroups (plain nested browsing) and
            // core's own flex groups (e.g. seasonal) are kept.
            if (group instanceof FlexItemGroup && !(group instanceof NestedItemGroup) && !"slimefun".equals(namespace)) {
                warnDeprecatedCustomGuideUi(group);
                continue;
            }

            try {
                if (group instanceof FlexItemGroup) {
                    FlexItemGroup flexItemGroup = (FlexItemGroup) group;
                    if (flexItemGroup.isVisible(p, profile, getMode())) {
                        groups.add(group);
                    }
                } else if (!group.isHidden(p)) {
                    groups.add(group);
                }
            } catch (Exception | LinkageError x) {
                SlimefunAddon addon = group.getAddon();

                if (addon != null) {
                    addon.getLogger().log(Level.SEVERE, x, () -> "Could not display item group: " + group);
                } else {
                    Slimefun.logger().log(Level.SEVERE, x, () -> "Could not display item group: " + group);
                }
            }
        }

        return groups;
    }

    @Override
    public void openMainMenu(PlayerProfile profile, int page) {
        Player p = profile.getPlayer();

        if (p == null) {
            return;
        }

        if (isSurvivalMode()) {
            GuideHistory history = profile.getGuideHistory();
            history.clear();
            history.setMainMenuPage(page);
        }

        ChestMenu menu = create(p);

        List<ItemGroup> itemGroups;
        try {
            itemGroups = getVisibleItemGroups(p, profile);
        } catch (Exception | LinkageError x) {
            // A failure while building the menu must never leave the player with an unopened/blank guide.
            itemGroups = new ArrayList<>();
            Slimefun.logger().log(Level.SEVERE, x, () -> "Failed to build the guide main menu for " + p.getName());
        }

        if (itemGroups.isEmpty()) {
            // Surface the reason rather than silently showing an empty guide - the counts pinpoint which
            // link (categories registered / groups visible / items loaded) is broken on this server.
            int cats = Slimefun.getGuideCategories().getAll().size();
            int enabled = Slimefun.getRegistry().getEnabledSlimefunItems().size();
            String breakdown;
            try {
                breakdown = io.github.thebusybiscuit.slimefun5.core.guide.categories.CategoryMenuBuilder
                    .diagnose(p, collectVisibleCategories(p, profile), Slimefun.getGuideCategories());
            } catch (Exception | LinkageError x) {
                breakdown = "diagnose failed: " + x;
            }
            Slimefun.logger().log(Level.WARNING, "Guide main menu is empty: registeredCategories={0}, enabledItems={1}. {2}",
                new Object[] { cats, enabled, breakdown });
        }

        int index = 9;
        createHeader(p, profile, menu);

        // Addon Visibility entry (main menu only).
        List<String> addonVisibilityLore = new ArrayList<>();
        addonVisibilityLore.add(Slimefun.getLocalization().getMessage(p, "guide.addon-visibility.name"));
        addonVisibilityLore.add("");
        addonVisibilityLore.addAll(Slimefun.getLocalization().getMessages(p, "guide.addon-visibility.lore"));
        menu.addItem(4, CustomItemStack.create(XMaterial.BOOKSHELF.parseMaterial(), addonVisibilityLore));
        menu.addMenuClickHandler(4, (pl, slot, item, action) -> {
            AddonVisibilityMenu.open(pl, this.item);
            return false;
        });

        int target = (MAX_ITEM_GROUPS * (page - 1)) - 1;

        while (target < (itemGroups.size() - 1) && index < MAX_ITEM_GROUPS + 9) {
            target++;

            ItemGroup group = itemGroups.get(target);
            showItemGroup(menu, p, profile, group, index);

            index++;
        }

        int pages = target == itemGroups.size() - 1 ? page : (itemGroups.size() - 1) / MAX_ITEM_GROUPS + 1;

        menu.addItem(46, ChestMenuUtils.getPreviousButton(p, page, pages));
        menu.addMenuClickHandler(46, (pl, slot, item, action) -> {
            int next = page - 1;

            if (next != page && next > 0) {
                openMainMenu(profile, next);
            }

            return false;
        });

        menu.addItem(52, ChestMenuUtils.getNextButton(p, page, pages));
        menu.addMenuClickHandler(52, (pl, slot, item, action) -> {
            int next = page + 1;

            if (next != page && next <= pages) {
                openMainMenu(profile, next);
            }

            return false;
        });

        // Functional addon widgets (e.g. an advancement tree) get a dedicated button, top or bottom row per
        // the widget's own preference, shown on every page and on both layouts.
        placeWidgets(menu, p, profile);

        menu.open(p);
    }

    // Widget buttons are centered in the bottom row; once it's full (>5) the extras spill into the free
    // header slots (1 = settings, 4 = addon-visibility, 7 = search are taken), also centered.
    private static final int[] BOTTOM_WIDGET_SLOTS = { 47, 48, 49, 50, 51 };
    private static final int[] TOP_WIDGET_SLOTS = { 0, 2, 3, 5, 6, 8 };

    private void placeWidgets(@Nonnull ChestMenu menu, @Nonnull Player p, @Nonnull PlayerProfile profile) {
        // Only the guide's own widgets: an addon's belong on its menu (classic) or in its declared
        // category (categorized), not on the main menu.
        placeWidgets(menu, p, profile, Slimefun.getGuideWidgets().getForMainMenu());
    }

    @ParametersAreNonnullByDefault
    private void placeWidgets(ChestMenu menu, Player p, PlayerProfile profile, List<io.github.thebusybiscuit.slimefun5.core.guide.widgets.GuideWidget> widgets) {
        if (widgets.isEmpty()) {
            return;
        }

        int bottomCount = Math.min(widgets.size(), BOTTOM_WIDGET_SLOTS.length);
        int topCount = Math.min(widgets.size() - bottomCount, TOP_WIDGET_SLOTS.length);
        int[] slots = new int[bottomCount + topCount];
        System.arraycopy(centeredSlots(BOTTOM_WIDGET_SLOTS, bottomCount), 0, slots, 0, bottomCount);
        System.arraycopy(centeredSlots(TOP_WIDGET_SLOTS, topCount), 0, slots, bottomCount, topCount);

        for (int i = 0; i < slots.length; i++) {
            io.github.thebusybiscuit.slimefun5.core.guide.widgets.GuideWidget widget = widgets.get(i);
            menu.replaceExistingItem(slots[i], widgetTile(p, widget));
            menu.addMenuClickHandler(slots[i], (pl, s, item, action) -> {
                widget.open(pl, profile);
                return false;
            });
        }

        if (slots.length < widgets.size()) {
            Slimefun.logger().warning("[Guide] " + (widgets.size() - slots.length) + " guide widget(s) beyond the "
                + slots.length + " available button slots are not shown.");
        }
    }

    /** The {@code count} middle slots of {@code available}, so buttons sit centered rather than left-aligned. */
    @Nonnull
    private static int[] centeredSlots(@Nonnull int[] available, int count) {
        int n = Math.max(0, Math.min(count, available.length));
        int start = (available.length - n) / 2;
        int[] result = new int[n];
        System.arraycopy(available, start, result, 0, n);
        return result;
    }

    @Nonnull
    private ItemStack widgetTile(@Nonnull Player p, @Nonnull io.github.thebusybiscuit.slimefun5.core.guide.widgets.GuideWidget widget) {
        return CustomItemStack.create(MaterialCompat.stack(widget.getIcon()),
            ChatColor.translateAlternateColorCodes('&', widget.getDefaultName()),
            "",
            Slimefun.getLocalization().getMessage(p, "guide.categories-meta.open"));
    }

    /**
     * Opens the contents of a single category: a paginated grid of its member groups, with a back button
     * to the main menu. A category with a single member opens that member directly.
     */
    public void openCategoryContents(@Nonnull PlayerProfile profile, @Nonnull CategoryItemGroup categoryGroup, int page) {
        Player p = profile.getPlayer();

        if (p == null) {
            return;
        }

        List<ItemGroup> categories = categoryGroup.getMembers();

        if (categories.size() == 1) {
            openItemGroup(profile, categories.get(0), 1);
            return;
        }

        if (isSurvivalMode()) {
            profile.getGuideHistory().add(categoryGroup, page);
        }

        ChestMenu menu = create(p);
        createHeader(p, profile, menu);
        addBackButton(menu, 1, p, profile);

        int index = 9;
        int target = (MAX_ITEM_GROUPS * (page - 1)) - 1;

        while (target < (categories.size() - 1) && index < MAX_ITEM_GROUPS + 9) {
            target++;
            showItemGroup(menu, p, profile, categories.get(target), index);
            index++;
        }

        int pages = target == categories.size() - 1 ? page : (categories.size() - 1) / MAX_ITEM_GROUPS + 1;

        menu.addItem(46, ChestMenuUtils.getPreviousButton(p, page, pages));
        menu.addMenuClickHandler(46, (pl, slot, item, action) -> {
            int next = page - 1;

            if (next != page && next > 0) {
                openCategoryContents(profile, categoryGroup, next);
            }

            return false;
        });

        menu.addItem(52, ChestMenuUtils.getNextButton(p, page, pages));
        menu.addMenuClickHandler(52, (pl, slot, item, action) -> {
            int next = page + 1;

            if (next != page && next <= pages) {
                openCategoryContents(profile, categoryGroup, next);
            }

            return false;
        });

        // Addon info widgets that declared this category. Without a category an addon widget has no home
        // in this layout, which is exactly why declaring one is required to appear here.
        placeWidgets(menu, p, profile, Slimefun.getGuideWidgets().getForCategory(categoryGroup.getCategory().getId()));

        menu.open(p);
    }

    /**
     * The classic-layout view of a category: a flat, paginated grid of ALL its items (core + every addon),
     * with no per-source sub-sections and a back button to the main menu.
     */
    public void openCategoryItemsFlat(@Nonnull PlayerProfile profile, @Nonnull CategoryItemGroup categoryGroup, int page) {
        Player p = profile.getPlayer();

        if (p == null) {
            return;
        }

        List<SlimefunItem> items = collapseForDisplay(p, categoryGroup.getAllItems());

        if (isSurvivalMode()) {
            profile.getGuideHistory().add(categoryGroup, page);
        }

        ChestMenu menu = create(p);
        createHeader(p, profile, menu);
        addBackButton(menu, 1, p, profile);

        int pages = Math.max(1, (items.size() - 1) / MAX_ITEM_GROUPS + 1);

        int index = 9;
        int itemIndex = MAX_ITEM_GROUPS * (page - 1);
        AsyncVariantDisplayTask variantTask = new AsyncVariantDisplayTask();

        for (int i = 0; i < MAX_ITEM_GROUPS; i++) {
            int target = itemIndex + i;

            if (target >= items.size()) {
                break;
            }

            SlimefunItem sfitem = items.get(target);
            displaySlimefunItem(menu, categoryGroup, p, profile, sfitem, page, index);

            VariantGroup group = Slimefun.getVariantGroups().getGroup(sfitem.getId());

            if (group != null) {
                variantTask.add(index, variantDisplayStacks(group));
            }

            index++;
        }

        menu.addItem(46, ChestMenuUtils.getPreviousButton(p, page, pages));
        menu.addMenuClickHandler(46, (pl, slot, item, action) -> {
            int next = page - 1;

            if (next != page && next > 0) {
                openCategoryItemsFlat(profile, categoryGroup, next);
            }

            return false;
        });

        menu.addItem(52, ChestMenuUtils.getNextButton(p, page, pages));
        menu.addMenuClickHandler(52, (pl, slot, item, action) -> {
            int next = page + 1;

            if (next != page && next <= pages) {
                openCategoryItemsFlat(profile, categoryGroup, next);
            }

            return false;
        });

        menu.open(p);

        if (!variantTask.isEmpty()) {
            variantTask.start(menu.toInventory());
        }
    }

    /** One consistent colour for every real category tile, so addons (which colour/prefix their group
     *  names inconsistently) don't make the guide's categories look mismatched. */
    private static final String UNIFIED_GROUP_COLOR = ChatColor.YELLOW.toString();

    /**
     * The category tile for a group, with its display name normalised to {@link #UNIFIED_GROUP_COLOR}.
     * Category tiles ({@link CategoryItemGroup}) are core-defined and intentionally colour-coded per
     * category, so they are left untouched; every other group (core or addon) is unified.
     */
    @Nonnull
    private ItemStack unifiedGroupTile(@Nonnull Player p, @Nonnull ItemGroup group) {
        ItemStack tile = group.getItem(p);

        if (group instanceof CategoryItemGroup) {
            return tile;
        }

        ItemStack copy = tile.clone();
        ItemMeta meta = copy.getItemMeta();

        if (meta != null && meta.hasDisplayName()) {
            meta.setDisplayName(UNIFIED_GROUP_COLOR + ChatColor.stripColor(meta.getDisplayName()));
            copy.setItemMeta(meta);
        }

        return copy;
    }

    private void showItemGroup(ChestMenu menu, Player p, PlayerProfile profile, ItemGroup group, int index) {
        if (!(group instanceof LockedItemGroup) || !isSurvivalMode() || ((LockedItemGroup) group).hasUnlocked(p, profile)) {
            menu.addItem(index, unifiedGroupTile(p, group));
            menu.addMenuClickHandler(index, (pl, slot, item, action) -> {
                openItemGroup(profile, group, 1);
                return false;
            });
        } else {
            List<String> lore = new ArrayList<>();
            lore.add("");

            for (String line : Slimefun.getLocalization().getMessages(p, "guide.locked-itemgroup")) {
                lore.add(ChatColor.WHITE + line);
            }

            lore.add("");

            for (ItemGroup parent : ((LockedItemGroup) group).getParents()) {
                lore.add(parent.getItem(p).getItemMeta().getDisplayName());
            }

            menu.addItem(index, CustomItemStack.create(Material.BARRIER, "&4" + Slimefun.getLocalization().getMessage(p, "guide.locked") + " &7- " + UNIFIED_GROUP_COLOR + ChatColor.stripColor(group.getItem(p).getItemMeta().getDisplayName()), lore.toArray(new String[0])));
            menu.addMenuClickHandler(index, ChestMenuUtils.getEmptyClickHandler());
        }
    }

    @Override
    @ParametersAreNonnullByDefault
    public void openItemGroup(PlayerProfile profile, ItemGroup itemGroup, int page) {
        Player p = profile.getPlayer();

        if (p == null) {
            return;
        }

        // Core's category tiles and standard nested groups keep their normal open: CategoryItemGroup is the
        // category mechanism, and NestedItemGroup is plain nested browsing (still "categories + item lists").
        // Every OTHER FlexItemGroup is a deprecated addon custom screen - taken out of the guide, so it must
        // never open (guards search/history paths); bounce to the main menu instead.
        if (itemGroup instanceof CategoryItemGroup || itemGroup instanceof NestedItemGroup) {
            ((FlexItemGroup) itemGroup).open(p, profile, getMode());
            return;
        }

        if (itemGroup instanceof FlexItemGroup) {
            warnDeprecatedCustomGuideUi(itemGroup);
            openMainMenu(profile, profile.getGuideHistory().getMainMenuPage());
            return;
        }

        // Collapse variant groups (one slot per group, not per member) and drop world-disabled items up
        // front, so pagination counts the slots actually drawn. Previously a skipped item silently left a
        // gap and short-changed the page.
        List<SlimefunItem> items = collapseForDisplay(p, itemGroup.getItems());

        if (isSurvivalMode()) {
            profile.getGuideHistory().add(itemGroup, page);
        }

        ChestMenu menu = create(p);
        createHeader(p, profile, menu);

        addBackButton(menu, 1, p, profile);

        int pages = (items.size() - 1) / MAX_ITEM_GROUPS + 1;

        menu.addItem(46, ChestMenuUtils.getPreviousButton(p, page, pages));
        menu.addMenuClickHandler(46, (pl, slot, item, action) -> {
            int next = page - 1;

            if (next != page && next > 0) {
                openItemGroup(profile, itemGroup, next);
            }

            return false;
        });

        menu.addItem(52, ChestMenuUtils.getNextButton(p, page, pages));
        menu.addMenuClickHandler(52, (pl, slot, item, action) -> {
            int next = page + 1;

            if (next != page && next <= pages) {
                openItemGroup(profile, itemGroup, next);
            }

            return false;
        });

        int index = 9;
        int itemGroupIndex = MAX_ITEM_GROUPS * (page - 1);
        AsyncVariantDisplayTask variantTask = new AsyncVariantDisplayTask();

        for (int i = 0; i < MAX_ITEM_GROUPS; i++) {
            int target = itemGroupIndex + i;

            if (target >= items.size()) {
                break;
            }

            SlimefunItem sfitem = items.get(target);
            displaySlimefunItem(menu, itemGroup, p, profile, sfitem, page, index);

            VariantGroup group = Slimefun.getVariantGroups().getGroup(sfitem.getId());

            if (group != null) {
                variantTask.add(index, variantDisplayStacks(group));
            }

            index++;
        }

        menu.open(p);

        if (!variantTask.isEmpty()) {
            variantTask.start(menu.toInventory());
        }
    }

    /**
     * One display copy per member of {@code group}, each marked with its {@code n/total} position so the
     * packet layer can render the counter beside the per-viewer name.
     */
    @Nonnull
    private List<ItemStack> variantDisplayStacks(@Nonnull VariantGroup group) {
        List<ItemStack> stacks = new ArrayList<>();
        int total = group.size();
        int position = 1;

        for (SlimefunItem variant : group.getVariants()) {
            ItemStack stack = variant.getItem().clone();
            VariantDisplayMarker.mark(stack, position, total);
            stacks.add(stack);
            position++;
        }

        return stacks;
    }

    /**
     * The variant a cycling group slot is currently showing, resolved from the marker on the stack the
     * player clicked; {@code anchor} itself for an ordinary ungrouped item.
     */
    @Nonnull
    private SlimefunItem resolveShownVariant(@Nonnull SlimefunItem anchor, @Nullable ItemStack clicked) {
        VariantGroup group = Slimefun.getVariantGroups().getGroup(anchor.getId());

        if (group == null || clicked == null) {
            return anchor;
        }

        String position = VariantDisplayMarker.read(clicked.getItemMeta());

        if (position == null) {
            return anchor;
        }

        int separator = position.indexOf('/');

        if (separator < 1) {
            return anchor;
        }

        try {
            int index = Integer.parseInt(position.substring(0, separator));

            if (index >= 1 && index <= group.size()) {
                return group.getVariants().get(index - 1);
            }
        } catch (NumberFormatException ignored) {
            // fall through to the anchor
        }

        return anchor;
    }

    /**
     * Adds previous/next buttons on the bottom row that step through the variants of {@code item}'s
     * {@link VariantGroup}, so a group reached from one guide slot can be browsed like pages. A no-op for
     * an ungrouped item.
     */
    @ParametersAreNonnullByDefault
    private void addVariantButtons(ChestMenu menu, PlayerProfile profile, Player p, SlimefunItem item) {
        VariantGroup group = Slimefun.getVariantGroups().getGroup(item.getId());

        if (group == null || group.size() < 2) {
            return;
        }

        int position = group.indexOf(item.getId());

        if (position == 0) {
            return;
        }

        // Wrap around: a group is a ring, so browsing never dead-ends on the first or last variant.
        SlimefunItem previous = group.getVariants().get((position - 2 + group.size()) % group.size());
        SlimefunItem next = group.getVariants().get(position % group.size());

        // Bottom row, where every other paginated guide screen puts its page buttons. Free here: the
        // recipe-display pager uses 28/34, not 46/52.
        menu.addItem(46, ChestMenuUtils.getPreviousButton(p, position, group.size()));
        menu.addMenuClickHandler(46, (pl, slot, itemstack, action) -> {
            displayItem(profile, previous, true);
            return false;
        });

        menu.addItem(52, ChestMenuUtils.getNextButton(p, position, group.size()));
        menu.addMenuClickHandler(52, (pl, slot, itemstack, action) -> {
            displayItem(profile, next, true);
            return false;
        });
    }

    /** Puts a cheated copy of {@code item} in the player's inventory, a full stack when shift-clicked. */
    @ParametersAreNonnullByDefault
    private void giveCheatedItem(Player p, SlimefunItem item, boolean fullStack) {
        ItemStack clonedItem = item.getItem().clone();

        if (fullStack) {
            clonedItem.setAmount(clonedItem.getMaxStackSize());
        }

        p.getInventory().addItem(clonedItem);
    }

    /**
     * A cheat-mode picker listing every member of {@code group}, so the player chooses which flavour to
     * take rather than receiving whichever one the cycling slot was showing.
     *
     * @param origin
     *            The item list this was opened from, so the back button returns there
     * @param originPage
     *            That list's page
     * @param page
     *            The picker's own page
     */
    @ParametersAreNonnullByDefault
    private void openVariantPicker(PlayerProfile profile, VariantGroup group, ItemGroup origin, int originPage, int page) {
        Player p = profile.getPlayer();

        if (p == null) {
            return;
        }

        List<SlimefunItem> variants = group.getVariants();
        int pages = (variants.size() - 1) / MAX_ITEM_GROUPS + 1;

        ChestMenu menu = create(p);
        createHeader(p, profile, menu, pages > 1);

        menu.addItem(1, ChestMenuUtils.getBackButton(p, "", ChatColor.GRAY + Slimefun.getLocalization().getMessage(p, "guide.back.title")));
        menu.addMenuClickHandler(1, (pl, slot, item, action) -> {
            openItemGroup(profile, origin, originPage);
            return false;
        });

        // ChestMenu sizes itself to the highest occupied slot, so a pager pinned to the bottom row would
        // stretch a five-variant group to six rows. Only a group that actually pages needs one, and it
        // stays on a fixed row there so the menu does not resize as the player pages through it.
        if (pages > 1) {
            menu.addItem(46, ChestMenuUtils.getPreviousButton(p, page, pages));
            menu.addMenuClickHandler(46, (pl, slot, item, action) -> {
                if (page > 1) {
                    openVariantPicker(profile, group, origin, originPage, page - 1);
                }

                return false;
            });

            menu.addItem(52, ChestMenuUtils.getNextButton(p, page, pages));
            menu.addMenuClickHandler(52, (pl, slot, item, action) -> {
                if (page < pages) {
                    openVariantPicker(profile, group, origin, originPage, page + 1);
                }

                return false;
            });
        }

        int index = 9;
        int offset = MAX_ITEM_GROUPS * (page - 1);

        for (int i = 0; i < MAX_ITEM_GROUPS && offset + i < variants.size(); i++) {
            SlimefunItem variant = variants.get(offset + i);

            // Marked so the packet layer renders the same "(n/total)" counter the cycling slot shows,
            // which is what tells two otherwise similar-looking flavours apart.
            ItemStack display = variant.getItem().clone();
            VariantDisplayMarker.mark(display, offset + i + 1, variants.size());

            menu.addItem(index, display);
            menu.addMenuClickHandler(index, (pl, slot, item, action) -> {
                if (pl.hasPermission("slimefun.cheat.items")) {
                    giveCheatedItem(pl, variant, action.isShiftClicked());
                } else {
                    Slimefun.getLocalization().sendMessage(pl, "messages.no-permission", true);
                }

                return false;
            });

            index++;
        }

        menu.open(p);
    }

    /**
     * The items a listing should actually draw: world-disabled ones dropped, and every
     * {@link VariantGroup} reduced to its anchor so a group occupies ONE slot rather than one per member.
     *
     * @implNote Shared because the guide has two independent listing paths - {@link #openItemGroup} for a
     *           plain item group and {@link #openCategoryItemsFlat} for the categorized view. Collapsing
     *           in only one of them meant the categorized view (the one players actually browse) still
     *           drew every variant as its own tile.
     */
    @Nonnull
    List<SlimefunItem> collapseForDisplay(@Nonnull Player p, @Nonnull List<SlimefunItem> source) {
        List<SlimefunItem> visible = new ArrayList<>();

        for (SlimefunItem candidate : source) {
            if (candidate.isDisabledIn(p.getWorld()) || Slimefun.getVariantGroups().isCollapsedMember(candidate.getId())) {
                continue;
            }

            visible.add(candidate);
        }

        return visible;
    }

    private final java.util.Set<String> warnedCustomGuideUis = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private void warnDeprecatedCustomGuideUi(@Nonnull ItemGroup group) {
        if (warnedCustomGuideUis.add(group.getKey().toString())) {
            SlimefunAddon addon = group.getAddon();
            String owner = addon != null ? addon.getName() : "unknown";
            Slimefun.logger().warning("[Guide] Addon '" + owner + "' uses a custom guide screen (" + group.getKey()
                + "). Custom guide layouts are deprecated - the guide only shows categories and item lists. "
                + "It is now rendered as a plain item list. Use a GuideWidget button for functional screens.");
        }
    }

    private void displaySlimefunItem(ChestMenu menu, ItemGroup itemGroup, Player p, PlayerProfile profile, SlimefunItem sfitem, int page, int index) {
        Research research = sfitem.getResearch();

        if (isSurvivalMode() && !hasPermission(p, sfitem)) {
            List<String> message = Slimefun.getPermissionsService().getLore(sfitem);
            menu.addItem(index, CustomItemStack.create(ChestMenuUtils.getNoPermissionItem(), Slimefun.getItemTranslationService().getName(p, sfitem), message.toArray(new String[0])));
            menu.addMenuClickHandler(index, ChestMenuUtils.getEmptyClickHandler());
        } else if (isSurvivalMode() && research != null && !profile.hasUnlocked(research)) {
            menu.addItem(index, CustomItemStack.create(ChestMenuUtils.getNotResearchedItem(), ChatColor.WHITE + ChatColor.stripColor(Slimefun.getItemTranslationService().getName(p, sfitem)), "&4&l" + Slimefun.getLocalization().getMessage(p, "guide.locked"), "", Slimefun.getLocalization().getMessage(p, "guide.research.unlock"), "", Slimefun.getLocalization().getMessage(p, "guide.research.cost").replace("%levels%", String.valueOf(research.getCost()))));
            menu.addMenuClickHandler(index, (pl, slot, item, action) -> {
                research.unlockFromGuide(this, p, profile, sfitem, itemGroup, page);
                return false;
            });
        } else {
            menu.addItem(index, sfitem.getItem());
            menu.addMenuClickHandler(index, (pl, slot, item, action) -> {
                try {
                    // A group slot cycles, so act on the variant actually on screen rather than the anchor.
                    SlimefunItem clicked = resolveShownVariant(sfitem, item);

                    if (isSurvivalMode()) {
                        displayItem(profile, clicked, true);
                    } else if (pl.hasPermission("slimefun.cheat.items")) {
                        VariantGroup variantGroup = Slimefun.getVariantGroups().getGroup(sfitem.getId());

                        if (variantGroup != null && variantGroup.size() > 1) {
                            // Cheating one flavour of a grouped item is a choice, not a guess at whichever
                            // variant the slot happened to be showing - let the player pick.
                            openVariantPicker(profile, variantGroup, itemGroup, page, 1);
                        } else {
                            // Multiblock items can be cheated in like any other: placing one now assembles
                            // the whole structure (see MultiBlockAssembler), so there is nothing to forbid.
                            giveCheatedItem(pl, clicked, action.isShiftClicked());
                        }
                    } else {
                        /*
                         * Fixes #3548 - If for whatever reason,
                         * an unpermitted players gets access to this guide,
                         * this will be our last line of defense to prevent any exploit.
                         */
                        Slimefun.getLocalization().sendMessage(pl, "messages.no-permission", true);
                    }
                } catch (Exception | LinkageError x) {
                    printErrorMessage(pl, sfitem, x);
                }

                return false;
            });
        }
    }

    @Override
    @ParametersAreNonnullByDefault
    public void openSearch(PlayerProfile profile, String input, boolean addToHistory) {
        Player p = profile.getPlayer();

        if (p == null) {
            return;
        }

        ChestMenu menu = new ChestMenu(Slimefun.getLocalization().getMessage(p, "guide.search.inventory").replace("%item%", ChatUtils.crop(ChatColor.WHITE, input)));
        String searchTerm = ChatColor.stripColor(input.toLowerCase(Locale.ROOT));

        if (addToHistory) {
            profile.getGuideHistory().add(searchTerm);
        }

        menu.setEmptySlotsClickable(false);
        createHeader(p, profile, menu);
        addBackButton(menu, 1, p, profile);

        int index = 9;

        for (SlimefunItem slimefunItem : searchHits(p, Slimefun.getRegistry().getEnabledSlimefunItems(), searchTerm)) {
            if (index == 44) {
                break;
            }

            ItemStack itemstack = CustomItemStack.create(slimefunItem.getItem(), meta -> {
                ItemGroup itemGroup = slimefunItem.getItemGroup();
                String categoryLabel = io.github.thebusybiscuit.slimefun5.core.guide.categories.CategoryMenuBuilder
                    .resolveCategoryLabel(p, itemGroup, Slimefun.getGuideCategories());
                String themeName = ChatColor.translateAlternateColorCodes('&', categoryLabel);
                meta.setLore(Arrays.asList("", ChatColor.DARK_GRAY + "\u21E8 " + ChatColor.WHITE + themeName + ChatColor.GRAY + " \u25B8 " + ChatColor.WHITE + itemGroup.getDisplayName(p)));
                VersionedItemFlag.addFlags(meta, VersionedItemFlag.HIDE_ATTRIBUTES, VersionedItemFlag.HIDE_ENCHANTS, VersionedItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            });

            menu.addItem(index, itemstack);
            menu.addMenuClickHandler(index, (pl, slot, itm, action) -> {
                try {
                    if (!isSurvivalMode()) {
                        VariantGroup hitGroup = Slimefun.getVariantGroups().getGroup(slimefunItem.getId());

                        if (hitGroup != null && hitGroup.size() > 1) {
                            openVariantPicker(profile, hitGroup, slimefunItem.getItemGroup(), 1, 1);
                        } else {
                            giveCheatedItem(pl, slimefunItem, action.isShiftClicked());
                        }
                    } else {
                        displayItem(profile, slimefunItem, true);
                    }
                } catch (Exception | LinkageError x) {
                    printErrorMessage(pl, slimefunItem, x);
                }

                return false;
            });

            index++;
        }

        menu.open(p);
    }

    /**
     * The items a search for {@code searchTerm} should list: everything visible to {@code p} that matches,
     * capped at one entry per {@link VariantGroup}.
     *
     * @implNote The entry kept is the variant that actually matched, not the group's anchor. Skipping every
     *           collapsed member before testing the name meant only the anchor could ever match, so a
     *           search for "gold" never found the gold leg plates sitting behind an iron anchor.
     *
     * @param p
     *            The searching {@link Player}
     * @param items
     *            The items to search
     * @param searchTerm
     *            The lowercased, colour-stripped term
     *
     * @return The matching items, at most one per variant group
     */
    @Nonnull
    @ParametersAreNonnullByDefault
    List<SlimefunItem> searchHits(Player p, Collection<SlimefunItem> items, String searchTerm) {
        List<SlimefunItem> hits = new ArrayList<>();
        Set<String> matchedGroups = new HashSet<>();

        for (SlimefunItem item : items) {
            VariantGroup group = Slimefun.getVariantGroups().getGroup(item.getId());

            if (group != null && matchedGroups.contains(group.getKey().toString())) {
                continue;
            }

            if (item.isHidden()
                || AddonVisibility.isHidden(p, item.getItemGroup().getKey().getNamespace())
                || !isItemGroupAccessible(p, item)
                || !isSearchFilterApplicable(p, item, searchTerm)) {
                continue;
            }

            if (group != null) {
                matchedGroups.add(group.getKey().toString());
            }

            hits.add(item);
        }

        return hits;
    }

    @ParametersAreNonnullByDefault
    private boolean isItemGroupAccessible(Player p, SlimefunItem slimefunItem) {
        return showHiddenItemGroupsInSearch || slimefunItem.getItemGroup().isAccessible(p);
    }

    /**
     * Whether {@code slimefunItem} matches {@code searchTerm}, by its English or its translated display
     * name.
     *
     * @implNote Deliberately NOT {@link SlimefunItem#getItemName()}: under the "name is always the id" rule
     *           that returns the raw id, which turned search into an id-substring match. Searching
     *           "binding" then returned every {@code *_TRAIT_PROP_BINDING_*} item - displayed as "Nimble",
     *           "Works" and so on - while crowding the real bindings out of the capped result list.
     */
    @ParametersAreNonnullByDefault
    private boolean isSearchFilterApplicable(Player p, SlimefunItem slimefunItem, String searchTerm) {
        String englishName = Slimefun.getItemTranslationService().getNameForLanguage("en", slimefunItem.getId());

        if (matches(englishName, searchTerm)) {
            return true;
        }

        return matches(Slimefun.getItemTranslationService().getName(p, slimefunItem), searchTerm);
    }

    private static boolean matches(@Nullable String name, @Nonnull String searchTerm) {
        if (name == null) {
            return false;
        }

        String stripped = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', name)).toLowerCase(Locale.ROOT);
        return !stripped.isEmpty() && stripped.contains(searchTerm);
    }

    @Override
    @ParametersAreNonnullByDefault
    public void displayItem(PlayerProfile profile, ItemStack item, int index, boolean addToHistory) {
        Player p = profile.getPlayer();

        if (p == null || item == null || item.getType() == Material.AIR) {
            return;
        }

        SlimefunItem sfItem = SlimefunItem.getByItem(item);

        if (sfItem != null) {
            displayItem(profile, sfItem, addToHistory);
            return;
        }

        if (!showVanillaRecipes) {
            return;
        }

        Recipe[] recipes = Slimefun.getMinecraftRecipeService().getRecipesFor(item);

        if (recipes.length == 0) {
            return;
        }

        showMinecraftRecipe(recipes, index, item, profile, p, addToHistory);
    }

    private void showMinecraftRecipe(Recipe[] recipes, int index, ItemStack item, PlayerProfile profile, Player p, boolean addToHistory) {
        Recipe recipe = recipes[index];

        ItemStack[] recipeItems = new ItemStack[9];
        RecipeType recipeType = RecipeType.NULL;
        ItemStack result = null;

        Optional<MinecraftRecipe<? super Recipe>> optional = MinecraftRecipe.of(recipe);
        AsyncRecipeChoiceTask task = new AsyncRecipeChoiceTask();

        if (optional.isPresent()) {
            showRecipeChoices(recipe, recipeItems, task);

            recipeType = new RecipeType(optional.get());
            result = recipe.getResult();
        } else {
            recipeItems = new ItemStack[] { null, null, null, null, CustomItemStack.create(Material.BARRIER, Slimefun.getLocalization().getMessage(p, "guide.recipe.error")), null, null, null, null };
        }

        ChestMenu menu = create(p);

        if (addToHistory) {
            profile.getGuideHistory().add(item, index);
        }

        displayItem(menu, profile, p, item, result, recipeType, recipeItems, task);

        if (recipes.length > 1) {
            for (int i = 27; i < 36; i++) {
                menu.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
            }

            menu.addItem(28, ChestMenuUtils.getPreviousButton(p, index + 1, recipes.length), (pl, slot, action, stack) -> {
                if (index > 0) {
                    showMinecraftRecipe(recipes, index - 1, item, profile, p, true);
                }
                return false;
            });

            menu.addItem(34, ChestMenuUtils.getNextButton(p, index + 1, recipes.length), (pl, slot, action, stack) -> {
                if (index < recipes.length - 1) {
                    showMinecraftRecipe(recipes, index + 1, item, profile, p, true);
                }
                return false;
            });
        }

        menu.open(p);

        if (!task.isEmpty()) {
            task.start(menu.toInventory());
        }
    }

    private <T extends Recipe> void showRecipeChoices(T recipe, ItemStack[] recipeItems, AsyncRecipeChoiceTask task) {
        RecipeChoice[] choices = Slimefun.getMinecraftRecipeService().getRecipeShape(recipe);

        if (choices.length == 1 && choices[0] instanceof MaterialChoice) {
            MaterialChoice materialChoice = (MaterialChoice) choices[0];
            recipeItems[4] = new ItemStack(materialChoice.getChoices().get(0));

            if (materialChoice.getChoices().size() > 1) {
                task.add(recipeSlots[4], materialChoice);
            }
        } else {
            for (int i = 0; i < choices.length; i++) {
                if (choices[i] instanceof MaterialChoice) {
                    MaterialChoice materialChoice = (MaterialChoice) choices[i];
                    recipeItems[i] = new ItemStack(materialChoice.getChoices().get(0));

                    if (materialChoice.getChoices().size() > 1) {
                        task.add(recipeSlots[i], materialChoice);
                    }
                }
            }
        }
    }

    @Override
    @ParametersAreNonnullByDefault
    public void displayItem(PlayerProfile profile, SlimefunItem item, boolean addToHistory) {
        Player p = profile.getPlayer();

        if (p == null) {
            return;
        }

        ChestMenu menu = create(p);

        if (io.github.thebusybiscuit.slimefun5.core.guide.SlimefunGuide.showExternalLinks()) {
            // Every item links to the wiki (uniform per-plugin URL), even if its page isn't authored yet -
            // an item's explicit wiki page still wins. See WikiLinks.
            String wikiUrl = io.github.thebusybiscuit.slimefun5.core.guide.wiki.WikiLinks.urlFor(item);
            menu.addItem(8, CustomItemStack.create(XMaterial.KNOWLEDGE_BOOK.parseMaterial(), ChatColor.WHITE + Slimefun.getLocalization().getMessage(p, "guide.tooltips.wiki"), "", ChatColor.GRAY + "\u21E8 " + ChatColor.GREEN + Slimefun.getLocalization().getMessage(p, "guide.tooltips.open-itemgroup")));
            menu.addMenuClickHandler(8, (pl, slot, itemstack, action) -> {
                pl.closeInventory();
                ChatUtils.sendURL(pl, wikiUrl);
                return false;
            });
        } else {
            // External links disabled in config: fill the slot with background glass.
            menu.addItem(8, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }

        AsyncRecipeChoiceTask task = new AsyncRecipeChoiceTask();

        if (addToHistory) {
            profile.getGuideHistory().add(item);
        }

        ItemStack result = item.getRecipeOutput();
        RecipeType recipeType = item.getRecipeType();
        ItemStack[] recipe = item.getRecipe();

        displayItem(menu, profile, p, item, result, recipeType, recipe, task);

        if (item instanceof RecipeDisplayItem) {
            RecipeDisplayItem recipeDisplayItem = (RecipeDisplayItem) item;            displayRecipes(p, profile, menu, recipeDisplayItem, 0);
        }

        addVariantButtons(menu, profile, p, item);

        menu.open(p);

        if (!task.isEmpty()) {
            task.start(menu.toInventory());
        }
    }

    private void displayItem(ChestMenu menu, PlayerProfile profile, Player p, Object item, ItemStack output, RecipeType recipeType, ItemStack[] recipe, AsyncRecipeChoiceTask task) {
        addBackButton(menu, 0, p, profile);

        MenuClickHandler clickHandler = (pl, slot, itemstack, action) -> {
            try {
                if (itemstack != null && itemstack.getType() != Material.BARRIER) {
                    displayItem(profile, itemstack, 0, true);
                }
            } catch (Exception | LinkageError x) {
                printErrorMessage(pl, x);
            }
            return false;
        };

        boolean isSlimefunRecipe = item instanceof SlimefunItem;

        for (int i = 0; i < 9; i++) {
            ItemStack recipeItem = getDisplayItem(p, isSlimefunRecipe, recipe[i]);
            menu.addItem(recipeSlots[i], recipeItem, clickHandler);

            if (recipeItem != null && item instanceof MultiBlockMachine) {
                for (Tag<Material> tag : MultiBlock.getSupportedTags()) {
                    if (tag.isTagged(recipeItem.getType())) {
                        task.add(recipeSlots[i], tag);
                        break;
                    }
                }
            }
        }

        ItemStack machineIcon = recipeType.getItem(p);
        if (machineIcon == null || machineIcon.getType() == Material.AIR) {
            // The machine/multiblock icon (slot 10) came back empty - surface why so a missing machine in
            // the recipe view is diagnosable on the live server rather than silently blank.
            Slimefun.logger().warning("[Guide] Recipe machine icon is empty for recipe type '" + recipeType.getKey()
                + "' (machine item id=" + (recipeType.getMachine() != null ? recipeType.getMachine().getId() : "none")
                + ", toItem=" + (recipeType.toItem() == null ? "null" : recipeType.toItem().getType()) + ").");
        }
        // Clicking the machine/multiblock icon opens that machine's own guide page, when the recipe
        // type resolves to a registered machine (standard Slimefun machines and multiblocks).
        SlimefunItem machine = recipeType.getMachine();
        if (machine != null && machine != item && !machine.isDisabled()) {
            menu.addItem(10, machineIcon, (pl, slot, itemstack, action) -> {
                try {
                    displayItem(profile, machine, true);
                } catch (Exception | LinkageError x) {
                    printErrorMessage(pl, x);
                }
                return false;
            });
        } else {
            menu.addItem(10, machineIcon, ChestMenuUtils.getEmptyClickHandler());
        }

        // The packet layer translates the result per viewer; the canonical template is id-only here.
        ItemStack displayedOutput = isSlimefunRecipe ? ((SlimefunItem) item).getItem() : output;
        menu.addItem(16, displayedOutput, ChestMenuUtils.getEmptyClickHandler());
    }

    @ParametersAreNonnullByDefault
    public void createHeader(Player p, PlayerProfile profile, ChestMenu menu) {
        createHeader(p, profile, menu, true);
    }

    /**
     * Draws the guide chrome into {@code menu}.
     *
     * @param p
     *            The viewing {@link Player}
     * @param profile
     *            That player's {@link PlayerProfile}
     * @param menu
     *            The menu to draw into
     * @param footer
     *            Whether to fill the bottom row. A {@link ChestMenu} sizes itself to its highest occupied
     *            slot, so a screen with only a handful of entries must skip it or be padded out to six
     *            rows of empty background.
     */
    @ParametersAreNonnullByDefault
    public void createHeader(Player p, PlayerProfile profile, ChestMenu menu, boolean footer) {
        Validate.notNull(p, "The Player cannot be null!");
        Validate.notNull(profile, "The Profile cannot be null!");
        Validate.notNull(menu, "The Inventory cannot be null!");

        for (int i = 0; i < 9; i++) {
            menu.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }

        // Settings Panel
        menu.addItem(1, ChestMenuUtils.getMenuButton(p));
        menu.addMenuClickHandler(1, (pl, slot, item, action) -> {
            SlimefunGuideSettings.openSettings(pl, HandCompat.getMainHand(pl.getInventory()));
            return false;
        });

        // Search feature!
        menu.addItem(7, ChestMenuUtils.getSearchButton(p));
        menu.addMenuClickHandler(7, (pl, slot, item, action) -> {
            pl.closeInventory();

            Slimefun.getLocalization().sendMessage(pl, "guide.search.message");
            ChatInput.waitForPlayer(Slimefun.instance(), pl, msg -> SlimefunGuide.openSearch(profile, msg, getMode(), isSurvivalMode()));

            return false;
        });

        if (footer) {
            for (int i = 45; i < 54; i++) {
                menu.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
            }
        }
    }

    private void addBackButton(ChestMenu menu, int slot, Player p, PlayerProfile profile) {
        GuideHistory history = profile.getGuideHistory();

        if (isSurvivalMode() && history.size() > 1) {
            menu.addItem(slot, ChestMenuUtils.getBackButton(p, Slimefun.getLocalization().getMessages(p, "guide.back.page").toArray(new String[0])));

            menu.addMenuClickHandler(slot, (pl, s, is, action) -> {
                if (action.isShiftClicked()) {
                    openMainMenu(profile, profile.getGuideHistory().getMainMenuPage());
                } else {
                    history.goBack(this);
                }
                return false;
            });

        } else {
            menu.addItem(slot, ChestMenuUtils.getBackButton(p, "", ChatColor.GRAY + Slimefun.getLocalization().getMessage(p, "guide.back.guide")));
            menu.addMenuClickHandler(slot, (pl, s, is, action) -> {
                openMainMenu(profile, profile.getGuideHistory().getMainMenuPage());
                return false;
            });
        }
    }

    @ParametersAreNonnullByDefault
    private static @Nonnull ItemStack getDisplayItem(Player p, boolean isSlimefunRecipe, ItemStack item) {
        if (isSlimefunRecipe) {
            SlimefunItem slimefunItem = SlimefunItem.getByItem(item);

            if (slimefunItem == null) {
                return item;
            }

            ItemTranslationService translations = Slimefun.getItemTranslationService();

            if (slimefunItem.canUse(p, false)) {
                // Preserve the recipe slot's required amount - returning the canonical item dropped it to 1,
                // so recipes needing several of a Slimefun ingredient wrongly showed a single item.
                ItemStack display = slimefunItem.getItem().clone();
                display.setAmount(item.getAmount());
                return display;
            }

            String lore = hasPermission(p, slimefunItem) ? Slimefun.getLocalization().getMessage(p, "guide.recipe.needs-unlock").replace("%group%", slimefunItem.getItemGroup().getDisplayName(p)) : Slimefun.getLocalization().getMessage(p, "guide.recipe.no-permission");
            return CustomItemStack.create(Material.BARRIER, translations.getName(p, slimefunItem), "&4&l" + Slimefun.getLocalization().getMessage(p, "guide.locked"), "", lore);
        } else {
            return item;
        }
    }

    @ParametersAreNonnullByDefault
    private void displayRecipes(Player p, PlayerProfile profile, ChestMenu menu, RecipeDisplayItem sfItem, int page) {
        List<ItemStack> recipes = sfItem.getDisplayRecipes();

        if (!recipes.isEmpty()) {
            menu.addItem(53, null);

            if (page == 0) {
                for (int i = 27; i < 36; i++) {
                    menu.replaceExistingItem(i, CustomItemStack.create(ChestMenuUtils.getBackground(), sfItem.getRecipeSectionLabel(p)));
                    menu.addMenuClickHandler(i, ChestMenuUtils.getEmptyClickHandler());
                }
            }

            int pages = (recipes.size() - 1) / 18 + 1;

            menu.replaceExistingItem(28, ChestMenuUtils.getPreviousButton(p, page + 1, pages));
            menu.addMenuClickHandler(28, (pl, slot, itemstack, action) -> {
                if (page > 0) {
                    displayRecipes(pl, profile, menu, sfItem, page - 1);
                    SoundEffect.GUIDE_BUTTON_CLICK_SOUND.playFor(pl);
                }

                return false;
            });

            menu.replaceExistingItem(34, ChestMenuUtils.getNextButton(p, page + 1, pages));
            menu.addMenuClickHandler(34, (pl, slot, itemstack, action) -> {
                if (recipes.size() > (18 * (page + 1))) {
                    displayRecipes(pl, profile, menu, sfItem, page + 1);
                    SoundEffect.GUIDE_BUTTON_CLICK_SOUND.playFor(pl);
                }

                return false;
            });

            int inputs = 36;
            int outputs = 45;

            for (int i = 0; i < 18; i++) {
                int slot;

                if (i % 2 == 0) {
                    slot = inputs;
                    inputs++;
                } else {
                    slot = outputs;
                    outputs++;
                }

                addDisplayRecipe(menu, profile, recipes, slot, i, page);
            }
        }
    }

    private void addDisplayRecipe(ChestMenu menu, PlayerProfile profile, List<ItemStack> recipes, int slot, int i, int page) {
        if ((i + (page * 18)) < recipes.size()) {
            ItemStack displayItem = recipes.get(i + (page * 18));

            /*
             * We want to clone this item to avoid corrupting the original
             * but we wanna make sure no stupid addon creator sneaked some nulls in here
             */
            if (displayItem != null) {
                displayItem = displayItem.clone();

                // Re-localize Slimefun items into the viewing player's language (vanilla items
                // are already shown in the client's locale by Minecraft). Only the name is
                // replaced, so any recipe-specific lore (amounts, chances) is preserved.
                Player p = profile.getPlayer();
                SlimefunItem sfItem = SlimefunItem.getByItem(displayItem);

                if (p != null && sfItem != null) {
                    String name = Slimefun.getItemTranslationService().getName(p, sfItem);
                    ItemMeta meta = displayItem.getItemMeta();

                    if (meta != null && name != null && !name.isEmpty()) {
                        meta.setDisplayName(name);
                        displayItem.setItemMeta(meta);
                    }
                }
            }

            menu.replaceExistingItem(slot, displayItem);

            if (page == 0) {
                menu.addMenuClickHandler(slot, (pl, s, itemstack, action) -> {
                    displayItem(profile, itemstack, 0, true);
                    return false;
                });
            }
        } else {
            menu.replaceExistingItem(slot, null);
            menu.addMenuClickHandler(slot, ChestMenuUtils.getEmptyClickHandler());
        }
    }

    @ParametersAreNonnullByDefault
    private static boolean hasPermission(Player p, SlimefunItem item) {
        return Slimefun.getPermissionsService().hasPermission(p, item);
    }

    private @Nonnull ChestMenu create(@Nonnull Player p) {
        ChestMenu menu = new ChestMenu(Slimefun.getLocalization().getMessage(p, "guide.title.main"));

        menu.setEmptySlotsClickable(false);
        menu.addMenuOpeningHandler(SoundEffect.GUIDE_BUTTON_CLICK_SOUND::playFor);
        return menu;
    }

    @ParametersAreNonnullByDefault
    private void printErrorMessage(Player p, Throwable x) {
        p.sendMessage(ChatColor.DARK_RED + "An internal server error has occurred. Please inform an admin, check the console for further info.");
        Slimefun.logger().log(Level.SEVERE, "An error has occurred while trying to open a SlimefunItem in the guide!", x);
    }

    @ParametersAreNonnullByDefault
    private void printErrorMessage(Player p, SlimefunItem item, Throwable x) {
        p.sendMessage(ChatColor.DARK_RED + "An internal server error has occurred. Please inform an admin, check the console for further info.");
        item.error("This item has caused an error message to be thrown while viewing it in the Slimefun guide.", x);
    }

}

