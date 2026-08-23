package io.github.thebusybiscuit.slimefun5.core.guide;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import io.github.thebusybiscuit.slimefun5.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.core.guide.categories.CategoryMenuBuilder;
import io.github.thebusybiscuit.slimefun5.core.guide.options.SlimefunGuideSettings;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * Describes where an item lives in the guide: the buttons a player clicks to reach it, and the page it
 * sits on once there.
 * <p>
 * The route depends on the active layout, so this reads the viewer's own setting rather than assuming
 * one. Categorized goes category then that addon's section; classic goes the addon's menu then the group.
 */
public final class GuidePath {

    /** Matches the guide's own grid: 36 item slots per page. */
    private static final int PAGE_SIZE = 36;

    private static final String ARROW = " " + ChatColor.DARK_GRAY + "▸ " + ChatColor.WHITE;

    private GuidePath() {}

    /**
     * The lore lines describing how to reach an item, ready to append to a tooltip.
     *
     * @param p
     *            The viewer, whose layout setting decides the route
     * @param item
     *            The item to locate
     *
     * @return A blank separator line followed by the route, or an empty list if it cannot be described
     */
    @Nonnull
    public static List<String> describe(@Nonnull Player p, @Nonnull SlimefunItem item) {
        List<String> lore = new ArrayList<>();

        try {
            ItemGroup group = item.getItemGroup();

            if (group == null) {
                return lore;
            }

            String route = route(p, item, group);
            String page = Slimefun.getLocalization().getMessage(p, "guide.path.page")
                .replace("%page%", String.valueOf(pageOf(p, item, group)));

            lore.add("");
            lore.add(ChatColor.GRAY + Slimefun.getLocalization().getMessage(p, "guide.path.title"));
            lore.add(ChatColor.DARK_GRAY + "⇨ " + ChatColor.WHITE + route);
            lore.add(ChatColor.DARK_GRAY + "⇨ " + ChatColor.WHITE + page);
        } catch (Exception | LinkageError ignored) {
            // A tooltip must never fail to render because the route could not be worked out.
        }

        return lore;
    }

    @Nonnull
    private static String route(@Nonnull Player p, @Nonnull SlimefunItem item, @Nonnull ItemGroup group) {
        String groupName = group.getDisplayName(p);

        if (!isCategorized(p)) {
            // Classic: an addon owns one menu, and its groups sit inside it. Core's groups are top-level.
            return item.getAddon() == null || "slimefun".equals(group.getKey().getNamespace())
                ? groupName
                : item.getAddon().getName() + ARROW + groupName;
        }

        String category = ChatColor.translateAlternateColorCodes('&',
            CategoryMenuBuilder.resolveCategoryLabel(p, group, Slimefun.getGuideCategories()));

        // Categorized splits an addon's items into a "<Addon> <Type>" section under the category; core's
        // own groups keep their own name there.
        return item.getAddon() == null || "slimefun".equals(group.getKey().getNamespace())
            ? category + ARROW + groupName
            : category + ARROW + item.getAddon().getName() + " " + groupName;
    }

    /** Which page of its group the item is drawn on, counting from 1. */
    private static int pageOf(@Nonnull Player p, @Nonnull SlimefunItem item, @Nonnull ItemGroup group) {
        List<SlimefunItem> items = group.getItems();

        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) != null && items.get(i).getId().equals(item.getId())) {
                return (i / PAGE_SIZE) + 1;
            }
        }

        return 1;
    }

    private static boolean isCategorized(@Nonnull Player p) {
        try {
            return SlimefunGuideSettings.isMainMenuCategorized(p);
        } catch (Exception | LinkageError ignored) {
            // Matches the guide's own default when the setting cannot be read.
            return true;
        }
    }
}
