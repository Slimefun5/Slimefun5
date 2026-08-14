package io.github.thebusybiscuit.slimefun5.core.guide.wiki;

import java.util.Locale;

import javax.annotation.Nonnull;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * Resolves the "view in wiki" URL for a {@link SlimefunItem}. The wiki is a single central site with a
 * per-plugin section for every one of our plugins (content authored in each plugin's own GitHub wiki). The
 * URL is derived uniformly from the owning addon and the item id, for every item, so the URL shape is
 * always consistent - a legacy per-item wiki page (see {@link SlimefunItem#getWikipage()}, deprecated) is
 * NOT consulted, since it used to point at the old GitHub wiki instead of this site. The template is
 * config-overridable ({@code guide.wiki-url-template}) so the real domain can be set once it exists.
 */
public final class WikiLinks {

    private static final String DEFAULT_TEMPLATE = "https://slimefun5.github.io/wiki/%plugin%/%id%";

    private WikiLinks() {}

    @Nonnull
    public static String urlFor(@Nonnull SlimefunItem item) {
        String template = Slimefun.getCfg().getString("guide.wiki-url-template");

        if (template == null || template.isEmpty()) {
            template = DEFAULT_TEMPLATE;
        }

        String plugin = item.getAddon() != null ? slug(item.getAddon().getName()) : "slimefun";
        String id = slug(item.getId());

        return template.replace("%plugin%", plugin).replace("%id%", id);
    }

    @Nonnull
    private static String slug(@Nonnull String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
    }
}
