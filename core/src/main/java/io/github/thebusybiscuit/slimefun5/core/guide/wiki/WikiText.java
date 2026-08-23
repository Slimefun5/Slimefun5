package io.github.thebusybiscuit.slimefun5.core.guide.wiki;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.cryptomorin.xseries.XMaterial;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun5.core.services.localization.Language;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;

/**
 * Holds authored, human-written explanation lines for the in-game wiki.
 *
 * Lines are authored per item id (and per mechanic-hub topic) in bundled YAML resources
 * (wiki/items.yml, wiki/mechanics.yml) and may also be supplied by addons at runtime.
 * When no authored text exists for an item, a generic auto-generated fallback is produced.
 */
public final class WikiText {

    private static final String FALLBACK_ITEM_KEY = "guide.wiki.fallback.item";
    private static final String FALLBACK_RECIPE_KEY = "guide.wiki.fallback.recipe";

    private final Map<String, List<String>> itemLines = new HashMap<>();
    private final Map<String, List<String>> mechanicLines = new HashMap<>();
    private final Map<String, List<String>> topicItems = new HashMap<>();
    private final List<WikiTopic> topics = new ArrayList<>();

    /** Per-language overrides for {@link #itemLines}/{@link #mechanicLines}: langId -> id -> lines. */
    private final Map<String, Map<String, List<String>>> itemLinesByLanguage = new HashMap<>();
    private final Map<String, Map<String, List<String>>> mechanicLinesByLanguage = new HashMap<>();

    /** guide.wiki.fallback.item / .recipe, per language id, loaded straight from messages.yml. */
    private final Map<String, String> fallbackItemMessage = new HashMap<>();
    private final Map<String, String> fallbackRecipeMessage = new HashMap<>();

    /** Stores authored explanation lines for the given item id. */
    public synchronized void set(@Nonnull String id, @Nonnull List<String> lines) {
        itemLines.put(id, new ArrayList<>(lines));
    }

    /** Stores authored explanation lines for the given mechanic-hub topic. */
    public synchronized void setMechanic(@Nonnull String id, @Nonnull List<String> lines) {
        mechanicLines.put(id, new ArrayList<>(lines));
    }

    /** Whether authored lines exist for the given item id. */
    public synchronized boolean has(@Nonnull String id) {
        return itemLines.containsKey(id);
    }

    /**
     * Returns authored explanation lines for the given item, or a generic auto-generated
     * fallback derived from the item's group and recipe type if no authored lines exist.
     */
    @Nonnull
    public synchronized List<String> get(@Nonnull SlimefunItem item) {
        return get(item, null);
    }

    /**
     * Returns authored explanation lines for the given item in {@code languageId}, falling back
     * per-key to the English entry, and finally to a generic auto-generated (but still localized)
     * fallback derived from the item's group and recipe type if no authored lines exist in either.
     *
     * @param languageId
     *            the viewing player's language id, or null to use English/the server default
     */
    @Nonnull
    public synchronized List<String> get(@Nonnull SlimefunItem item, @Nullable String languageId) {
        List<String> localized = lookup(itemLinesByLanguage, languageId, item.getId());

        if (localized != null) {
            return new ArrayList<>(localized);
        }

        List<String> authored = itemLines.get(item.getId());

        if (authored != null) {
            return new ArrayList<>(authored);
        }

        return buildFallback(item, languageId);
    }

    /** Returns authored mechanic-hub lines for the topic, or an empty list. */
    @Nonnull
    public synchronized List<String> getMechanic(@Nonnull String id) {
        return getMechanic(id, null);
    }

    /**
     * Returns authored mechanic-hub lines for the topic in {@code languageId}, falling back per-key
     * to the English entry, and finally to an empty list if neither has an entry for this topic.
     */
    @Nonnull
    public synchronized List<String> getMechanic(@Nonnull String id, @Nullable String languageId) {
        List<String> localized = lookup(mechanicLinesByLanguage, languageId, id);

        if (localized != null) {
            return new ArrayList<>(localized);
        }

        List<String> authored = mechanicLines.get(id);

        if (authored != null) {
            return new ArrayList<>(authored);
        }

        return Collections.emptyList();
    }

    @Nullable
    private static List<String> lookup(@Nonnull Map<String, Map<String, List<String>>> byLanguage, @Nullable String languageId, @Nonnull String id) {
        if (languageId == null) {
            return null;
        }

        Map<String, List<String>> perLanguage = byLanguage.get(languageId);
        return perLanguage != null ? perLanguage.get(id) : null;
    }

    /** Stores the list of relevant item ids shown alongside a topic guide. */
    public synchronized void setTopicItems(@Nonnull String topicId, @Nonnull List<String> itemIds) {
        topicItems.put(topicId, new ArrayList<>(itemIds));
    }

    /** Returns the item ids relevant to a topic (rendered as clickable icons), or an empty list. */
    @Nonnull
    public synchronized List<String> getTopicItems(@Nonnull String topicId) {
        List<String> ids = topicItems.get(topicId);

        if (ids != null) {
            return new ArrayList<>(ids);
        }

        return Collections.emptyList();
    }

    /**
     * Loads the bundled wiki resources from the jar. Missing resources are logged and skipped
     * rather than treated as fatal, mirroring the defensive IO handling used by InstallState.
     */
    public void loadBundled() {
        loadResource("/wiki/items.yml", itemLines);
        loadResource("/wiki/mechanics.yml", mechanicLines);
        loadResource("/wiki/topic-items.yml", topicItems);
        loadLanguageOverrides();
        loadFallbackMessages();
        loadTopics();
    }

    /**
     * Loads optional per-language wiki-body overrides ({@code /wiki/<langId>/items.yml} and
     * {@code /wiki/<langId>/mechanics.yml}) for every loaded {@link Language}. A language that ships
     * neither file is simply skipped - this is a clean no-op while no addon/core ships such a file.
     */
    private void loadLanguageOverrides() {
        for (Language language : Slimefun.getLocalization().getLanguages()) {
            String langId = language.getId();
            loadLanguageResource("/wiki/" + langId + "/items.yml", langId, itemLinesByLanguage);
            loadLanguageResource("/wiki/" + langId + "/mechanics.yml", langId, mechanicLinesByLanguage);
        }
    }

    private void loadLanguageResource(@Nonnull String path, @Nonnull String langId, @Nonnull Map<String, Map<String, List<String>>> target) {
        InputStream stream = Slimefun.class.getResourceAsStream(path);

        if (stream == null) {
            return; // no override shipped for this language - expected for every language today
        }

        loadLanguageResource(stream, langId, target, path);
    }

    private synchronized void loadLanguageResource(@Nonnull InputStream stream, @Nonnull String langId, @Nonnull Map<String, Map<String, List<String>>> target) {
        loadLanguageResource(stream, langId, target, "<test>");
    }

    private synchronized void loadLanguageResource(@Nonnull InputStream stream, @Nonnull String langId, @Nonnull Map<String, Map<String, List<String>>> target, @Nonnull String sourceForLogging) {
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            Map<String, List<String>> perLanguage = target.computeIfAbsent(langId, k -> new HashMap<>());

            for (String key : config.getKeys(false)) {
                perLanguage.put(key, new ArrayList<>(config.getStringList(key)));
            }
        } catch (RuntimeException e) {
            Slimefun.logger().log(Level.WARNING, "Failed to load wiki language override {0}: {1}", new Object[] { sourceForLogging, e.getMessage() });
        }
    }

    /** Loads the guide.wiki.fallback.item/.recipe messages for every loaded {@link Language}. */
    private void loadFallbackMessages() {
        for (Language language : Slimefun.getLocalization().getLanguages()) {
            InputStream stream = Slimefun.class.getResourceAsStream("/languages/" + language.getId() + "/messages.yml");

            if (stream != null) {
                loadFallbackMessage(language.getId(), stream);
            }
        }
    }

    private synchronized void loadFallbackMessage(@Nonnull String langId, @Nonnull InputStream stream) {
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String item = config.getString(FALLBACK_ITEM_KEY);
            String recipe = config.getString(FALLBACK_RECIPE_KEY);

            if (item != null) {
                fallbackItemMessage.put(langId, item);
            }

            if (recipe != null) {
                fallbackRecipeMessage.put(langId, recipe);
            }
        } catch (RuntimeException e) {
            Slimefun.logger().log(Level.WARNING, "Failed to load wiki fallback messages for {0}: {1}", new Object[] { langId, e.getMessage() });
        }
    }

    // Package-private seams for headless tests: Slimefun.getLocalization().getLanguages() is always
    // empty under the MockBukkit unit-test harness (see Slimefun#onUnitTestStart), so loadBundled()
    // alone never populates these per-language maps there.
    void loadItemLanguageOverrideForTest(@Nonnull String langId, @Nonnull InputStream stream) {
        loadLanguageResource(stream, langId, itemLinesByLanguage);
    }

    void loadMechanicLanguageOverrideForTest(@Nonnull String langId, @Nonnull InputStream stream) {
        loadLanguageResource(stream, langId, mechanicLinesByLanguage);
    }

    void loadFallbackMessagesForTest(@Nonnull String langId, @Nonnull InputStream stream) {
        loadFallbackMessage(langId, stream);
    }

    /** Registers a guide topic shown on the wiki home. Addons may call this to add their own topics. */
    public synchronized void registerTopic(@Nonnull WikiTopic topic) {
        for (WikiTopic existing : topics) {
            if (existing.getId().equals(topic.getId())) {
                return;
            }
        }

        topics.add(topic);
    }

    /** All registered guide topics, in registration order (core first, then addons). */
    @Nonnull
    public synchronized List<WikiTopic> getTopics() {
        return new ArrayList<>(topics);
    }

    /** The topics shipped by one addon, in registration order. Empty if it ships no wiki content. */
    @Nonnull
    public synchronized List<WikiTopic> getTopics(@Nonnull String addon) {
        List<WikiTopic> owned = new ArrayList<>();

        for (WikiTopic topic : topics) {
            if (addon.equals(topic.getAddon())) {
                owned.add(topic);
            }
        }

        return owned;
    }

    /**
     * The topics one addon filed under a category. A topic with no category counts for every category, so
     * an addon that never categorises its guides still shows them all.
     */
    @Nonnull
    public synchronized List<WikiTopic> getTopics(@Nonnull String addon, @Nullable String category) {
        List<WikiTopic> owned = new ArrayList<>();

        for (WikiTopic topic : topics) {
            if (!addon.equals(topic.getAddon())) {
                continue;
            }

            if (category == null || topic.getCategory() == null || category.equals(topic.getCategory())) {
                owned.add(topic);
            }
        }

        return owned;
    }

    /**
     * Loads an addon's bundled wiki content: {@code /wiki/topics.yml} (its own guide topics),
     * {@code /wiki/items.yml}, {@code /wiki/mechanics.yml} and {@code /wiki/topic-items.yml}, plus any
     * {@code /wiki/<langId>/...} overrides. Mirrors
     * {@code ItemTranslationService#registerTranslations(JavaPlugin)}: an addon that ships none of these
     * is a silent no-op.
     *
     * @param addon
     *            The addon whose jar to read
     */
    public void registerWiki(@Nonnull JavaPlugin addon) {
        loadResource(addon.getResource("wiki/items.yml"), itemLines, addon.getName());
        loadResource(addon.getResource("wiki/mechanics.yml"), mechanicLines, addon.getName());
        loadResource(addon.getResource("wiki/topic-items.yml"), topicItems, addon.getName());
        loadTopics(addon.getResource("wiki/topics.yml"), addon.getName());

        for (Language language : Slimefun.getLocalization().getLanguages()) {
            String langId = language.getId();
            InputStream items = addon.getResource("wiki/" + langId + "/items.yml");
            InputStream mechanics = addon.getResource("wiki/" + langId + "/mechanics.yml");

            if (items != null) {
                loadLanguageResource(items, langId, itemLinesByLanguage, addon.getName());
            }

            if (mechanics != null) {
                loadLanguageResource(mechanics, langId, mechanicLinesByLanguage, addon.getName());
            }
        }
    }

    /** Loads the fixed set of core Slimefun guide topics from the bundled {@code /wiki/topics.yml}. */
    private void loadTopics() {
        InputStream stream = Slimefun.class.getResourceAsStream("/wiki/topics.yml");

        if (stream == null) {
            Slimefun.logger().log(Level.WARNING, "Bundled wiki resource was not found: {0}", "/wiki/topics.yml");
            return;
        }

        loadTopics(stream, null);
    }

    /** Registers the topics in one {@code topics.yml}, attributed to {@code addon} ({@code null} = core). */
    private void loadTopics(@Nullable InputStream stream, @Nullable String addon) {
        if (stream == null) {
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));

            for (String id : config.getKeys(false)) {
                registerTopic(readTopic(config, id, addon));
            }
        } catch (RuntimeException e) {
            Slimefun.logger().log(Level.WARNING, "Failed to load wiki topics from {0}: {1}", new Object[] { addon != null ? addon : "Slimefun", e.getMessage() });
        }
    }

    @Nonnull
    private WikiTopic readTopic(@Nonnull YamlConfiguration config, @Nonnull String id, @Nullable String addon) {
        String title = config.getString(id + ".title", id);
        String summary = config.getString(id + ".summary", "");
        String iconName = config.getString(id + ".icon", "PAPER");

        return new WikiTopic(id, title, resolveIcon(id, iconName), summary, addon, config.getString(id + ".category"));
    }

    @Nonnull
    private XMaterial resolveIcon(@Nonnull String topicId, @Nonnull String iconName) {
        Optional<XMaterial> material = XMaterial.matchXMaterial(iconName);

        if (!material.isPresent()) {
            Slimefun.logger().log(Level.WARNING, "Unknown wiki topic icon {0} for topic {1}", new Object[] { iconName, topicId });
            return XMaterial.PAPER;
        }

        return material.get();
    }

    private void loadResource(@Nonnull String path, @Nonnull Map<String, List<String>> target) {
        InputStream stream = Slimefun.class.getResourceAsStream(path);

        if (stream == null) {
            Slimefun.logger().log(Level.WARNING, "Bundled wiki resource was not found: {0}", path);
            return;
        }

        loadResource(stream, target, path);
    }

    /** Merges one wiki body file into {@code target}. A {@code null} stream means the source ships none. */
    private synchronized void loadResource(@Nullable InputStream stream, @Nonnull Map<String, List<String>> target, @Nonnull String source) {
        if (stream == null) {
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));

            for (String key : config.getKeys(false)) {
                target.put(key, new ArrayList<>(config.getStringList(key)));
            }
        } catch (RuntimeException e) {
            Slimefun.logger().log(Level.WARNING, "Failed to load wiki resource from {0}: {1}", new Object[] { source, e.getMessage() });
        }
    }

    /** Builds a short, generic explanation from the item's group and recipe type, in {@code languageId}. */
    @Nonnull
    private List<String> buildFallback(@Nonnull SlimefunItem item, @Nullable String languageId) {
        List<String> lines = new ArrayList<>();

        NamespacedKey groupKey = item.getItemGroup().getKey();
        String groupName = groupKey != null ? groupKey.getKey() : "Slimefun";
        lines.add(fallbackMessage(fallbackItemMessage, languageId).replace("%group%", groupName));

        String recipeName = readRecipeName(item.getRecipeType());

        if (recipeName != null) {
            lines.add(fallbackMessage(fallbackRecipeMessage, languageId).replace("%recipe%", recipeName));
        }

        return lines;
    }

    /** {@code languageId}'s message, else English, else a "missing key" marker (never null). */
    @Nonnull
    private static String fallbackMessage(@Nonnull Map<String, String> byLanguage, @Nullable String languageId) {
        String message = languageId != null ? byLanguage.get(languageId) : null;

        if (message != null) {
            return message;
        }

        String english = byLanguage.get("en");
        return english != null ? english : "! Missing wiki fallback message";
    }

    /** Null-safe extraction of a recipe type's key for display. */
    private String readRecipeName(RecipeType recipeType) {
        if (recipeType == null) {
            return null;
        }

        NamespacedKey key = recipeType.getKey();
        return key != null ? key.getKey() : null;
    }
}
