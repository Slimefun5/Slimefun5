package io.github.thebusybiscuit.slimefun5.core.guide.wiki;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.cryptomorin.xseries.XMaterial;

/**
 * A single entry shown on the wiki home: a guide topic. Its text comes from
 * {@link WikiText#getMechanic(String)} and its related items from {@link WikiText#getTopicItems(String)},
 * both keyed by {@link #getId()}. Core registers a fixed set; one is also auto-generated per installed
 * addon, and addons may register their own via {@link WikiText#registerTopic(WikiTopic)}.
 */
public final class WikiTopic {

    private final String id;
    private final String displayName;
    private final XMaterial icon;
    private final String summary;
    private final String addon;

    /** Creates a core topic, owned by Slimefun itself rather than an addon. */
    public WikiTopic(@Nonnull String id, @Nonnull String displayName, @Nonnull XMaterial icon, @Nonnull String summary) {
        this(id, displayName, icon, summary, null);
    }

    public WikiTopic(@Nonnull String id, @Nonnull String displayName, @Nonnull XMaterial icon, @Nonnull String summary, @Nullable String addon) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.summary = summary;
        this.addon = addon;
    }

    @Nonnull
    public String getId() {
        return id;
    }

    @Nonnull
    public String getDisplayName() {
        return displayName;
    }

    @Nonnull
    public XMaterial getIcon() {
        return icon;
    }

    @Nonnull
    public String getSummary() {
        return summary;
    }

    /** The addon that shipped this topic, or {@code null} for a core Slimefun topic. */
    @Nullable
    public String getAddon() {
        return addon;
    }
}
