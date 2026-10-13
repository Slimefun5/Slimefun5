package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import com.cryptomorin.xseries.XMaterial;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.utils.ChatUtils;

/**
 * Resolves vanilla entity and material names for one viewer, from
 * {@code languages/<lang>/vanilla.yml}.
 * <p>
 * Slimefun translates the template of a dynamic item family but historically title-cased the captured
 * segment, so {@code %MOB%_SOUL_JAR} rendered a translated name around an English mob. This supplies
 * that missing half.
 *
 * @implNote The language-id form is primary and the {@link Player} form is the convenience, not the
 *           other way round: {@code ItemTranslationService.resolveFamily} is reached from the packet
 *           rewriter on a Netty thread, where resolving a viewer's language reads their persistent
 *           data. For the same reason a lookup NEVER loads a table - see {@link #ensureLoaded}.
 */
public final class VanillaTranslationService {

    /** Which table a key belongs to. Explicit, because 49 names exist in both. */
    public enum Kind {
        ENTITY("entity"),
        MATERIAL("material");

        private final String section;

        Kind(String section) {
            this.section = section;
        }
    }

    /** Outer: {@code ConcurrentHashMap} (no null values). Inner maps: unmodifiable, built before publish. */
    private final Map<String, Map<Kind, Map<String, String>>> byLanguage = new ConcurrentHashMap<>();

    /** Loads English, which every other language falls back to. */
    public void loadBundledEnglish() {
        ensureLoaded("en");
    }

    /**
     * Loads one language's table if it is not already present.
     *
     * @implNote Must be called from the main thread only. A lookup deliberately does not call this,
     *           because the lookup path runs on the Netty event loop and must not perform file I/O.
     */
    public void ensureLoaded(@Nonnull String languageId) {
        if (byLanguage.containsKey(languageId)) {
            return;
        }

        try (InputStream stream = Slimefun.class.getResourceAsStream("/languages/" + languageId + "/vanilla.yml")) {
            if (stream != null) {
                load(languageId, stream);
            }
        } catch (Exception ignored) {
            // A missing or broken table must leave every lookup working on the English fallback.
        }
    }

    private void load(@Nonnull String languageId, @Nonnull InputStream stream) {
        Map<Kind, Map<String, String>> tables = new HashMap<>();

        try {
            YamlConfiguration config =
                YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));

            for (Kind kind : Kind.values()) {
                Map<String, String> names = new HashMap<>();

                if (config.isConfigurationSection(kind.section)) {
                    for (String key : config.getConfigurationSection(kind.section).getKeys(false)) {
                        String value = config.getString(kind.section + "." + key);

                        if (value != null && !value.isEmpty()) {
                            names.put(key.toLowerCase(Locale.ROOT), value);
                        }
                    }
                }

                tables.put(kind, Collections.unmodifiableMap(names));
            }
        } catch (RuntimeException e) {
            for (Kind kind : Kind.values()) {
                tables.putIfAbsent(kind, Collections.<String, String>emptyMap());
            }
        }

        byLanguage.put(languageId, Collections.unmodifiableMap(tables));
    }

    /** Test seam: {@code Slimefun.getLocalization().getLanguages()} is always empty under MockBukkit. */
    void loadForTest(@Nonnull String languageId, @Nonnull InputStream stream) {
        load(languageId, stream);
    }

    @Nonnull
    public String entity(@Nullable String languageId, @Nonnull String entityKey) {
        return name(languageId, Kind.ENTITY, entityKey);
    }

    @Nonnull
    public String material(@Nullable String languageId, @Nonnull String materialKey) {
        return name(languageId, Kind.MATERIAL, materialKey);
    }

    /**
     * @return the name in this language, else English, else {@link ChatUtils#humanize} output - which is
     *         exactly what every call site rendered before this service existed, so a gap cannot regress
     *         any screen
     */
    @Nonnull
    public String name(@Nullable String languageId, @Nonnull Kind kind, @Nonnull String key) {
        String path = normalise(kind, key);

        if (languageId != null) {
            String found = lookup(languageId, kind, path);

            if (found != null) {
                return found;
            }
        }

        String english = lookup("en", kind, path);
        return english != null ? english : ChatUtils.humanize(key);
    }

    @Nullable
    private String lookup(@Nonnull String languageId, @Nonnull Kind kind, @Nonnull String path) {
        Map<Kind, Map<String, String>> tables = byLanguage.get(languageId);
        return tables == null ? null : tables.get(kind).get(path);
    }

    /**
     * @implNote The tables hold MODERN Minecraft paths only, so a legacy enum name is mapped through
     *           XSeries first. This is what keeps one table valid from 1.8 to 26.x instead of needing a
     *           per-version table.
     */
    @Nonnull
    private static String normalise(@Nonnull Kind kind, @Nonnull String key) {
        String lower = key.toLowerCase(Locale.ROOT);

        if (kind != Kind.MATERIAL) {
            return lower;
        }

        Optional<XMaterial> matched = XMaterial.matchXMaterial(key);
        return matched.isPresent() ? matched.get().name().toLowerCase(Locale.ROOT) : lower;
    }

    @Nonnull
    public String entity(@Nullable Player viewer, @Nonnull EntityType type) {
        return entity(languageOf(viewer), type.name());
    }

    @Nonnull
    public String material(@Nullable Player viewer, @Nonnull Material material) {
        return material(languageOf(viewer), material.name());
    }

    /** Main thread only: reads the viewer's language. */
    @Nullable
    private static String languageOf(@Nullable Player viewer) {
        if (viewer == null) {
            return null;
        }

        Language language = Slimefun.getLocalization().getLanguage(viewer);
        return language == null ? null : language.getId();
    }

}
