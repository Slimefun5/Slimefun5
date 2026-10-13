package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
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
import io.github.thebusybiscuit.slimefun5.utils.compatibility.PdcCompat;

import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;

/**
 * Translates the decorative info items inside a block's {@link BlockMenu} (titles, descriptions and
 * background labels) per language. These strings are hardcoded English literals in each machine's
 * preset; this service keeps a per-language override keyed by preset id (= item id) and slot, loaded
 * from {@code languages/<lang>/menus.yml} shipped by core and addons.
 *
 * A slot is translated per viewer in the outgoing packet, keyed by a {@link MenuSlotStamp} the item
 * carries, so the shared inventory keeps the English source of truth. The open-time rewrite remains as
 * a fallback and writes into that shared inventory, which is why it skips variant slots. Only slots
 * some language actually translates are ever marked, so machine inputs and outputs are untouched.
 */
public class MenuTranslationService {

    /** A single decorative slot's translated name and lore. Either field may be null/empty. */
    static final class MenuItemTranslation {

        private final String name;
        private final List<String> lore;

        MenuItemTranslation(@Nullable String name, @Nonnull List<String> lore) {
            this.name = name;
            this.lore = lore;
        }

        @Nullable
        String getName() {
            return name;
        }

        @Nonnull
        List<String> getLore() {
            return lore;
        }

        @Nonnull
        MenuItemTranslation withCaptures(@Nonnull Map<Integer, String> captures) {
            return captures.isEmpty() ? this
                : new MenuItemTranslation(MenuTextPattern.substitute(name, captures), MenuTextPattern.substituteAll(lore, captures));
        }

        @Nonnull
        Set<Integer> placeholders() {
            Set<Integer> used = MenuTextPattern.placeholdersIn(name);

            for (String line : lore) {
                used.addAll(MenuTextPattern.placeholdersIn(line));
            }

            return used;
        }
    }

    /**
     * One state of a slot whose text changes at runtime, keyed by the English line it replaces and,
     * when two states share a name, by English lore lines that must also be present.
     */
    private static final class MenuItemVariant {

        private final String match;
        private final MenuTextPattern namePattern;
        private final List<MenuTextPattern> lorePatterns;
        private final MenuItemTranslation translation;

        MenuItemVariant(@Nonnull String match, @Nonnull List<String> matchLore, @Nonnull MenuItemTranslation translation) {
            this.match = match;
            this.namePattern = MenuTextPattern.compile(match);
            this.lorePatterns = new ArrayList<>();
            this.translation = translation;

            for (String line : matchLore) {
                lorePatterns.add(MenuTextPattern.compile(line));
            }
        }

        /** What two variants of one slot are told apart by, colours ignored. */
        @Nonnull
        String key() {
            StringBuilder key = new StringBuilder(namePattern.key());

            for (MenuTextPattern line : lorePatterns) {
                key.append('\n').append(line.key());
            }

            return key.toString();
        }

        @Nonnull
        Set<Integer> captureIndices() {
            Set<Integer> captured = namePattern.captureIndices();

            for (MenuTextPattern line : lorePatterns) {
                captured.addAll(line.captureIndices());
            }

            return captured;
        }

        /**
         * @return the captured values when the live item is in this state, else {@code null}
         * @implNote Each lore pattern must match a live line after the previous pattern's line, so a
         *           variant names its lore lines in the order the machine writes them.
         */
        @Nullable
        Map<Integer, String> capturesFrom(@Nullable String liveName, @Nonnull List<String> liveLore) {
            Map<Integer, String> captures = new HashMap<>();

            if (!namePattern.matchInto(liveName, captures)) {
                return null;
            }

            int nextLine = 0;

            for (MenuTextPattern line : lorePatterns) {
                while (nextLine < liveLore.size() && !line.matchInto(liveLore.get(nextLine), captures)) {
                    nextLine++;
                }

                if (nextLine++ >= liveLore.size()) {
                    return null;
                }
            }

            return captures;
        }
    }

    /** Indexed language -&gt; presetId -&gt; slot -&gt; translation. */
    private final Map<String, Map<String, Map<Integer, MenuItemTranslation>>> byLanguage = new ConcurrentHashMap<>();

    /**
     * Indexed language -&gt; presetId -&gt; slot -&gt; the variants authored for that slot.
     *
     * @implNote A slot whose text depends on machine state cannot be keyed by slot alone, because
     *           {@link #applyToMenu} would stamp one state's text over the other. Each variant names
     *           the English line it replaces, so the live item selects its own translation.
     */
    private final Map<String, Map<String, Map<Integer, List<MenuItemVariant>>>> variantsByLanguage = new ConcurrentHashMap<>();

    /**
     * Indexed presetId -&gt; every slot any language has an entry for, plain or variant.
     *
     * @implNote The stamp is gated on this. It used to mark EVERY slot, which meant machine inputs and
     *           outputs: a PDC-only meta flips {@code hasItemMeta()}, dough's {@code ItemUtils.canStack}
     *           rejects a pair whose {@code hasItemMeta()} differs, and a stamped output then refused to
     *           stack with the next one, jamming the machine and destroying android drops.
     */
    private final Map<String, Set<Integer>> translatableSlots = new ConcurrentHashMap<>();

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

        warnAboutAuthoringProblems();
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

        warnAboutAuthoringProblems();
    }

    private void warnAboutAuthoringProblems() {
        for (String problem : findAuthoringProblems()) {
            Slimefun.logger().log(Level.WARNING, "menus.yml: {0}", problem);
        }
    }

    /**
     * Every loaded entry that parses but cannot resolve per viewer as written, one line per problem.
     *
     * @implNote Each of these loads silently and then misbehaves only in game: a plain entry over an
     *           English toggle reapplies one state's text whatever the machine's state, two variants
     *           that differ only by colour collapse to one key so the first always wins, a variant
     *           whose {@code match} or {@code match-lore} is not an English line never fires, because
     *           the live item it is compared against always carries the English text, and a
     *           translation that drops a captured value hides a live number from the player.
     */
    @Nonnull
    public List<String> findAuthoringProblems() {
        List<String> problems = new ArrayList<>();
        Set<String> languages = new TreeSet<>(byLanguage.keySet());
        languages.addAll(variantsByLanguage.keySet());

        for (String language : languages) {
            if (!"en".equals(language)) {
                findPlainEntriesOverEnglishToggles(language, problems);
            }

            findVariantProblems(language, problems);
        }

        return problems;
    }

    private void findPlainEntriesOverEnglishToggles(@Nonnull String language, @Nonnull List<String> problems) {
        for (Map.Entry<String, Map<Integer, MenuItemTranslation>> preset : sorted(byLanguage.get(language))) {
            for (int slot : new TreeSet<>(preset.getValue().keySet())) {
                if (isEnglishToggleOnly(preset.getKey(), slot)) {
                    problems.add(describe(language, preset.getKey(), slot)
                        + "plain entry where English has only variants; author one variant per English match");
                }
            }
        }
    }

    private boolean isEnglishToggleOnly(@Nonnull String presetId, int slot) {
        Map<Integer, MenuItemTranslation> englishSlots = slotsFor("en", presetId);
        return variantsFor("en", presetId, slot) != null && (englishSlots == null || !englishSlots.containsKey(slot));
    }

    private void findVariantProblems(@Nonnull String language, @Nonnull List<String> problems) {
        for (Map.Entry<String, Map<Integer, List<MenuItemVariant>>> preset : sorted(variantsByLanguage.get(language))) {
            for (Map.Entry<Integer, List<MenuItemVariant>> slot : new TreeMap<>(preset.getValue()).entrySet()) {
                String where = describe(language, preset.getKey(), slot.getKey());
                findColourOnlyMatches(where, slot.getValue(), problems);
                findPlaceholderMismatches(where, slot.getValue(), problems);

                if (!"en".equals(language)) {
                    findMatchesEnglishNeverProduces(where, preset.getKey(), slot.getKey(), slot.getValue(), problems);
                }
            }
        }
    }

    private static void findColourOnlyMatches(@Nonnull String where, @Nonnull List<MenuItemVariant> variants, @Nonnull List<String> problems) {
        Set<String> seen = new HashSet<>();

        for (MenuItemVariant variant : variants) {
            if (!seen.add(variant.key())) {
                problems.add(where + "variant matches differ only by colour, so '" + variant.match + "' never fires");
            }
        }
    }

    private static void findPlaceholderMismatches(@Nonnull String where, @Nonnull List<MenuItemVariant> variants, @Nonnull List<String> problems) {
        for (MenuItemVariant variant : variants) {
            Set<Integer> captured = variant.captureIndices();
            Set<Integer> used = variant.translation.placeholders();

            for (int index : used) {
                if (!captured.contains(index)) {
                    problems.add(where + "'" + variant.match + "' uses {" + index + "} which its match never captures");
                }
            }

            for (int index : captured) {
                if (!used.contains(index)) {
                    problems.add(where + "'" + variant.match + "' drops the captured {" + index + "}, hiding a live value");
                }
            }
        }
    }

    private void findMatchesEnglishNeverProduces(@Nonnull String where, @Nonnull String presetId, int slot,
                                                 @Nonnull List<MenuItemVariant> variants, @Nonnull List<String> problems) {
        List<MenuItemVariant> english = variantsFor("en", presetId, slot);

        if (english == null) {
            problems.add(where + "variants on a slot English does not vary; author a plain entry instead");
            return;
        }

        Set<String> englishKeys = new HashSet<>();

        for (MenuItemVariant variant : english) {
            englishKeys.add(variant.key());
        }

        for (MenuItemVariant variant : variants) {
            if (!englishKeys.contains(variant.key())) {
                problems.add(where + "match '" + variant.match + "' is not one of English's; match and match-lore must stay the English lines");
            }
        }
    }

    @Nonnull
    private static String describe(@Nonnull String language, @Nonnull String presetId, int slot) {
        return language + " " + presetId + "/" + slot + ": ";
    }

    @Nonnull
    private static <V> Set<Map.Entry<String, V>> sorted(@Nullable Map<String, V> map) {
        return map == null ? Collections.<Map.Entry<String, V>>emptySet() : new TreeMap<>(map).entrySet();
    }

    /** Package-private (not private): the same test seam pattern as {@code CategoryMenuBuilder.build} - lets tests feed a synthetic menus.yml without touching bundled resources. */
    void load(@Nonnull String language, @Nonnull InputStream stream) {
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            Map<String, Map<Integer, MenuItemTranslation>> presets = byLanguage.computeIfAbsent(language, k -> new ConcurrentHashMap<>());
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
                        rememberTranslatableSlot(presetId, slot);
                    }

                    List<MenuItemVariant> variants = readVariants(presetSection, slotKey);

                    if (!variants.isEmpty()) {
                        variantsByLanguage
                            .computeIfAbsent(language, k -> new ConcurrentHashMap<>())
                            .computeIfAbsent(presetId, k -> new ConcurrentHashMap<>())
                            .put(slot, variants);
                        rememberTranslatableSlot(presetId, slot);
                    }
                }
            }
        } catch (RuntimeException e) {
            Slimefun.logger().log(Level.WARNING, "Failed to load menu translations for {0}: {1}", new Object[] { language, e.getMessage() });
        }
    }

    /**
     * @implNote {@code getMapList} is the only accessor that preserves a list of authored maps;
     *           an entry with no {@code match} is skipped rather than failing the whole file, which
     *           would cost every other preset in it.
     */
    @Nonnull
    private static List<MenuItemVariant> readVariants(@Nonnull ConfigurationSection section, @Nonnull String slotKey) {
        List<MenuItemVariant> parsed = new ArrayList<>();

        for (Map<?, ?> entry : section.getMapList(slotKey + ".variants")) {
            Object match = entry.get("match");

            if (match == null) {
                continue;
            }

            Object name = entry.get("name");
            MenuItemTranslation translation = new MenuItemTranslation(name == null ? null : String.valueOf(name), linesOf(entry.get("lore")));
            parsed.add(new MenuItemVariant(String.valueOf(match), linesOf(entry.get("match-lore")), translation));
        }

        return parsed;
    }

    /** A YAML value authored as either one line or a list of lines. */
    @Nonnull
    private static List<String> linesOf(@Nullable Object value) {
        List<String> lines = new ArrayList<>();

        if (value instanceof List) {
            for (Object line : (List<?>) value) {
                lines.add(String.valueOf(line));
            }
        } else if (value != null) {
            lines.add(String.valueOf(value));
        }

        return lines;
    }

    /**
     * The translation for one slot in one language, choosing among the slot's variants by the name the
     * live item currently carries.
     *
     * @return the variant's translation, else the slot's own, else {@code null} to leave the item alone
     */
    @Nullable
    MenuItemTranslation resolveSlot(@Nullable String languageId, @Nonnull String presetId, int slot, @Nullable String currentName) {
        return resolveSlot(languageId, presetId, slot, currentName, Collections.<String>emptyList());
    }

    /**
     * @param currentLore
     *            the live item's lore, which a variant's {@code match-lore} is matched against
     */
    @Nullable
    MenuItemTranslation resolveSlot(@Nullable String languageId, @Nonnull String presetId, int slot,
                                    @Nullable String currentName, @Nonnull List<String> currentLore) {
        if (languageId == null) {
            return null;
        }

        List<MenuItemVariant> variants = variantsFor(languageId, presetId, slot);

        if (variants != null) {
            for (MenuItemVariant variant : variants) {
                Map<Integer, String> captures = variant.capturesFrom(currentName, currentLore);

                if (captures != null) {
                    return variant.translation.withCaptures(captures);
                }
            }
        }

        Map<Integer, MenuItemTranslation> slots = slotsFor(languageId, presetId);
        MenuItemTranslation plain = slots == null ? null : slots.get(slot);
        return plain != null && isStillTheEnglishBaseline(presetId, slot, currentName) ? plain : null;
    }

    /**
     * Whether the item in this slot still reads what the English file says it should.
     *
     * @implNote A plain entry is the translation of the slot's English BASELINE, so applying it to an
     *           item a ticker rewrote would freeze live text: {@code en/menus.yml} names
     *           {@code ADVANCED_STORAGE} slot 4 "Status" while the machine replaces that slot with the
     *           live stored-item line, and the packet rewrite runs on every packet rather than once per
     *           open. A slot whose English entry sets no name has no baseline to compare and still
     *           applies, because it replaces no name.
     */
    private boolean isStillTheEnglishBaseline(@Nonnull String presetId, int slot, @Nullable String currentName) {
        Map<Integer, MenuItemTranslation> english = slotsFor("en", presetId);
        MenuItemTranslation baseline = english == null ? null : english.get(slot);
        String baselineName = baseline == null ? null : baseline.getName();
        return baselineName == null || MenuTextPattern.colourless(baselineName).equals(MenuTextPattern.colourless(currentName));
    }

    private void rememberTranslatableSlot(@Nonnull String presetId, int slot) {
        translatableSlots.computeIfAbsent(presetId, k -> ConcurrentHashMap.newKeySet()).add(slot);
    }

    /**
     * Whether any language translates this slot, and so whether it is worth marking the item in it.
     * Anything else, machine inputs and outputs included, must be left exactly as the machine made it.
     */
    public boolean isTranslatableSlot(@Nonnull String presetId, int slot) {
        Set<Integer> slots = translatableSlots.get(presetId);
        return slots != null && slots.contains(slot);
    }

    @Nullable
    private List<MenuItemVariant> variantsFor(@Nonnull String languageId, @Nonnull String presetId, int slot) {
        Map<String, Map<Integer, List<MenuItemVariant>>> byPreset = variantsByLanguage.get(languageId);
        Map<Integer, List<MenuItemVariant>> slots = byPreset == null ? null : byPreset.get(presetId);
        return slots == null ? null : slots.get(slot);
    }

    /** The slots of a preset that exist only as variants, which a slot-keyed lookup cannot see. */
    @Nonnull
    private Set<Integer> variantSlotsFor(@Nullable String languageId, @Nonnull String presetId) {
        Map<String, Map<Integer, List<MenuItemVariant>>> byPreset =
            languageId == null ? null : variantsByLanguage.get(languageId);
        Map<Integer, List<MenuItemVariant>> slots = byPreset == null ? null : byPreset.get(presetId);
        return slots == null ? Collections.emptySet() : slots.keySet();
    }

    /**
     * Rewrites a stamped decorative menu item into one language, or returns {@code null} when the item
     * carries no menu slot key or nothing resolves for it.
     *
     * @implNote Called from the Netty write thread. It reads the ITEM's own PDC, which
     *           {@code PacketTranslationService.rewriteItem} already does there, and never touches
     *           player PDC or config. A {@code null} return means "send the item unchanged".
     */
    @Nullable
    public ItemStack rewriteStampedItem(@Nullable ItemStack item, @Nullable String languageId) {
        if (item == null || Slimefun.instance() == null) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return null;
        }

        MenuSlotKey key = MenuSlotKey.parse(PdcCompat.getString(meta, Slimefun.getRegistry().getMenuSlotKey()));

        if (key == null) {
            return null;
        }

        String liveName = meta.getDisplayName();
        List<String> liveLore = meta.hasLore() ? meta.getLore() : Collections.<String>emptyList();
        MenuItemTranslation translation = resolveSlot(languageId, key.getPresetId(), key.getSlot(), liveName, liveLore);

        if (translation == null) {
            translation = resolveSlot("en", key.getPresetId(), key.getSlot(), liveName, liveLore);
        }

        if (translation == null) {
            return null;
        }

        ItemStack copy = item.clone();
        ItemMeta copyMeta = copy.getItemMeta();

        if (copyMeta == null) {
            return null;
        }

        if (translation.getName() != null) {
            copyMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', translation.getName()));
        }

        if (!translation.getLore().isEmpty()) {
            List<String> lore = new ArrayList<>();

            for (String line : translation.getLore()) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }

            copyMeta.setLore(lore);
        }

        copy.setItemMeta(copyMeta);
        return copy;
    }

    /**
     * Every slot {@link #applyToMenu} visits: the ones translated for this viewer's language or for
     * English, MINUS every variant slot.
     *
     * @implNote {@link #applyToMenu} writes into the shared inventory, and a variant's match keys are
     *           English. Writing one viewer's language into a variant slot is what made the next viewer
     *           of the same block match nothing and see the first viewer's language, so those slots are
     *           left to the packet layer and the shared inventory stays the English source of truth.
     */
    @Nonnull
    Set<Integer> targetSlotsFor(@Nullable String languageId, @Nonnull String presetId) {
        Set<Integer> targets = new HashSet<>();
        Map<Integer, MenuItemTranslation> playerSlots = slotsFor(languageId, presetId);
        Map<Integer, MenuItemTranslation> englishSlots = slotsFor("en", presetId);

        if (playerSlots != null) {
            targets.addAll(playerSlots.keySet());
        }

        if (englishSlots != null) {
            targets.addAll(englishSlots.keySet());
        }

        targets.removeAll(variantSlotsFor(languageId, presetId));
        targets.removeAll(variantSlotsFor("en", presetId));
        return targets;
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
        String language = languageOf(p);
        Set<Integer> targets = targetSlotsFor(language, presetId);

        if (targets.isEmpty()) {
            return;
        }

        for (int slot : targets) {
            String liveName = displayNameOf(menu.getItemInSlot(slot));
            MenuItemTranslation translation = resolveSlot(language, presetId, slot, liveName);

            if (translation == null) {
                translation = resolveSlot("en", presetId, slot, liveName);
            }

            if (translation != null) {
                applyToSlot(menu, slot, translation);
            }
        }
    }

    @Nullable
    private static String displayNameOf(@Nullable ItemStack item) {
        if (item == null) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getDisplayName();
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

        Map<String, Map<Integer, List<MenuItemVariant>>> englishVariants =
            variantsByLanguage.getOrDefault("en", Collections.<String, Map<Integer, List<MenuItemVariant>>>emptyMap());

        for (Map.Entry<String, Map<Integer, List<MenuItemVariant>>> presetEntry : englishVariants.entrySet()) {
            Map<Integer, MenuItemTranslation> englishSlots = english.get(presetEntry.getKey());

            for (Integer slot : presetEntry.getValue().keySet()) {
                if (englishSlots != null && englishSlots.containsKey(slot)) {
                    continue; // already counted above as a plain slot
                }

                total++;

                if (isEnglish || variantsFor(languageId, presetEntry.getKey(), slot) != null) {
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
