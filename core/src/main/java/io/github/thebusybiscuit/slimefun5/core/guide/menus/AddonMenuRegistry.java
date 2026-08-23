package io.github.thebusybiscuit.slimefun5.core.guide.menus;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

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
     * Records that the guide built an addon's menu for it, and reports the set once.
     *
     * @implNote One summary line rather than a warning per addon: folding is the normal path, not a
     *           defect, so eleven warnings on every boot was noise. It stays visible because it is still
     *           the list of addons that have not chosen their own icon and ordering.
     *
     * @param addon
     *            The addon that was folded
     * @param groupCount
     *            How many top-level groups it registered
     */
    public void warnAutoWrapped(@Nonnull String addon, int groupCount) {
        if (!warnedAddons.add(addon + " (" + groupCount + ")")) {
            return;
        }

        Slimefun.logger().log(Level.INFO,
            "[Guide] Built the addon menu for: {0}. Each addon gets one entry; an addon can shape its own"
            + " via Slimefun.getAddonMenus().declareRoot(..).",
            String.join(", ", warnedAddons));
    }
}
