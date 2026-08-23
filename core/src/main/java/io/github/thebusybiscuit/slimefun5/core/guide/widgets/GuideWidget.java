package io.github.thebusybiscuit.slimefun5.core.guide.widgets;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.entity.Player;

import com.cryptomorin.xseries.XMaterial;

import io.github.thebusybiscuit.slimefun5.api.player.PlayerProfile;

/**
 * A functional guide screen an addon registers so the core guide embeds it as a dedicated entry on both
 * layouts (classic + categorized), instead of the addon injecting its own category/custom layout. Meant
 * for genuinely functional UIs (e.g. an advancement tree), NOT item browsing - items are always listed
 * under the shared categories.
 */
public final class GuideWidget {

    /** Where the widget's button sits on the guide's main menu. */
    public enum Position {
        TOP,
        BOTTOM
    }

    /** Opens the widget's screen for a viewer; runs on the main thread from a guide menu click. */
    @FunctionalInterface
    public interface Opener {
        void open(@Nonnull Player player, @Nonnull PlayerProfile profile);
    }

    private final String id;
    private final String defaultName;
    private final XMaterial icon;
    private final int order;
    private final Position position;
    private final Opener opener;
    private final String addon;
    private final String category;

    /** Convenience constructor placing the widget on the bottom row. */
    public GuideWidget(@Nonnull String id, @Nonnull String defaultName, @Nonnull XMaterial icon, int order, @Nonnull Opener opener) {
        this(id, defaultName, icon, order, Position.BOTTOM, opener);
    }

    public GuideWidget(@Nonnull String id, @Nonnull String defaultName, @Nonnull XMaterial icon, int order, @Nonnull Position position, @Nonnull Opener opener) {
        this(id, defaultName, icon, order, position, opener, null, null);
    }

    /**
     * Creates a widget bound to one addon's guide menu.
     *
     * @param addon
     *            The addon whose menu carries this widget, or {@code null} for the guide's main menu
     * @param category
     *            The guide category that shows this widget in the categorized layout. {@code null} keeps
     *            it out of that layout entirely: a categorized view has no addon menu to attach it to, so
     *            an uncategorized addon widget has nowhere to live.
     */
    public GuideWidget(@Nonnull String id, @Nonnull String defaultName, @Nonnull XMaterial icon, int order, @Nonnull Position position, @Nonnull Opener opener, @Nullable String addon, @Nullable String category) {
        this.id = id;
        this.defaultName = defaultName;
        this.icon = icon;
        this.order = order;
        this.position = position;
        this.opener = opener;
        this.addon = addon;
        this.category = category;
    }

    @Nonnull
    public String getId() {
        return id;
    }

    @Nonnull
    public String getDefaultName() {
        return defaultName;
    }

    @Nonnull
    public XMaterial getIcon() {
        return icon;
    }

    public int getOrder() {
        return order;
    }

    @Nonnull
    public Position getPosition() {
        return position;
    }

    /** The addon whose guide menu carries this widget, or {@code null} for the guide's main menu. */
    @Nullable
    public String getAddon() {
        return addon;
    }

    /** The guide category that shows this widget in the categorized layout, or {@code null} for none. */
    @Nullable
    public String getCategory() {
        return category;
    }

    public void open(@Nonnull Player player, @Nonnull PlayerProfile profile) {
        opener.open(player, profile);
    }
}
