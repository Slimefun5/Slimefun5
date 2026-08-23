package io.github.thebusybiscuit.slimefun5.core.guide.categories;

import javax.annotation.Nonnull;

import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun5.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;

/**
 * One addon's slice of a guide category in the categorized layout: the "&lt;Addon&gt; &lt;Type&gt;" tile.
 * <p>
 * Carries which addon and category it represents so the guide can draw that addon's info widgets on its
 * bottom row. The tile is otherwise a plain {@link ItemGroup} holding the addon's items of that type.
 */
public class AddonSectionItemGroup extends ItemGroup {

    private final String addonName;
    private final String categoryId;

    public AddonSectionItemGroup(@Nonnull NamespacedKey key, @Nonnull ItemStack icon, @Nonnull String addonName, @Nonnull String categoryId) {
        super(key, icon);
        this.addonName = addonName;
        this.categoryId = categoryId;
    }

    @Nonnull
    public String getAddonName() {
        return addonName;
    }

    @Nonnull
    public String getCategoryId() {
        return categoryId;
    }
}
