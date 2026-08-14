package io.github.thebusybiscuit.slimefun5.core.guide;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nonnull;

import org.bukkit.entity.Player;

import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.PdcCompat;

/**
 * Per-player set of addon ids (category {@code NamespacedKey} namespaces) hidden from that player's guide.
 * Persisted via {@link PdcCompat} as a single comma-separated string, so it works on every Minecraft
 * version (PDC on 1.14+, legacy YAML fallback on 1.8-1.13). Default: empty (everything visible).
 */
public final class AddonVisibility {

    private static final NamespacedKey KEY = new NamespacedKey(Slimefun.instance(), "guide_hidden_addons");

    /**
     * When set for the current thread, {@link #isHidden} reports nothing hidden. The guide sets this for a
     * single re-render when applying the player's visibility would leave the menu completely empty (a
     * corrupt/stale set that hides everything) - the guide must never be blank because of visibility.
     */
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private AddonVisibility() {}

    @Nonnull
    public static Set<String> getHidden(@Nonnull Player p) {
        String raw = PdcCompat.getString(p, KEY);

        if (raw == null || raw.isEmpty()) {
            return new HashSet<>();
        }

        return new HashSet<>(Arrays.asList(raw.split(",")));
    }

    public static boolean isHidden(@Nonnull Player p, @Nonnull String addonId) {
        if (BYPASS.get()) {
            return false;
        }

        return getHidden(p).contains(addonId.toLowerCase(Locale.ROOT));
    }

    /**
     * Runs {@code action} with visibility filtering disabled on this thread, then restores it. Used by the
     * guide to re-render "show everything" when the player's visibility set would otherwise blank the menu.
     */
    public static void runWithoutFiltering(@Nonnull Runnable action) {
        BYPASS.set(Boolean.TRUE);

        try {
            action.run();
        } finally {
            BYPASS.set(Boolean.FALSE);
        }
    }

    /**
     * Clears a player's hidden-addon set (everything becomes visible again). Called to repair a set that
     * hides all content, which the visibility menu never produces (it keeps at least one shown).
     */
    public static void clear(@Nonnull Player p) {
        PdcCompat.setString(p, KEY, "");
    }

    public static void setHidden(@Nonnull Player p, @Nonnull String addonId, boolean hidden) {
        setHidden(p, Collections.singleton(addonId), hidden);
    }

    /**
     * Bulk variant of {@link #setHidden(Player, String, boolean)} - one PDC write for any number of
     * addons, backing the addon-visibility menu's "show all" / "hide all" buttons.
     */
    public static void setHidden(@Nonnull Player p, @Nonnull Collection<String> addonIds, boolean hidden) {
        Set<String> hiddenSet = getHidden(p);

        for (String addonId : addonIds) {
            String id = addonId.toLowerCase(Locale.ROOT);

            if (hidden) {
                hiddenSet.add(id);
            } else {
                hiddenSet.remove(id);
            }
        }

        PdcCompat.setString(p, KEY, String.join(",", hiddenSet));
    }

    /**
     * Hides every addon in {@code allAddonIds} except {@code addonId} in a single write - the "only this
     * addon" solo action. Replaces the hidden set entirely rather than merging with the previous one, so a
     * player can never end up with more than the requested addon shown.
     */
    public static void solo(@Nonnull Player p, @Nonnull Collection<String> allAddonIds, @Nonnull String addonId) {
        String keep = addonId.toLowerCase(Locale.ROOT);
        Set<String> hiddenSet = new HashSet<>();

        for (String id : allAddonIds) {
            String lower = id.toLowerCase(Locale.ROOT);

            if (!lower.equals(keep)) {
                hiddenSet.add(lower);
            }
        }

        PdcCompat.setString(p, KEY, String.join(",", hiddenSet));
    }
}
