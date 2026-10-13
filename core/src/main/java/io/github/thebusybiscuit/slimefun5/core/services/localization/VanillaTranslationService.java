package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    private final Set<String> attempted = ConcurrentHashMap.newKeySet();

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
        if (byLanguage.containsKey(languageId) || !attempted.add(languageId)) {
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
        String direct = key.toLowerCase(Locale.ROOT);
        String found = lookupWithFallback(languageId, kind, direct);

        if (found == null && kind == Kind.MATERIAL) {
            String modern = modernPath(key);
            found = modern.equals(direct) ? null : lookupWithFallback(languageId, kind, modern);
        }

        return found != null ? found : ChatUtils.humanize(key);
    }

    @Nullable
    private String lookupWithFallback(@Nullable String languageId, @Nonnull Kind kind, @Nonnull String path) {
        String found = languageId == null ? null : lookup(languageId, kind, path);
        return found != null ? found : lookup("en", kind, path);
    }

    @Nullable
    private String lookup(@Nonnull String languageId, @Nonnull Kind kind, @Nonnull String path) {
        Map<Kind, Map<String, String>> tables = byLanguage.get(languageId);
        return tables == null ? null : tables.get(kind).get(path);
    }

    /**
     * @implNote Tried only after a direct table hit fails: XSeries can remap an already-modern name to a
     *           different one (for example {@code map}), so it must never override an exact key. The
     *           tables hold MODERN paths only, so this is what lets one table serve 1.8 to 26.x.
     */
    @Nonnull
    private static String modernPath(@Nonnull String key) {
        Optional<XMaterial> matched = XMaterial.matchXMaterial(key);
        return matched.isPresent() ? matched.get().name().toLowerCase(Locale.ROOT) : key.toLowerCase(Locale.ROOT);
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
