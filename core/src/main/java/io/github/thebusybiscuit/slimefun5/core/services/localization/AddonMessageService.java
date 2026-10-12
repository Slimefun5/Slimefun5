package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;

/**
 * Translates an addon's chat and GUI text per viewer, loaded from
 * {@code languages/<lang>/messages.yml} shipped inside the addon's own jar and keyed by that
 * addon's plugin name so two addons may use the same key without colliding.
 *
 * @implNote Unlike core's own {@link SlimefunLocalization}, there is deliberately no {@code en}
 *           entry to fall back to: the English literal already present at the call site *is* the
 *           English source, exactly as {@link MenuTranslationService} treats a
 *           {@link BlockMenuPreset}'s own title. Every accessor therefore takes that literal as a
 *           fallback and returns it unchanged when no override exists, so a missing or malformed
 *           translation degrades to the previous behaviour instead of surfacing a missing-string
 *           marker to a player.
 */
public class AddonMessageService {

    /**
     * Indexed language id -&gt; addon id -&gt; flattened dotted key -&gt; {@link String} or
     * {@link List}.
     *
     * @implNote Flattened and merged rather than holding one {@link YamlConfiguration} per addon,
     *           because a library shaded into an addon (InfinityLib) ships its own translations at
     *           the same in-jar resource path as its consumer. Shadow's {@code relocate} rewrites
     *           Java packages only, never resource paths, so both files must be able to register
     *           into one namespace instead of the second silently replacing the first.
     */
    private final Map<String, Map<String, Map<String, Object>>> byLanguage = new ConcurrentHashMap<>();

    /**
     * Lets an addon contribute its own {@code languages/<lang>/messages.yml} translations. Call this
     * from the addon's {@code onEnable}.
     *
     * @param addon
     *            The addon shipping the translations
     */
    public void registerTranslations(@Nonnull JavaPlugin addon) {
        registerTranslations(addon, "messages.yml");
    }

    /**
     * As {@link #registerTranslations(JavaPlugin)}, but reads a differently named file under
     * {@code languages/<lang>/}. A library shaded into an addon must use this with its own file
     * name, since its resources would otherwise collide with the consuming addon's.
     *
     * The first registration of a key wins, so register the addon's own file before a shaded
     * library's defaults if the addon should be able to override them.
     *
     * @param addon
     *            The plugin the keys belong to
     * @param fileName
     *            The file name under {@code languages/<lang>/}, for example {@code infinitylib.yml}
     */
    public void registerTranslations(@Nonnull JavaPlugin addon, @Nonnull String fileName) {
        for (Language language : Slimefun.getLocalization().getLanguages()) {
            InputStream stream = addon.getResource("languages/" + language.getId() + "/" + fileName);

            if (stream != null) {
                load(language.getId(), addonId(addon), stream);
            }
        }
    }

    /** Package-private test seam: lets tests feed a synthetic messages.yml without bundled resources. */
    void load(@Nonnull String language, @Nonnull String addonId, @Nonnull InputStream stream) {
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            Map<String, Object> merged = byLanguage.computeIfAbsent(language, k -> new ConcurrentHashMap<>()).computeIfAbsent(addonId, k -> new ConcurrentHashMap<>());

            for (String key : config.getKeys(true)) {
                Object value = config.get(key);

                if (value instanceof String || value instanceof List) {
                    merged.putIfAbsent(key, value);
                }
            }
        } catch (RuntimeException e) {
            warnMalformed(language, addonId, e);
        }
    }

    /**
     * @implNote {@link Slimefun#logger()} throws once the plugin instance is gone (before onEnable,
     *           after onDisable, under a unit test), so reaching for it here would let the handler
     *           for a malformed file fail the very registration it exists to keep alive. A lost
     *           diagnostic line is the cheaper half of that trade.
     */
    private static void warnMalformed(@Nonnull String language, @Nonnull String addonId, @Nonnull RuntimeException cause) {
        Slimefun plugin = Slimefun.instance();

        if (plugin != null) {
            plugin.getLogger().log(Level.WARNING, "Failed to load addon messages for {0}/{1}: {2}", new Object[] { addonId, language, cause.getMessage() });
        }
    }

    private static @Nonnull String addonId(@Nonnull JavaPlugin addon) {
        return addon.getName().toLowerCase(Locale.ROOT);
    }

    private @Nullable Object lookup(@Nullable Player p, @Nonnull JavaPlugin addon, @Nonnull String key) {
        Language language = p == null ? null : Slimefun.getLocalization().getLanguage(p);
        return language == null ? null : lookup(language.getId(), addonId(addon), key);
    }

    /** Package-private test seam: the storage lookup, without needing a server to resolve a viewer's language. */
    @Nullable
    Object lookup(@Nonnull String language, @Nonnull String addonId, @Nonnull String key) {
        Map<String, Map<String, Object>> byAddon = byLanguage.get(language);

        if (byAddon == null) {
            return null;
        }

        Map<String, Object> keys = byAddon.get(addonId);
        return keys == null ? null : keys.get(key);
    }

    /**
     * Resolves a single translated line for the given viewer, falling back to the English literal
     * from the call site when this addon ships no override for the viewer's language.
     *
     * @param p
     *            The viewer, or null for console
     * @param addon
     *            The addon owning the key
     * @param key
     *            The dotted key inside the addon's {@code messages.yml}
     * @param fallbackEnglish
     *            The English literal to return when no translation exists
     *
     * @return The translated line, or {@code fallbackEnglish}
     */
    public @Nonnull String getMessage(@Nullable Player p, @Nonnull JavaPlugin addon, @Nonnull String key, @Nonnull String fallbackEnglish) {
        Object translated = lookup(p, addon, key);
        return translated instanceof String ? (String) translated : fallbackEnglish;
    }

    /**
     * The list-valued counterpart of {@link #getMessage(Player, JavaPlugin, String, String)}, used
     * for multi-line lore blocks.
     *
     * @return The translated lines, or {@code fallbackEnglish} when no override exists
     */
    public @Nonnull List<String> getMessages(@Nullable Player p, @Nonnull JavaPlugin addon, @Nonnull String key, @Nonnull List<String> fallbackEnglish) {
        Object translated = lookup(p, addon, key);

        if (!(translated instanceof List) || ((List<?>) translated).isEmpty()) {
            return fallbackEnglish;
        }

        List<String> lines = new ArrayList<>();

        for (Object line : (List<?>) translated) {
            lines.add(String.valueOf(line));
        }

        return lines;
    }

    /**
     * Sends one translated line to a recipient, colourising {@code &}-codes the same way core's own
     * {@link SlimefunLocalization#sendMessage(CommandSender, String)} does. Console recipients get
     * the English literal with colour codes stripped.
     */
    public void sendMessage(@Nonnull CommandSender recipient, @Nonnull JavaPlugin addon, @Nonnull String key, @Nonnull String fallbackEnglish) {
        sendMessage(recipient, addon, key, fallbackEnglish, UnaryOperator.identity());
    }

    /**
     * As {@link #sendMessage(CommandSender, JavaPlugin, String, String)}, but applies
     * {@code function} to the resolved line first so callers can substitute {@code %placeholder%}
     * tokens after translation.
     */
    public void sendMessage(@Nonnull CommandSender recipient, @Nonnull JavaPlugin addon, @Nonnull String key, @Nonnull String fallbackEnglish, @Nonnull UnaryOperator<String> function) {
        if (recipient instanceof Player) {
            Player player = (Player) recipient;
            recipient.sendMessage(ChatColor.translateAlternateColorCodes('&', function.apply(getMessage(player, addon, key, fallbackEnglish))));
        } else {
            recipient.sendMessage(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', function.apply(fallbackEnglish))));
        }
    }
}
