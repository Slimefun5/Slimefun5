package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;

/**
 * Translates the decorative info items inside a block's {@link BlockMenu} (titles, descriptions and
 * background labels) per language. These strings are hardcoded English literals in each machine's
 * preset; this service keeps a per-language override keyed by preset id (= item id) and slot, loaded
 * from {@code languages/<lang>/menus.yml} shipped by core and addons.
 *
 * Block menu inventories are shared per placed block, so the translation is applied for the viewing
 * player when they open the menu. Only the preset's decorative slots are rewritten - input/output
 * slots (the only slots persisted to disk) are never touched, so stored inventories are unaffected.
 */
public class MenuTranslationService {

    /** A single decorative slot's translated name and lore. Either field may be null/empty. */
    private static final class MenuItemTranslation {

        private final String name;
        private final List<String> lore;

        MenuItemTranslation(@Nullable String name, @Nonnull List<String> lore) {
            this.name = name;
            this.lore = lore;
        }
    }

    /** Indexed language -&gt; presetId -&gt; slot -&gt; translation. */
    private final Map<String, Map<String, Map<Integer, MenuItemTranslation>>> byLanguage = new HashMap<>();

    /**
     * Indexed language -&gt; presetId -&gt; translated inventory title, loaded from an optional {@code title}
     * key sibling to the numbered slot keys in {@code menus.yml}. Unlike the per-slot translations, there
     * is no English entry to fall back to here: the preset's own title already *is* the English text, so
     * an untranslated language simply keeps it (see {@link #getTitleFor}).
     */
    private final Map<String, Map<String, String>> titlesByLanguage = new HashMap<>();

    /** Loads the bundled core menu translations for every supported language. */
    public void loadBundled() {
        for (Language language : Slimefun.getLocalization().getLanguages()) {
            InputStream stream = Slimefun.class.getResourceAsStream("/languages/" + language.getId() + "/menus.yml");

            if (stream != null) {
                load(language.getId(), stream);
            }
        }
    }

    /**
     * Lets an addon contribute its own {@code languages/<lang>/menus.yml} translations. Call from the
     * addon's {@code onEnable} after its items are registered.
     */
    public void registerTranslations(@Nonnull JavaPlugin addon) {
        for (Language language : Slimefun.getLocalization().getLanguages()) {
            InputStream stream = addon.getResource("languages/" + language.getId() + "/menus.yml");

            if (stream != null) {
                load(language.getId(), stream);
            }
        }
    }

    /** Package-private (not private): the same test seam pattern as {@code CategoryMenuBuilder.build} - lets tests feed a synthetic menus.yml without touching bundled resources. */
    void load(@Nonnull String language, @Nonnull InputStream stream) {
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            Map<String, Map<Integer, MenuItemTranslation>> presets = byLanguage.computeIfAbsent(language, k -> new HashMap<>());
            Map<String, String> titles = titlesByLanguage.computeIfAbsent(language, k -> new HashMap<>());

            for (String presetId : config.getKeys(false)) {
                ConfigurationSection presetSection = config.getConfigurationSection(presetId);

                if (presetSection == null) {
                    continue;
                }

                String title = presetSection.getString("title");

                if (title != null) {
                    titles.put(presetId, title);
                }

                Map<Integer, MenuItemTranslation> slots = presets.computeIfAbsent(presetId, k -> new HashMap<>());

                for (String slotKey : presetSection.getKeys(false)) {
                    Integer slot = parseSlot(slotKey);

                    if (slot == null) {
                        continue;
                    }

                    String name = presetSection.getString(slotKey + ".name");
                    List<String> lore = presetSection.getStringList(slotKey + ".lore");

                    if (name != null || !lore.isEmpty()) {
                        slots.put(slot, new MenuItemTranslation(name, lore));
                    }
                }
            }
        } catch (RuntimeException e) {
            Slimefun.logger().log(Level.WARNING, "Failed to load menu translations for {0}: {1}", new Object[] { language, e.getMessage() });
        }
    }

    @Nullable
    private static Integer parseSlot(@Nonnull String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Rewrites the decorative info items of an opened {@link BlockMenu} into the viewing player's
     * language. Falls back to the English ({@code en}) entry so a player whose language lacks an entry
     * (or an English player after a German player opened the same block) sees the correct language.
     * Slots with no translation in either the player's language or English are left untouched.
     */
    public void applyToMenu(@Nonnull BlockMenu menu, @Nonnull Player p) {
        String presetId = menu.getPreset().getID();
        Map<Integer, MenuItemTranslation> playerSlots = slotsFor(languageOf(p), presetId);
        Map<Integer, MenuItemTranslation> englishSlots = slotsFor("en", presetId);

        if (playerSlots == null && englishSlots == null) {
            return;
        }

        java.util.Set<Integer> targets = new java.util.HashSet<>();

        if (playerSlots != null) {
            targets.addAll(playerSlots.keySet());
        }

        if (englishSlots != null) {
            targets.addAll(englishSlots.keySet());
        }

        for (int slot : targets) {
            MenuItemTranslation translation = playerSlots != null ? playerSlots.get(slot) : null;

            if (translation == null && englishSlots != null) {
                translation = englishSlots.get(slot);
            }

            if (translation != null) {
                applyToSlot(menu, slot, translation);
            }
        }
    }

    /**
     * The translated inventory title for the viewing player, recoloured per {@link #resolveColoredTitleFor},
     * or {@code null} if the result is unchanged from the preset's own current title, so the caller never
     * resends a no-op window update.
     */
    @Nullable
    public String getTitleFor(@Nonnull BlockMenu menu, @Nonnull Player p) {
        return resolveColoredTitleFor(languageOf(p), menu.getPreset());
    }

    /**
     * The {@link Player}-independent half of {@link #getTitleFor}: resolves the title text (see
     * {@link #resolveTitleFor}) and then recolours it per {@link #resolveTitleColor}, so a coloured title
     * can be tested without MockBukkit's player/locale plumbing (see {@link #resolveTitleFor}'s own note
     * on this). Returns {@code null} when nothing changes relative to the preset's current title.
     */
    @Nullable
    String resolveColoredTitleFor(@Nullable String language, @Nonnull BlockMenuPreset preset) {
        String rawTitle = preset.getTitle();
        String currentTitle = ChatColor.translateAlternateColorCodes('&', rawTitle);
        String resolvedText = resolveTitleFor(language, preset.getID(), rawTitle);
        String baseText = resolvedText != null ? resolvedText : currentTitle;

        String finalTitle = recolor(baseText, resolveTitleColor(preset));

        return finalTitle.equals(currentTitle) ? null : finalTitle;
    }

    /**
     * Every menu title is deliberately coloured, in order of precedence: the slot a preset
     * {@link BlockMenuPreset#setHeaderItemSlot declared} as its header item; else the colour it
     * {@link BlockMenuPreset#optOutOfHeaderItem(ChatColor) explicitly opted out} with; else the colour of
     * the machine's own item name, if it {@link BlockMenuPreset#optOutOfHeaderItem() declared that}; else
     * a header item found by the name-matching heuristic (see {@link #headerColorOf}); else
     * {@link ChatColor#GRAY} - never left to inherit whatever colour the preset's hardcoded title string
     * happened to have.
     *
     * @implNote A declared header slot outranks a declared opt-out colour so a base class can set the
     *           floor for every machine derived from it while a subclass that does have a header item
     *           still wins - the two are otherwise never set on the same preset.
     */
    @Nonnull
    private static ChatColor resolveTitleColor(@Nonnull BlockMenuPreset preset) {
        Integer declaredSlot = preset.getExplicitHeaderSlot();

        if (declaredSlot != null) {
            ChatColor declared = colorOfSlot(preset, declaredSlot);

            if (declared != null) {
                return declared;
            }
        }

        ChatColor optOut = preset.getExplicitTitleColor();

        if (optOut != null) {
            return optOut;
        }

        if (preset.usesItemNameTitleColor()) {
            ChatColor itemColor = itemNameColorOf(preset);

            if (itemColor != null) {
                return itemColor;
            }
        }

        ChatColor headerColor = headerColorOf(preset);
        return headerColor != null ? headerColor : ChatColor.GRAY;
    }

    /** The leading colour of the machine's own (English) item name, or {@code null} if it has none. */
    @Nullable
    private static ChatColor itemNameColorOf(@Nonnull BlockMenuPreset preset) {
        SlimefunItem item = preset.getSlimefunItem();

        if (item == null) {
            return null;
        }

        String name = Slimefun.getItemTranslationService().getNameForLanguage("en", item.getId());
        return leadingColor(ChatColor.translateAlternateColorCodes('&', name != null ? name : item.getItemName()));
    }

    /**
     * Reports how {@link #resolveTitleColor} would colour {@code preset}'s title today: the header slot
     * the name-matching heuristic resolves, or every named decorative slot it could have matched. Feeds
     * the boot-time menu audit while presets are migrated off the heuristic onto an explicit declaration.
     *
     * @param preset
     *            The preset to describe
     *
     * @return A one-line description of the current resolution
     */
    @Nonnull
    public static String describeHeaderResolution(@Nonnull BlockMenuPreset preset) {
        Integer slot = findHeaderSlot(preset);

        if (slot != null) {
            ChatColor color = headerColorOf(preset);
            return "slot " + slot + " (" + (color != null ? color.name() : "no colour") + ")";
        }

        return "none; named slots: " + describeNamedSlots(preset);
    }

    @Nonnull
    private static String describeNamedSlots(@Nonnull BlockMenuPreset preset) {
        StringBuilder description = new StringBuilder();

        for (int slot : preset.getPresetSlots()) {
            ItemStack candidate = preset.getItemInSlot(slot);
            ItemMeta meta = candidate != null && candidate.hasItemMeta() ? candidate.getItemMeta() : null;

            if (meta == null || !meta.hasDisplayName()) {
                continue;
            }

            String name = ChatColor.stripColor(meta.getDisplayName()).trim();

            if (name.isEmpty()) {
                continue;
            }

            ChatColor color = leadingColor(meta.getDisplayName());

            if (description.length() > 0) {
                description.append(", ");
            }

            description.append(slot).append('=').append(color != null ? color.name() : "PLAIN").append(':').append(name);
        }

        return description.length() == 0 ? "(none named)" : description.toString();
    }

    /**
     * The header item of a machine's GUI is, by default, the slot a preset declares via
     * {@link BlockMenuPreset#setHeaderItemSlot(int)}. Legacy presets that never declared one are still
     * resolved by convention: a decorative slot that repeats the machine's own item name (often in a
     * different colour, e.g. the Trash Can's item name is aqua while its GUI header reads red), found by
     * matching a preset slot's (colour-stripped) name against the item's own English name.
     *
     * @implNote The item's baked template name is always its raw id (see the "name is always the id"
     *           rule), so the match target is the item's resolved English name ({@code en/items.yml}, or
     *           {@link SlimefunItem#getItemName()} if the item has no entry there) rather than the baked
     *           template's own display name.
     *           <p>Not every preset has a matching decorative slot (e.g. a bare {@code AContainer}-derived
     *           furnace GUI has no such slot), in which case this returns {@code null} and
     *           {@link #resolveTitleColor} falls further back.
     */
    @Nullable
    private static Integer findHeaderSlot(@Nonnull BlockMenuPreset preset) {
        Integer explicitSlot = preset.getExplicitHeaderSlot();

        if (explicitSlot != null) {
            return explicitSlot;
        }

        SlimefunItem item = preset.getSlimefunItem();

        if (item == null) {
            return null;
        }

        String englishName = Slimefun.getItemTranslationService().getNameForLanguage("en", item.getId());
        String itemName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', englishName != null ? englishName : item.getItemName())).trim();

        if (itemName.isEmpty()) {
            return null;
        }

        for (int slot : preset.getPresetSlots()) {
            ItemStack candidate = preset.getItemInSlot(slot);
            ItemMeta meta = candidate != null && candidate.hasItemMeta() ? candidate.getItemMeta() : null;

            if (meta != null && meta.hasDisplayName() && itemName.equalsIgnoreCase(ChatColor.stripColor(meta.getDisplayName()).trim())) {
                return slot;
            }
        }

        return null;
    }

    @Nullable
    private static ChatColor headerColorOf(@Nonnull BlockMenuPreset preset) {
        Integer slot = findHeaderSlot(preset);
        return slot != null ? colorOfSlot(preset, slot) : null;
    }

    /** The leading colour of the item sitting in {@code slot}, or {@code null} if it has no coloured name. */
    @Nullable
    private static ChatColor colorOfSlot(@Nonnull BlockMenuPreset preset, int slot) {
        ItemStack item = preset.getItemInSlot(slot);
        ItemMeta meta = item != null && item.hasItemMeta() ? item.getItemMeta() : null;
        return meta != null && meta.hasDisplayName() ? leadingColor(meta.getDisplayName()) : null;
    }

    /** The first COLOUR code (not a formatting code) in a leading run of {@code §}-codes, or {@code null} if the text starts with plain characters. */
    @Nullable
    private static ChatColor leadingColor(@Nonnull String text) {
        int i = 0;

        while (i + 1 < text.length() && text.charAt(i) == ChatColor.COLOR_CHAR) {
            ChatColor code = ChatColor.getByChar(text.charAt(i + 1));

            if (code != null && code.isColor()) {
                return code;
            }

            i += 2;
        }

        return null;
    }

    /** Replaces any leading run of {@code §}-codes with a single colour code, so colour is never doubled up. */
    @Nonnull
    private static String recolor(@Nonnull String text, @Nonnull ChatColor color) {
        int i = 0;

        while (i + 1 < text.length() && text.charAt(i) == ChatColor.COLOR_CHAR) {
            i += 2;
        }

        return color + text.substring(i);
    }

    /**
     * The {@link Player}-independent half of {@link #getTitleFor}, isolated so it is testable without
     * MockBukkit's player/locale plumbing.
     *
     * @implNote Precedence: an explicit {@code title:} entry for {@code presetId} in {@code language}, else
     *           that language's translated name of the {@link SlimefunItem} registered under {@code presetId}
     *           (every machine preset id is also that machine's item id) - this is what actually translates
     *           the ~100+ presets that never got a hand-authored {@code title:} entry, using translations
     *           {@code items.yml} already ships - else {@code null} (keep the preset's own English title).
     */
    @Nullable
    String resolveTitleFor(@Nullable String language, @Nonnull String presetId, @Nonnull String currentRawTitle) {
        if (language == null) {
            return null;
        }

        String translated = explicitTitleOverride(language, presetId);

        if (translated == null) {
            translated = Slimefun.getItemTranslationService().getNameForLanguage(language, presetId);
        }

        if (translated == null) {
            return null;
        }

        String current = ChatColor.translateAlternateColorCodes('&', currentRawTitle);
        return translated.equals(current) ? null : translated;
    }

    @Nullable
    private String explicitTitleOverride(@Nonnull String language, @Nonnull String presetId) {
        Map<String, String> titles = titlesByLanguage.get(language);
        String raw = titles != null ? titles.get(presetId) : null;
        return raw != null ? ChatColor.translateAlternateColorCodes('&', raw) : null;
    }

    private void applyToSlot(@Nonnull BlockMenu menu, int slot, @Nonnull MenuItemTranslation translation) {
        ItemStack current = menu.getItemInSlot(slot);

        if (current == null) {
            return;
        }

        ItemMeta meta = current.getItemMeta();

        if (meta == null) {
            return;
        }

        String targetName = translation.name != null
            ? ChatColor.translateAlternateColorCodes('&', translation.name)
            : meta.getDisplayName();

        List<String> targetLore = null;

        if (!translation.lore.isEmpty()) {
            targetLore = new ArrayList<>();

            for (String line : translation.lore) {
                targetLore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
        }

        // Skip the rewrite when the slot is already in the right language, so re-opening is cheap and
        // does not needlessly dirty the menu.
        boolean nameSame = targetName == null || targetName.equals(meta.getDisplayName());
        boolean loreSame = targetLore == null || targetLore.equals(meta.getLore());

        if (nameSame && loreSame) {
            return;
        }

        ItemStack copy = current.clone();
        ItemMeta copyMeta = copy.getItemMeta();

        if (copyMeta == null) {
            return;
        }

        if (translation.name != null) {
            copyMeta.setDisplayName(targetName);
        }

        if (targetLore != null) {
            copyMeta.setLore(targetLore);
        }

        copy.setItemMeta(copyMeta);
        menu.replaceExistingItem(slot, copy, false);
    }

    /**
     * Development helper: writes the English baseline of every currently-registered block-menu preset
     * to the given file as {@code menus.yml}, capturing each decorative slot's name and lore. Pure
     * background panes (blank name, no lore) are skipped. This covers core and all loaded addons in one
     * pass, giving an exact preset-id/slot baseline to translate. Call once after all items are loaded.
     */
    public void dumpBaseline(@Nonnull java.io.File out) {
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<String, me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset> entry : Slimefun.getRegistry().getMenuPresets().entrySet()) {
            String presetId = entry.getKey();
            me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset preset = entry.getValue();

            config.set(presetId + ".title", preset.getTitle().replace('§', '&'));

            for (int slot : preset.getPresetSlots()) {
                ItemStack item = preset.getItemInSlot(slot);

                if (item == null || !item.hasItemMeta()) {
                    continue;
                }

                ItemMeta meta = item.getItemMeta();
                String name = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : null;
                List<String> lore = meta != null && meta.hasLore() ? meta.getLore() : new ArrayList<>();

                boolean blankName = name == null || ChatColor.stripColor(name).trim().isEmpty();

                if (blankName && lore.isEmpty()) {
                    continue;
                }

                String base = presetId + "." + slot;

                if (name != null) {
                    config.set(base + ".name", name.replace('§', '&'));
                }

                if (!lore.isEmpty()) {
                    List<String> converted = new ArrayList<>();

                    for (String line : lore) {
                        converted.add(line.replace('§', '&'));
                    }

                    config.set(base + ".lore", converted);
                }
            }
        }

        try {
            config.save(out);
            Slimefun.logger().log(Level.INFO, "Dumped menu baseline to {0}", out.getPath());
        } catch (java.io.IOException e) {
            Slimefun.logger().log(Level.WARNING, "Failed to dump menu baseline: {0}", e.getMessage());
        }
    }

    /**
     * Key coverage of a language: {@code {covered, total}} where total is the number of English
     * (presetId, slot) menu entries and covered is how many of those the given language also defines
     * (an entry only ever exists non-empty - see {@link #load}). English returns {@code {total, total}}.
     */
    @Nonnull
    public int[] getKeyCoverage(@Nonnull String languageId) {
        Map<String, Map<Integer, MenuItemTranslation>> english = byLanguage.getOrDefault("en", Collections.<String, Map<Integer, MenuItemTranslation>>emptyMap());
        boolean isEnglish = "en".equalsIgnoreCase(languageId);
        Map<String, Map<Integer, MenuItemTranslation>> lang = isEnglish ? english : byLanguage.getOrDefault(languageId, Collections.<String, Map<Integer, MenuItemTranslation>>emptyMap());

        int total = 0;
        int covered = 0;

        for (Map.Entry<String, Map<Integer, MenuItemTranslation>> presetEntry : english.entrySet()) {
            Map<Integer, MenuItemTranslation> langSlots = lang.get(presetEntry.getKey());

            for (Integer slot : presetEntry.getValue().keySet()) {
                total++;

                if (isEnglish || (langSlots != null && langSlots.containsKey(slot))) {
                    covered++;
                }
            }
        }

        return new int[] { covered, total };
    }

    @Nullable
    private Map<Integer, MenuItemTranslation> slotsFor(@Nullable String language, @Nonnull String presetId) {
        if (language == null) {
            return null;
        }

        Map<String, Map<Integer, MenuItemTranslation>> presets = byLanguage.get(language);
        return presets != null ? presets.get(presetId) : null;
    }

    @Nullable
    private String languageOf(@Nonnull Player p) {
        Language language = Slimefun.getLocalization().getLanguage(p);
        return language != null ? language.getId() : null;
    }
}
