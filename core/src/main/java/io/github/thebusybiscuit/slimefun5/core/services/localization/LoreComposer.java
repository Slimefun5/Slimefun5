package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.ChatColor;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.core.attributes.EnergyNetComponent;

/**
 * Composes an item's lore from ordered blocks: Type -> Description -> Stats -> Usage, each separated by
 * a single blank line, empty blocks omitted. Forcing every migrated item through this one structure is
 * what eliminates lore inconsistency. Per-type static {@code %placeholder%} tokens are resolved from the
 * item's attributes; unknown tokens pass through unchanged (Phase 2 adds per-instance live values).
 *
 * Three modes:
 *  - structural blocks present (type/stats/usage) -> compose [Type, Description?, Stats, Usage] only.
 *  - only a description authored -> legacy fallback base + Description? (the Feature-4 behaviour).
 *  - nothing authored -> the legacy/base lore unchanged.
 */
public final class LoreComposer {

    /**
     * The house colour each block renders in when a line carries no colour code of its own, measured from
     * core's own {@code en/items.yml} (Type is {@code &7&o} on all 552 entries; Stats and Usage are the
     * dominant choice in theirs). Defaulting per block rather than to one flat grey is what keeps a
     * runtime-composed display indistinguishable from an authored one.
     */
    private static final String TYPE_COLOR = ChatColor.GRAY.toString() + ChatColor.ITALIC;
    private static final String DESCRIPTION_COLOR = ChatColor.WHITE.toString();
    private static final String STATS_COLOR = ChatColor.DARK_GRAY.toString();
    private static final String USAGE_COLOR = ChatColor.YELLOW.toString();
    private static final String LEGACY_COLOR = ChatColor.GRAY.toString();

    private LoreComposer() {}

    @Nonnull
    public static List<String> compose(@Nonnull SlimefunItem item, @Nonnull List<String> type, @Nonnull List<String> description,
                                       @Nonnull List<String> stats, @Nonnull List<String> usage, @Nonnull List<String> fallbackBase,
                                       boolean includeDescription, @Nullable String languageId) {
        List<String> desc = includeDescription ? description : new ArrayList<String>();

        // Enchantment lines render directly under the Type category (not vanilla's default spot above the
        // lore). EnchantDisplay returns empty unless the item is enchanted AND the vanilla tooltip can be
        // hidden on this version, so this never double-renders.
        List<String> enchantLines = EnchantDisplay.lines(item, languageId);

        boolean hasStructuralBlocks = !type.isEmpty() || !stats.isEmpty() || !usage.isEmpty() || !enchantLines.isEmpty();

        if (hasStructuralBlocks) {
            // Enchantments are their own block after Type, so joinBlocks puts a blank line between the
            // category and its enchantments.
            return normalizeBlankLines(joinBlocks(item, Arrays.asList(type, enchantLines, desc, stats, usage),
                Arrays.asList(TYPE_COLOR, LEGACY_COLOR, DESCRIPTION_COLOR, STATS_COLOR, USAGE_COLOR)));
        }

        if (!description.isEmpty()) {
            return normalizeBlankLines(joinBlocks(item, Arrays.asList(fallbackBase, desc),
                Arrays.asList(LEGACY_COLOR, DESCRIPTION_COLOR)));
        }

        // No authored blocks: the item's own lore IS its description, so the toggle hides it on physical
        // items (the guide passes includeDescription=true, so the guide still shows it).
        return includeDescription ? normalizeBlankLines(renderBlock(item, fallbackBase, LEGACY_COLOR)) : new ArrayList<String>();
    }

    /**
     * Enforces the lore spacing rules on any composed lore: no leading or trailing blank lines, and never
     * two blank lines in a row (a blank being a line that is empty once colour codes/whitespace are stripped,
     * so an authored "&7" spacer counts too). Applied to every compose() result so no item can render with
     * doubled gaps.
     */
    @Nonnull
    private static List<String> normalizeBlankLines(@Nonnull List<String> lore) {
        List<String> out = new ArrayList<>(lore.size());

        for (String line : lore) {
            boolean blank = line == null || ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', line)).trim().isEmpty();

            if (blank) {
                // Drop leading blanks and any blank that follows another blank.
                if (out.isEmpty() || out.get(out.size() - 1).isEmpty()) {
                    continue;
                }

                out.add("");
            } else {
                out.add(line);
            }
        }

        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
            out.remove(out.size() - 1);
        }

        return out;
    }

    /** Concatenates non-empty blocks with one blank line between them. */
    @Nonnull
    private static List<String> joinBlocks(@Nonnull SlimefunItem item, @Nonnull List<List<String>> blocks,
                                           @Nonnull List<String> defaultColors) {
        List<String> out = new ArrayList<>();

        for (int i = 0; i < blocks.size(); i++) {
            List<String> block = blocks.get(i);

            if (block.isEmpty()) {
                continue;
            }

            if (!out.isEmpty()) {
                out.add("");
            }

            out.addAll(renderBlock(item, block, defaultColors.get(i)));
        }

        return out;
    }

    /** Resolves placeholders and translates '&' colour codes for each line of a block. */
    @Nonnull
    private static List<String> renderBlock(@Nonnull SlimefunItem item, @Nonnull List<String> lines,
                                            @Nonnull String defaultColor) {
        List<String> out = new ArrayList<>(lines.size());

        for (String line : lines) {
            String rendered = ChatColor.translateAlternateColorCodes('&', resolvePlaceholders(item, line));

            // A lore line with no leading colour renders in Minecraft's default purple italic, which authors
            // rarely intend (e.g. addon usage lines written without a code). Defaulting to the block's own
            // house colour is also what lets a resolver write plain text and still match every other item.
            if (!rendered.isEmpty() && rendered.charAt(0) != ChatColor.COLOR_CHAR) {
                rendered = defaultColor + rendered;
            }

            out.add(rendered);
        }

        return out;
    }

    /**
     * Resolves per-type static placeholders. Phase 1 supports {@code %capacity%} (energy buffer/capacity);
     * unknown tokens are left intact so authors may write literal values. Guarded so a broken attribute
     * never throws during rendering.
     */
    @Nonnull
    private static String resolvePlaceholders(@Nonnull SlimefunItem item, @Nonnull String line) {
        if (line.indexOf('%') < 0) {
            return line;
        }

        String result = line;

        if (result.contains("%capacity%") && item instanceof EnergyNetComponent) {
            try {
                result = result.replace("%capacity%", String.valueOf(((EnergyNetComponent) item).getCapacity()));
            } catch (Exception | LinkageError ignored) {
                // Leave the token intact if the attribute cannot be read.
            }
        }

        return result;
    }
}
