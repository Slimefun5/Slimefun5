package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import org.apache.commons.lang.Validate;
import org.bukkit.ChatColor;
import io.github.thebusybiscuit.slimefun5.libraries.keys.Keyed;
import org.bukkit.Material;
import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.bakedlibs.dough.common.ChatColors;
import io.github.bakedlibs.dough.config.Config;
import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun5.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.api.researches.Research;
import io.github.thebusybiscuit.slimefun5.api.MinecraftVersion;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.VersionedItemFlag;
import io.github.thebusybiscuit.slimefun5.api.SlimefunBranch;
import io.github.thebusybiscuit.slimefun5.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun5.core.services.LocalizationService;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.ReflectionCompat;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * This is an abstract parent class of {@link LocalizationService}.
 * There is not really much more I can say besides that...
 *
 * @author TheBusyBiscuit
 *
 * @see LocalizationService
 *
 */
public abstract class SlimefunLocalization implements Keyed {

    private final Config defaultConfig;

    protected SlimefunLocalization(@Nonnull Slimefun plugin) {
        this.defaultConfig = new Config(plugin, "messages.yml");
    }

    protected @Nonnull Config getConfig() {
        return defaultConfig;
    }

    /**
     * Saves this Localization to its File
     */
    protected void save() {
        defaultConfig.save();
    }

    /**
     * This returns the chat prefix for our messages.
     * Every message (unless explicitly omitted) will have this
     * prefix prepended.
     *
     * @return The chat prefix
     */
    public @Nonnull String getChatPrefix() {
        return getMessage("prefix");
    }

    /**
     * This method attempts to return the {@link Language} with the given
     * language code.
     *
     * @param id
     *            The language code
     *
     * @return A {@link Language} with the given id or null
     */

    public abstract @Nullable Language getLanguage(@Nonnull String id);

    /**
     * This method returns the currently selected {@link Language} of a {@link Player}.
     *
     * @param p
     *            The {@link Player} to query
     *
     * @return The {@link Language} that was selected by the given {@link Player}
     */

    public abstract @Nullable Language getLanguage(@Nonnull Player p);

    /**
     * This method returns the default {@link Language} of this {@link Server}
     *
     * @return The default {@link Language}
     */

    public abstract @Nullable Language getDefaultLanguage();

    /**
     * This returns whether a {@link Language} with the given id exists within
     * the project resources.
     *
     * @param id
     *            The {@link Language} id
     *
     * @return Whether the project contains a {@link Language} with that id
     */
    protected abstract boolean hasLanguage(@Nonnull String id);

    /**
     * This method returns a full {@link Collection} of every {@link Language} that was
     * found.
     *
     * @return A {@link Collection} that contains every installed {@link Language}
     */

    public abstract @Nonnull Collection<Language> getLanguages();

    /**
     * This method adds a new {@link Language} with the given id and texture.
     *
     * @param id
     *            The {@link Language} id
     * @param texture
     *            The texture of how this {@link Language} should be displayed
     */
    protected abstract void addLanguage(@Nonnull String id, @Nonnull String texture);

    /**
     * This will load every {@link LanguagePreset} into memory.
     * To be precise: It performs {@link #addLanguage(String, String)} for every
     * value of {@link LanguagePreset}.
     */
    protected void loadEmbeddedLanguages() {
        for (LanguagePreset lang : LanguagePreset.values()) {
            if (lang.isReadyForRelease() || Slimefun.getUpdater().getBranch() != SlimefunBranch.STABLE) {
                addLanguage(lang.getLanguageCode(), lang.getTexture());
            }
        }
    }

    private @Nonnull FileConfiguration getDefaultFile(@Nonnull LanguageFile file) {
        Language language = getLanguage(LanguagePreset.ENGLISH.getLanguageCode());

        if (language == null) {
            throw new IllegalStateException("Fallback language \"en\" is missing!");
        }

        FileConfiguration fallback = language.getFile(file);

        if (fallback != null) {
            return fallback;
        } else {
            throw new IllegalStateException("Fallback file: \"" + file.getFilePath("en") + "\" is missing!");
        }
    }

    @ParametersAreNonnullByDefault
    private @Nullable String getStringOrNull(@Nullable Language language, LanguageFile file, String path) {
        Validate.notNull(file, "You need to provide a LanguageFile!");
        Validate.notNull(path, "The path cannot be null!");

        if (language == null) {
            // Unit-Test scenario (or something went horribly wrong)
            return "Error: No language present";
        }

        FileConfiguration config = language.getFile(file);

        if (config != null) {
            String value = config.getString(path);

            // Return the found value (unless null)
            if (value != null) {
                return value;
            }
        }

        // Fallback to default configuration
        FileConfiguration defaults = getDefaultFile(file);
        String defaultValue = defaults.getString(path);

        // Return the default value or an error message
        return defaultValue != null ? defaultValue : null;
    }

    @ParametersAreNonnullByDefault
    private @Nonnull String getString(@Nullable Language language, LanguageFile file, String path) {
        String string = getStringOrNull(language, file, path);
        return string != null ? string : "! Missing string \"" + path + '"';
    }

    /**
     * Per-language message read with an English fallback, for callers that only have a {@link Language}
     * (no {@link Player}) - e.g. a boot-time precompute running on the Netty-safe path. Unlike
     * {@link #getMessage(Player, String)}, this never touches a {@link Player}. Public (not
     * package-private): {@link LocalizationService}, the concrete subclass returned by
     * {@code Slimefun.getLocalization()}, lives in a different package and would not inherit a
     * default-access member declared here.
     */
    @ParametersAreNonnullByDefault
    public @Nullable String getStringOrDefault(Language language, LanguageFile file, String path) {
        return getStringOrNull(language, file, path);
    }

    @ParametersAreNonnullByDefault
    private @Nullable List<String> getStringListOrNull(@Nullable Language language, LanguageFile file, String path) {
        Validate.notNull(file, "You need to provide a LanguageFile!");
        Validate.notNull(path, "The path cannot be null!");

        if (language == null) {
            // Unit-Test scenario (or something went horribly wrong)
            return Arrays.asList("Error: No language present");
        }

        FileConfiguration config = language.getFile(file);

        if (config != null) {
            List<String> value = config.getStringList(path);

            // Return the found value (unless empty)
            if (!value.isEmpty()) {
                return value;
            }
        }

        // Fallback to default configuration
        FileConfiguration defaults = getDefaultFile(file);
        List<String> defaultValue = defaults.getStringList(path);

        // Return the default value or an error message
        return !defaultValue.isEmpty() ? defaultValue : null;
    }

    @ParametersAreNonnullByDefault
    private @Nonnull List<String> getStringList(@Nullable Language language, LanguageFile file, String path) {
        List<String> list = getStringListOrNull(language, file, path);
        return list != null ? list : Arrays.asList("! Missing string \"" + path + '"');
    }

    public @Nonnull String getMessage(@Nonnull String key) {
        Validate.notNull(key, "Message key must not be null!");

        Language language = getDefaultLanguage();

        String message = language == null ? null : language.getFile(LanguageFile.MESSAGES).getString(key);

        if (message == null) {
            return getDefaultFile(LanguageFile.MESSAGES).getString(key);
        }

        return message;
    }

    public @Nonnull String getMessage(@Nonnull Player p, @Nonnull String key) {
        Validate.notNull(p, "Player must not be null!");
        Validate.notNull(key, "Message key must not be null!");

        return getString(getLanguage(p), LanguageFile.MESSAGES, key);
    }

    /**
     * Returns the Strings referring to the specified Key
     *
     * @param key
     *            The Key of those Messages
     * @return The List this key is referring to
     */
    public @Nonnull List<String> getDefaultMessages(@Nonnull String key) {
        return defaultConfig.getStringList(key);
    }

    public @Nonnull List<String> getMessages(@Nonnull Player p, @Nonnull String key) {
        Validate.notNull(p, "Player should not be null.");
        Validate.notNull(key, "Message key cannot be null.");

        return getStringList(getLanguage(p), LanguageFile.MESSAGES, key);
    }

    @ParametersAreNonnullByDefault
    public @Nonnull List<String> getMessages(Player p, String key, UnaryOperator<String> function) {
        Validate.notNull(p, "Player cannot be null.");
        Validate.notNull(key, "Message key cannot be null.");
        Validate.notNull(function, "Function cannot be null.");

        List<String> messages = getMessages(p, key);
        messages.replaceAll(function);

        return messages;
    }

    public @Nullable String getResearchName(@Nonnull Player p, @Nonnull NamespacedKey key) {
        Validate.notNull(p, "Player must not be null.");
        Validate.notNull(key, "NamespacedKey cannot be null.");

        return getStringOrNull(getLanguage(p), LanguageFile.RESEARCHES, key.getNamespace() + '.' + key.getKey());
    }

    public @Nullable String getItemGroupName(@Nonnull Player p, @Nonnull NamespacedKey key) {
        Validate.notNull(p, "Player must not be null.");
        Validate.notNull(key, "NamespacedKey cannot be null!");

        return getStringOrNull(getLanguage(p), LanguageFile.CATEGORIES, key.getNamespace() + '.' + key.getKey());
    }

    /**
     * Lets an addon name its own {@link ItemGroup}s in the viewer's language, from
     * {@code languages/<lang>/categories.yml} inside the addon's jar. Call this from the addon's
     * {@code onEnable}.
     * <p>
     * Without it an {@link ItemGroup}'s icon keeps the English display name it was constructed
     * with, since that literal is the only name the group has.
     *
     * @param addon
     *            The addon shipping the translations
     */
    public void registerItemGroupTranslations(@Nonnull JavaPlugin addon) {
        registerAddonNames(addon, LanguageFile.CATEGORIES);
    }

    /**
     * Lets an addon name its own {@link Research}es in the viewer's language, from
     * {@code languages/<lang>/researches.yml} inside the addon's jar. Call this from the addon's
     * {@code onEnable}.
     * <p>
     * Without it a {@link Research} keeps the English name it was constructed with, since that
     * literal is the only name it has.
     *
     * @param addon
     *            The addon shipping the translations
     */
    public void registerResearchTranslations(@Nonnull JavaPlugin addon) {
        registerAddonNames(addon, LanguageFile.RESEARCHES);
    }

    /**
     * Lets an addon name and describe its own {@link RecipeType}s in the viewer's language, from
     * {@code languages/<lang>/recipes.yml} inside the addon's jar, keyed {@code <namespace>.<key>}
     * with a {@code name} and a {@code lore} list. Call this from the addon's {@code onEnable}.
     * <p>
     * Without it a {@link RecipeType}'s guide icon keeps the English text it was constructed with.
     *
     * @param addon
     *            The addon shipping the translations
     */
    public void registerRecipeTypeTranslations(@Nonnull JavaPlugin addon) {
        registerAddonNames(addon, LanguageFile.RECIPES);
    }

    private void registerAddonNames(@Nonnull JavaPlugin addon, @Nonnull LanguageFile file) {
        Validate.notNull(addon, "The addon cannot be null.");

        for (Language language : getLanguages()) {
            InputStream stream = addon.getResource("languages/" + language.getId() + '/' + file.getFileName());

            if (stream == null) {
                continue;
            }

            FileConfiguration names = language.getFile(file);

            if (names == null) {
                names = new YamlConfiguration();
                language.setFile(file, names);
            }

            mergeAddonNames(names, stream);
        }
    }

    /**
     * Copies an addon's names into the language's own file, so {@link #getItemGroupName} and
     * {@link #getResearchName} resolve them with no second lookup path.
     *
     * @implNote An existing key wins, matching {@link AddonMessageService}. Keys are
     *           {@code <namespace>.<key>} and an addon's namespace is its own, so this is a
     *           safeguard against a jar shipping a duplicate rather than a conflict between addons.
     *           Only strings and lists of strings are copied, so a name can never resolve to a nested section,
     *           which {@link FileConfiguration#getString} would render as its {@code toString}. A
     *           malformed file is skipped whole rather than taking the surrounding loop over every
     *           language and addon down with it.
     */
    static void mergeAddonNames(@Nonnull FileConfiguration target, @Nonnull InputStream stream) {
        YamlConfiguration source;

        try {
            source = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return;
        }

        for (String key : source.getKeys(true)) {
            if (target.isSet(key)) {
                continue;
            }

            if (source.isString(key)) {
                target.set(key, source.getString(key));
            } else if (isStringList(source, key)) {
                target.set(key, source.getStringList(key));
            }
        }
    }

    /**
     * @implNote {@link FileConfiguration#getStringList} silently converts numbers and drops other
     *           values, so a list is copied only when every element already is a string.
     */
    private static boolean isStringList(@Nonnull FileConfiguration config, @Nonnull String key) {
        List<?> list = config.getList(key);
        return list != null && list.stream().allMatch(String.class::isInstance);
    }

    /**
     * Writes the English name of every item group, research and recipe type an addon registered,
     * keyed exactly as {@code categories.yml}, {@code researches.yml} and {@code recipes.yml} look
     * them up, so an addon's translation files can be authored from the runtime instead of a source
     * scan. Core's own {@code slimefun} and {@code minecraft} namespaces are skipped.
     *
     * @param out
     *            The file to write
     */
    public void dumpAddonNames(@Nonnull File out) {
        YamlConfiguration config = new YamlConfiguration();

        for (ItemGroup group : Slimefun.getRegistry().getAllItemGroups()) {
            if (isAddonKey(group.getKey())) {
                config.set("categories." + toYamlKey(group.getKey()), group.getUnlocalizedName());
            }
        }

        for (Research research : Slimefun.getRegistry().getResearches()) {
            if (isAddonKey(research.getKey())) {
                config.set("researches." + toYamlKey(research.getKey()), research.getUnlocalizedName());
            }
        }

        Set<RecipeType> recipeTypes = new LinkedHashSet<>();

        for (SlimefunItem item : Slimefun.getRegistry().getAllSlimefunItems()) {
            recipeTypes.add(item.getRecipeType());
        }

        for (RecipeType recipeType : recipeTypes) {
            ItemStack item = recipeType.toItem();

            if (item == null || !isAddonKey(recipeType.getKey()) || !item.hasItemMeta()) {
                continue;
            }

            ItemMeta meta = item.getItemMeta();
            String path = "recipes." + toYamlKey(recipeType.getKey());

            if (meta.hasDisplayName()) {
                config.set(path + ".name", meta.getDisplayName().replace(ChatColor.COLOR_CHAR, '&'));
            }

            if (meta.hasLore()) {
                List<String> lore = new ArrayList<>(meta.getLore());
                lore.replaceAll(line -> line.replace(ChatColor.COLOR_CHAR, '&'));
                config.set(path + ".lore", lore);
            }

            String machine = recipeType.getMachine() == null ? null : recipeType.getMachine().getId();

            if (machine != null) {
                config.set(path + ".machine", machine);
            }
        }

        try {
            config.save(out);
            Slimefun.logger().log(Level.INFO, "Dumped addon names to {0}", out.getPath());
        } catch (IOException e) {
            Slimefun.logger().log(Level.WARNING, "Failed to dump addon names: {0}", e.getMessage());
        }
    }

    private static boolean isAddonKey(@Nonnull NamespacedKey key) {
        return !key.getNamespace().equals("slimefun") && !key.getNamespace().equals("minecraft");
    }

    private static @Nonnull String toYamlKey(@Nonnull NamespacedKey key) {
        return key.getNamespace() + '.' + key.getKey();
    }

    public @Nullable String getResourceString(@Nonnull Player p, @Nonnull String key) {
        Validate.notNull(p, "Player should not be null!");
        Validate.notNull(key, "Message key should not be null!");

        return getStringOrNull(getLanguage(p), LanguageFile.RESOURCES, key);
    }

    public @Nonnull ItemStack getRecipeTypeItem(@Nonnull Player p, @Nonnull RecipeType recipeType) {
        Validate.notNull(p, "Player cannot be null!");
        Validate.notNull(recipeType, "Recipe type cannot be null!");

        ItemStack item = recipeType.toItem();

        if (item == null) {
            // Fixes #3088
            return new ItemStack(Material.AIR);
        }

        Language language = getLanguage(p);
        NamespacedKey key = recipeType.getKey();

        return CustomItemStack.create(item, meta -> {
            String displayName = getStringOrNull(language, LanguageFile.RECIPES, key.getNamespace() + "." + key.getKey() + ".name");

            // Set the display name if possible, else keep the default item name.
            if (displayName != null) {
                meta.setDisplayName(ChatColor.AQUA + displayName);
            }

            List<String> lore = getStringListOrNull(language, LanguageFile.RECIPES, key.getNamespace() + "." + key.getKey() + ".lore");

            // Set the lore if possible, else keep the default lore.
            if (lore != null) {
                lore.replaceAll(line -> ChatColor.GRAY + line);
                meta.setLore(lore);
            }

            VersionedItemFlag.addFlags(meta, VersionedItemFlag.HIDE_ATTRIBUTES);
            VersionedItemFlag.addFlags(meta, VersionedItemFlag.HIDE_ENCHANTS);
        });
    }

    public void sendMessage(@Nonnull CommandSender recipient, @Nonnull String key, boolean addPrefix) {
        Validate.notNull(recipient, "Recipient cannot be null!");
        Validate.notNull(key, "Message key cannot be null!");

        String prefix = addPrefix ? getChatPrefix() : "";

        if (recipient instanceof Player) {
            Player player = (Player) recipient;            recipient.sendMessage(ChatColors.color(prefix + getMessage(player, key)));
        } else {
            recipient.sendMessage(ChatColor.stripColor(ChatColors.color(prefix + getMessage(key))));
        }
    }

    public void sendActionbarMessage(@Nonnull Player player, @Nonnull String key, boolean addPrefix) {
        Validate.notNull(player, "Player cannot be null!");
        Validate.notNull(key, "Message key cannot be null!");

        String prefix = addPrefix ? getChatPrefix() : "";
        String message = ChatColors.color(prefix + getMessage(player, key));

        BaseComponent[] components = TextComponent.fromLegacyText(message);
        // The (ChatMessageType, BaseComponent[]) sendMessage overload is post-1.8; reached reflectively.
        ReflectionCompat.invoke(player.spigot(), "sendMessage", ChatMessageType.ACTION_BAR, components);
    }

    public void sendMessage(@Nonnull CommandSender recipient, @Nonnull String key) {
        sendMessage(recipient, key, true);
    }

    @ParametersAreNonnullByDefault
    public void sendMessage(CommandSender recipient, String key, UnaryOperator<String> function) {
        sendMessage(recipient, key, true, function);
    }

    @ParametersAreNonnullByDefault
    public void sendMessage(CommandSender recipient, String key, boolean addPrefix, UnaryOperator<String> function) {
        if (Slimefun.getMinecraftVersion() == MinecraftVersion.UNIT_TEST) {
            return;
        }

        String prefix = addPrefix ? getChatPrefix() : "";

        if (recipient instanceof Player) {
            Player player = (Player) recipient;            recipient.sendMessage(ChatColors.color(prefix + function.apply(getMessage(player, key))));
        } else {
            recipient.sendMessage(ChatColor.stripColor(ChatColors.color(prefix + function.apply(getMessage(key)))));
        }
    }

    public void sendMessages(@Nonnull CommandSender recipient, @Nonnull String key) {
        String prefix = getChatPrefix();

        if (recipient instanceof Player) {
            Player player = (Player) recipient;            for (String translation : getMessages(player, key)) {
                String message = ChatColors.color(prefix + translation);
                recipient.sendMessage(message);
            }
        } else {
            for (String translation : getDefaultMessages(key)) {
                String message = ChatColors.color(prefix + translation);
                recipient.sendMessage(ChatColor.stripColor(message));
            }
        }
    }

    @ParametersAreNonnullByDefault
    public void sendMessages(CommandSender recipient, String key, boolean addPrefix, UnaryOperator<String> function) {
        String prefix = addPrefix ? getChatPrefix() : "";

        if (recipient instanceof Player) {
            Player player = (Player) recipient;            for (String translation : getMessages(player, key)) {
                String message = ChatColors.color(prefix + function.apply(translation));
                recipient.sendMessage(message);
            }
        } else {
            for (String translation : getDefaultMessages(key)) {
                String message = ChatColors.color(prefix + function.apply(translation));
                recipient.sendMessage(ChatColor.stripColor(message));
            }
        }
    }

    @ParametersAreNonnullByDefault
    public void sendMessages(CommandSender recipient, String key, UnaryOperator<String> function) {
        sendMessages(recipient, key, true, function);
    }

    protected @Nonnull Set<String> getTotalKeys(@Nonnull Language lang) {
        return getKeys(lang.getFiles());
    }

    protected @Nonnull Set<String> getKeys(@Nonnull FileConfiguration... files) {
        Set<String> keys = new HashSet<>();

        for (FileConfiguration cfg : files) {
            keys.addAll(cfg.getKeys(true));
        }

        return keys;
    }
}

