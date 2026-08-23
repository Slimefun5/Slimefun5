package io.github.thebusybiscuit.slimefun5.core.guide.menus;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * Which addons declared their own guide menu, and which were folded into one by the guide.
 * <p>
 * An addon gets exactly one entry in the classic layout. It can shape that entry itself by declaring a
 * root here; otherwise the guide builds one around whatever top-level groups the addon registered, which
 * is what keeps the rule true for addons that never migrate (including third-party ones).
 *
 * @see AddonItemGroup
 */
public final class AddonMenuRegistry {

    private final Map<String, AddonItemGroup> declaredRoots = new LinkedHashMap<>();

    /** Addons already warned about relying on the automatic menu, so the warning prints once each. */
    private final Set<String> warnedAddons = new LinkedHashSet<>();

    /**
     * Declares the single guide menu for an addon, replacing any previous declaration.
     *
     * @param root
     *            The addon's root menu; its {@link AddonItemGroup#getAddonName()} is the key
     */
    public void declareRoot(@Nonnull AddonItemGroup root) {
        declaredRoots.put(root.getAddonName(), root);
    }

    /** The menu an addon declared for itself, or {@code null} if it left the guide to build one. */
    @Nullable
    public AddonItemGroup getDeclaredRoot(@Nonnull String addon) {
        return declaredRoots.get(addon);
    }

    public boolean hasDeclaredRoot(@Nonnull String addon) {
        return declaredRoots.containsKey(addon);
    }

    /**
     * Logs, once per addon, that the guide had to fold several top-level groups into one menu because the
     * addon never declared a root.
     *
     * @param addon
     *            The addon that was folded
     * @param groupCount
     *            How many top-level groups it registered
     */
    public void warnAutoWrapped(@Nonnull String addon, int groupCount) {
        if (warnedAddons.add(addon)) {
            Slimefun.logger().warning("[Guide] Addon " + addon + " registers " + groupCount
                + " top-level item groups. The guide folded them into a single menu, since an addon gets"
                + " one entry. Declare the menu yourself via Slimefun.getAddonMenus().declareRoot(..) to"
                + " control its icon, name and ordering.");
        }
    }
}
