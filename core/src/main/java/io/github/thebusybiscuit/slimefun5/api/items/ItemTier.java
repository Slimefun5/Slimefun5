package io.github.thebusybiscuit.slimefun5.api.items;

import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.ChatColor;

/**
 * How far into progression a {@link SlimefunItem} sits, shown as the colour of its name. The colours follow
 * vanilla's rarity scale, with {@link #ENDGAME} one step above it.
 *
 * @see SlimefunItem#getItemTier()
 */
public enum ItemTier {

    COMMON(ChatColor.WHITE.toString()),
    UNCOMMON(ChatColor.YELLOW.toString()),
    RARE(ChatColor.AQUA.toString()),
    EPIC(ChatColor.LIGHT_PURPLE.toString()),
    ENDGAME(ChatColor.GOLD.toString() + ChatColor.BOLD);

    private final String style;

    ItemTier(@Nonnull String style) {
        this.style = style;
    }

    /** The colour and format codes a name of this tier starts with. */
    public @Nonnull String getStyle() {
        return style;
    }

    /** The tier spelled as it is in {@code item-tiers.yml}, in any case, or {@code null} if none is. */
    public static @Nullable ItemTier parse(@Nullable String value) {
        if (value == null) {
            return null;
        }

        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
