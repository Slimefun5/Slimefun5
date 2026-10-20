package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import org.bukkit.ChatColor;

import io.github.thebusybiscuit.slimefun5.api.items.ItemTier;

/**
 * Composes an item name from its plain text and its {@link ItemTier}: the name in the tier's style and a
 * trailing {@code (qualifier)} in grey, whatever codes the translation itself carries.
 */
public final class ItemNameStyle {

    private static final Pattern TRAILING_QUALIFIER = Pattern.compile("^(.*\\S)\\s*\\(([^()]+)\\)$");
    private static final Pattern RUNS_OF_SPACE = Pattern.compile("\\s+");

    private ItemNameStyle() {}

    @Nonnull
    public static String render(@Nonnull ItemTier tier, @Nonnull String rawName) {
        String plain = plainText(rawName);
        Matcher qualified = TRAILING_QUALIFIER.matcher(plain);

        if (!qualified.matches()) {
            return tier.getStyle() + plain;
        }

        return tier.getStyle() + qualified.group(1) + ChatColor.GRAY + " (" + qualified.group(2).trim() + ")";
    }

    /** The name with every {@code &} and {@code §} code removed and its spacing collapsed. */
    @Nonnull
    public static String plainText(@Nonnull String rawName) {
        String stripped = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', rawName));
        return RUNS_OF_SPACE.matcher(stripped).replaceAll(" ").trim();
    }
}
