package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.cryptomorin.xseries.XMaterial;

import io.github.thebusybiscuit.slimefun5.api.events.PlayerLanguageChangeEvent;
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
public final class VanillaTranslationService implements Listener {

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

    private final Set<String> scheduled = ConcurrentHashMap.newKeySet();

    /**
     * Legacy (1.8-1.12) enum names that stood for several data variants and are absent from the tables.
     *
     * @implNote A legacy name with data variants carries no variant information, so remapping it through
     *           XSeries guesses one (for example {@code LOG} becomes birch). A generic correct name beats a
     *           specific wrong one, so these skip the remap and fall to {@link ChatUtils#humanize}.
     */
    private static final Set<String> LEGACY_VARIANT_PARENTS = new HashSet<>(Arrays.asList(
        "log", "wood", "wool", "stained_clay", "stained_glass", "carpet", "sapling", "leaves", "step",
        "ink_sack", "double_plant", "skull", "banner", "long_grass", "stained_glass_pane", "cobble_wall",
        "monster_eggs", "smooth_brick", "wood_step", "double_step", "raw_fish", "cooked_fish",
        "skull_item", "leaves_2", "log_2", "wood_double_step"));

    /** Loads English, which every other language falls back to. */
    public void loadBundledEnglish() {
        ensureLoaded("en");
    }

    /**
     * Registers this service's own join and language-change listener, and loads the server default.
     *
     * @implNote Its own listener, rather than being called from {@code PacketTranslationService}'s
     *           handlers, so the two services stay independent and nothing else has to remember to call it.
     */
    public void listen(@Nonnull JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);

        Language fallback = Slimefun.getLocalization().getDefaultLanguage();

        if (fallback != null) {
            ensureLoaded(fallback.getId());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        ensureLoadedFor(e.getPlayer());
    }

    @EventHandler
    public void onLanguageChange(PlayerLanguageChangeEvent e) {
        // The new language is only applied after this event resolves, so re-resolve one tick later.
        Player player = e.getPlayer();
        Slimefun.runSync(() -> ensureLoadedFor(player), 1L);
    }

    private void ensureLoadedFor(@Nonnull Player player) {
        String language = languageOf(player);

        if (language != null) {
            ensureLoaded(language);
        }
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
        invalidateResolvedNames();
    }

    /**
     * @implNote A newly loaded table invalidates names already resolved from the fallback, which would
     *           otherwise stick until restart. Guarded like {@code ItemTranslationService#vanillaName}
     *           because load also runs under unit tests with no plugin instance.
     */
    private static void invalidateResolvedNames() {
        if (Slimefun.instance() != null) {
            ItemTranslationService items = Slimefun.getItemTranslationService();
            items.clearFamilyCache();
            items.clearRenderCache();
        }
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

        if (found == null && kind == Kind.MATERIAL && !LEGACY_VARIANT_PARENTS.contains(direct)) {
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

        if (tables == null) {
            scheduleLoad(languageId);
            return null;
        }

        return tables.get(kind).get(path);
    }

    /**
     * Queues one main-thread load for a language a lookup found missing.
     *
     * @implNote Lookups run on the Netty thread and must not do file I/O, so this only schedules. The
     *           English fallback is returned for this call and {@code invalidateResolvedNames} makes the
     *           real names visible on the next render. {@link #scheduled} bounds it to once per language.
     */
    private void scheduleLoad(@Nonnull String languageId) {
        if (Slimefun.instance() == null || attempted.contains(languageId) || !scheduled.add(languageId)) {
            return;
        }

        Slimefun.runSync(() -> ensureLoaded(languageId));
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
