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

    /** Addons whose menu the guide built, reported together once the fold pass finishes. */
    private final Set<String> autoWrapped = new LinkedHashSet<>();

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
     * Records that the guide built an addon's menu for it. Reported by {@link #reportBuiltMenus()} once
     * the whole pass is done, not here: menus are folded one addon at a time, so logging per addon
     * reprinted the growing list on every step.
     *
     * @param addon
     *            The addon that was folded
     * @param groupCount
     *            How many top-level groups it registered
     */
    public void recordAutoWrapped(@Nonnull String addon, int groupCount) {
        autoWrapped.add(addon + " (" + groupCount + ")");
    }

    /**
     * The addons whose menu the guide built, rather than the addon declaring one.
     *
     * @implNote Not logged. Folding is the standard path, the menu is named by the addon and iconed from
     *           the installer catalog either way, so there is nothing for an addon author to act on - the
     *           line was pure boot noise. Kept as state so a declared root still takes precedence and so
     *           this stays answerable if it is ever wanted.
     */
    @Nonnull
    public Set<String> getAutoWrappedAddons() {
        return new LinkedHashSet<>(autoWrapped);
    }
}
