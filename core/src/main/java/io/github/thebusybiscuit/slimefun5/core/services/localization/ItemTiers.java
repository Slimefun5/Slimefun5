package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.configuration.file.YamlConfiguration;

import io.github.thebusybiscuit.slimefun5.api.items.ItemTier;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.implementation.items.VanillaItem;

/**
 * The {@link ItemTier} of every item, declared once per plugin in its bundled {@code item-tiers.yml}
 * ({@code ITEM_ID: rare}) and overridable by the server in {@code plugins/Slimefun/item-tiers.yml}.
 *
 * @implNote Only a plugin that ships the file has its names styled; every other plugin's names render as
 *           authored, so addons adopt the style one at a time instead of all turning white at once.
 */
public final class ItemTiers {

    public static final String FILE_NAME = "item-tiers.yml";

    private final Map<String, ItemTier> declared = new ConcurrentHashMap<>();
    private final Map<String, ItemTier> overridden = new ConcurrentHashMap<>();
    private final Set<String> styledPlugins = ConcurrentHashMap.newKeySet();

    /** Reads one plugin's bundled declarations; a plugin that passes {@code null} keeps its names as authored. */
    public void loadDeclared(@Nonnull String pluginName, @Nullable InputStream stream) {
        if (stream == null) {
            return;
        }

        styledPlugins.add(pluginName);
        read(stream, declared, pluginName);
    }

    public void loadOverrides(@Nonnull File file) {
        if (!file.isFile()) {
            return;
        }

        try (InputStream stream = new java.io.FileInputStream(file)) {
            read(stream, overridden, file.getPath());
        } catch (java.io.IOException e) {
            Slimefun.logger().log(Level.WARNING, "Could not read {0}: {1}", new Object[] { file, e.getMessage() });
        }
    }

    private static void read(@Nonnull InputStream stream, @Nonnull Map<String, ItemTier> into, @Nonnull String source) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));

        for (String id : config.getKeys(false)) {
            ItemTier tier = ItemTier.parse(config.getString(id));

            if (tier == null) {
                Slimefun.logger().log(Level.WARNING, "{0}: {1} names no tier (\"{2}\")", new Object[] { source, id, config.getString(id) });
            } else {
                into.put(id, tier);
            }
        }
    }

    @Nonnull
    public ItemTier tierOf(@Nonnull String itemId) {
        ItemTier tier = overridden.get(itemId);

        if (tier == null) {
            tier = declared.get(itemId);
        }

        return tier != null ? tier : ItemTier.COMMON;
    }

    public boolean isStyled(@Nullable SlimefunItem item) {
        return item != null && !(item instanceof VanillaItem) && styledPlugins.contains(item.getAddon().getName());
    }

    /** Every id any {@code item-tiers.yml} declares, for the check that each one exists. */
    @Nonnull
    public Set<String> declaredIds() {
        Set<String> ids = new java.util.HashSet<>(declared.keySet());
        ids.addAll(overridden.keySet());
        return Collections.unmodifiableSet(ids);
    }

    @Nonnull
    public Set<String> styledPlugins() {
        return Collections.unmodifiableSet(styledPlugins);
    }
}
