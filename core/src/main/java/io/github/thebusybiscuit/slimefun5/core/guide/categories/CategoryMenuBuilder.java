package io.github.thebusybiscuit.slimefun5.core.guide.categories;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun5.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;
import io.github.thebusybiscuit.slimefun5.core.guide.AddonVisibility;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.MaterialCompat;

/**
 * Builds the transient {@link CategoryItemGroup} tiles for the categorized main menu. Slimefun's own
 * groups keep their curated category and their own names; every enabled ADDON item is pulled from the
 * registry, classified by type ({@link ItemTypeClassifier}) and grouped into "&lt;Addon&gt; &lt;Type&gt;"
 * section tiles under the matching type category - so an addon's items split across the shared categories
 * even when the addon's own guide is a single custom flex UI. Anything the classifier can't type lands in
 * the addon's Misc section (never dropped).
 */
public final class CategoryMenuBuilder {

    private CategoryMenuBuilder() {}

    @Nonnull
    public static List<ItemGroup> build(@Nonnull Player p, @Nonnull List<ItemGroup> visibleGroups, @Nonnull GuideCategoryRegistry registry) {
        return build(p, visibleGroups, Slimefun.getRegistry().getEnabledSlimefunItems(), registry);
    }

    /** Seam: the item source is a parameter so tests can pass constructed addon items without registering. */
    @Nonnull
    static List<ItemGroup> build(@Nonnull Player p, @Nonnull List<ItemGroup> visibleGroups, @Nonnull Collection<SlimefunItem> allItems, @Nonnull GuideCategoryRegistry registry) {
        Map<String, List<ItemGroup>> membersByCat = new LinkedHashMap<>();

        // Slimefun's own groups keep their curated category; addon groups are NOT bucketed here - their
        // items are classified individually below (their own guide UI stays in the classic layout).
        for (ItemGroup group : visibleGroups) {
            String ns = group.getKey().getNamespace();

            if (!"slimefun".equals(ns) || AddonVisibility.isHidden(p, ns)) {
                continue;
            }

            String declared = group.getCategoryId();
            String cat = (declared != null && registry.getById(declared) != null) ? declared : DefaultGuideCategories.MISC;
            membersByCat.computeIfAbsent(cat, k -> new ArrayList<>()).add(group);
        }

        Map<String, Map<String, List<SlimefunItem>>> addonItems = new LinkedHashMap<>();

        for (SlimefunItem item : allItems) {
            ItemGroup group = item.getItemGroup();

            if (group == null) {
                continue;
            }

            String ns = group.getKey().getNamespace();

            if ("slimefun".equals(ns) || AddonVisibility.isHidden(p, ns)) {
                continue;
            }

            if (item.isHidden() || item.isDisabledIn(p.getWorld())) {
                continue;
            }

            String typeId = ItemTypeClassifier.classify(item);
            String cat = (typeId != null && registry.getById(typeId) != null) ? typeId : DefaultGuideCategories.MISC;
            String addonName = item.getAddon() != null ? item.getAddon().getName() : ns;

            addonItems
                .computeIfAbsent(cat, k -> new LinkedHashMap<>())
                .computeIfAbsent(addonName, k -> new ArrayList<>())
                .add(item);
        }

        for (Map.Entry<String, Map<String, List<SlimefunItem>>> catEntry : addonItems.entrySet()) {
            String cat = catEntry.getKey();

            for (Map.Entry<String, List<SlimefunItem>> addonEntry : catEntry.getValue().entrySet()) {
                try {
                    membersByCat.computeIfAbsent(cat, k -> new ArrayList<>())
                        .add(section(cat, addonEntry.getKey(), addonEntry.getValue()));
                } catch (Exception | LinkageError x) {
                    Slimefun.logger().log(java.util.logging.Level.WARNING, x,
                        () -> "Could not build guide section: " + addonEntry.getKey() + " / " + cat);
                }
            }
        }

        // A single failing tile (e.g. a missing message key on an out-of-date messages.yml) must never
        // empty the whole guide, so each tile is built defensively and skipped on failure.
        List<ItemGroup> tiles = new ArrayList<>();

        for (GuideCategory category : registry.getAll()) {
            List<ItemGroup> members = membersByCat.get(category.getId());

            if (members != null && !members.isEmpty()) {
                try {
                    tiles.add(tile(p, category, members));
                } catch (Exception | LinkageError x) {
                    Slimefun.logger().log(java.util.logging.Level.WARNING, x,
                        () -> "Could not build guide category tile: " + category.getId());
                }
            }
        }

        return tiles;
    }

    /**
     * Explains why {@link #build} produced no tiles, as a human-readable breakdown of every filter stage.
     * Called only when the menu came back empty, so its cost is irrelevant. Mirrors build()'s filters
     * exactly so the counts pinpoint the culprit (core groups vs each addon-item filter).
     */
    @Nonnull
    public static String diagnose(@Nonnull Player p, @Nonnull List<ItemGroup> visibleGroups, @Nonnull GuideCategoryRegistry registry) {
        Collection<SlimefunItem> allItems = Slimefun.getRegistry().getEnabledSlimefunItems();

        int visibleTotal = visibleGroups.size();
        int coreGroups = 0;
        int coreHiddenByAddonVis = 0;
        for (ItemGroup group : visibleGroups) {
            String ns = group.getKey().getNamespace();
            if ("slimefun".equals(ns)) {
                if (AddonVisibility.isHidden(p, ns)) {
                    coreHiddenByAddonVis++;
                } else {
                    coreGroups++;
                }
            }
        }

        int addonTotal = 0;
        int skipNullGroup = 0;
        int skipCoreNs = 0;
        int skipAddonHidden = 0;
        int skipItemHidden = 0;
        int skipDisabledWorld = 0;
        int addonSurvived = 0;
        for (SlimefunItem item : allItems) {
            ItemGroup group = item.getItemGroup();
            if (group == null) {
                skipNullGroup++;
                continue;
            }
            String ns = group.getKey().getNamespace();
            if ("slimefun".equals(ns)) {
                skipCoreNs++;
                continue;
            }
            addonTotal++;
            if (AddonVisibility.isHidden(p, ns)) {
                skipAddonHidden++;
            } else if (item.isHidden()) {
                skipItemHidden++;
            } else if (item.isDisabledIn(p.getWorld())) {
                skipDisabledWorld++;
            } else {
                addonSurvived++;
            }
        }

        return "world=" + p.getWorld().getName()
            + " | visibleGroups=" + visibleTotal + " (coreVisible=" + coreGroups + ", coreHiddenByAddonVis=" + coreHiddenByAddonVis + ")"
            + " | addonItems=" + addonTotal + " -> survived=" + addonSurvived
            + " [skip: addonHidden=" + skipAddonHidden + ", itemHidden=" + skipItemHidden + ", disabledInWorld=" + skipDisabledWorld + "]"
            + " | coreNsItems=" + skipCoreNs + ", nullGroup=" + skipNullGroup;
    }

    /** A transient (unregistered) "<Addon> <Type>" section holding an addon's items of one type. */
    @Nonnull
    private static ItemGroup section(@Nonnull String categoryId, @Nonnull String addonName, @Nonnull List<SlimefunItem> items) {
        String title = ChatColor.YELLOW + addonName + " " + ItemTypeClassifier.typeSingular(categoryId);
        String keyId = "typed_" + categoryId + "_" + addonName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        ItemStack icon = ChestMenuUtils.stripTranslationIdentity(CustomItemStack.create(items.get(0).getItem().clone(), title));

        ItemGroup group = new ItemGroup(new NamespacedKey(Slimefun.instance(), keyId), icon);
        for (SlimefunItem item : items) {
            group.add(item);
        }

        return group;
    }

    /**
     * The localized display label for the category an {@link ItemGroup} is filed under - the same lookup
     * {@link #tile} uses for the category-menu tiles, exposed for anywhere else in the guide that needs to
     * show a group's owning category (e.g. search results). Every {@link SlimefunItem} belongs to exactly
     * one {@link ItemGroup}, and every {@link ItemGroup} resolves to exactly one category here, so there is
     * no multi-category case to reconcile - an undeclared or unknown category id simply falls back to
     * {@link DefaultGuideCategories#MISC}.
     */
    @Nonnull
    public static String resolveCategoryLabel(@Nonnull Player p, @Nonnull ItemGroup group, @Nonnull GuideCategoryRegistry registry) {
        String categoryId = resolveCategoryId(group, registry);
        GuideCategory category = registry.getById(categoryId);
        String fallback = category != null ? category.getDefaultName()
            : (group.getAddon() != null ? "&e" + group.getAddon().getName() : categoryId);

        return message(p, "guide.categories." + categoryId, fallback);
    }

    /**
     * The category id an {@link ItemGroup} resolves to: its own declared id if the registry recognizes it,
     * else {@link DefaultGuideCategories#MISC}. Split out from {@link #resolveCategoryLabel} so this
     * decision is testable without the localization service (which a headless test harness cannot fully
     * initialize - see {@code SlimefunLocalization}'s "Error: No language present" unit-test sentinel).
     */
    @Nonnull
    static String resolveCategoryId(@Nonnull ItemGroup group, @Nonnull GuideCategoryRegistry registry) {
        String declared = group.getCategoryId();
        return (declared != null && registry.getById(declared) != null) ? declared : DefaultGuideCategories.MISC;
    }

    @Nonnull
    private static CategoryItemGroup tile(@Nonnull Player p, @Nonnull GuideCategory category, @Nonnull List<ItemGroup> members) {
        String name = message(p, "guide.categories." + category.getId(), category.getDefaultName());

        String countLine = message(p, "guide.categories-meta.categories", "&7Categories: &e%count%")
            .replace("%count%", String.valueOf(members.size()));
        String openLine = message(p, "guide.categories-meta.open", "&aClick to open");

        ItemStack icon = CustomItemStack.create(MaterialCompat.stack(category.getIcon()), name, "", countLine, "", openLine);

        return new CategoryItemGroup(category, icon, members);
    }

    /**
     * A message lookup that never returns null and falls back to a sane default when the key is missing
     * (an out-of-date {@code messages.yml} that predates these keys) - the old direct lookups NPE'd on a
     * null result and emptied the entire guide.
     */
    @Nonnull
    private static String message(@Nonnull Player p, @Nonnull String key, @Nonnull String fallback) {
        String value = Slimefun.getLocalization().getMessage(p, key);

        if (value == null || value.startsWith(key) || value.startsWith("! Missing") || value.startsWith("guide.categories")) {
            return fallback;
        }

        return value;
    }
}
