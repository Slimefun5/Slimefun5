package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * What an {@link ItemTextResolver} contributes for one item: an optional display name plus the same
 * ordered lore blocks an {@code items.yml} entry authors. The resolver never returns finished lore -
 * {@link LoreComposer} turns these blocks into it - so a runtime-generated display is laid out and
 * coloured exactly like every static one.
 *
 * <p>
 * Every field is optional, and {@code null} means "I do not own this block": the item's authored
 * {@code items.yml} value is kept for it. That is what makes a resolver widen an item rather than
 * replace it - a per-instance resolver can inject the one block only it can know (a tinker part's
 * material, say) and leave the authored type and description untouched.
 *
 * <p>
 * Block lines should normally be written WITHOUT a colour code so each one takes its block's house
 * colour. Write a colour only where it carries meaning (a green bonus beside a red penalty).
 */
public final class ItemTextBlocks {

    private final String name;
    private final List<String> type;
    private final List<String> description;
    private final List<String> stats;
    private final List<String> usage;

    private ItemTextBlocks(@Nullable String name, @Nullable List<String> type, @Nullable List<String> description,
                           @Nullable List<String> stats, @Nullable List<String> usage) {
        this.name = name;
        this.type = copy(type);
        this.description = copy(description);
        this.stats = copy(stats);
        this.usage = copy(usage);
    }

    @Nullable
    private static List<String> copy(@Nullable List<String> lines) {
        return lines == null ? null : Collections.unmodifiableList(new ArrayList<>(lines));
    }

    /**
     * @param name
     *            the display name (with '&amp;' colour codes), or {@code null} to keep the authored one
     * @param type
     *            the Type block, or {@code null} to keep the authored one
     * @param description
     *            the Description block, or {@code null} to keep the authored one
     * @param stats
     *            the Stats block, or {@code null} to keep the authored one
     * @param usage
     *            the Usage block, or {@code null} to keep the authored one
     *
     * @return the contribution
     */
    @Nonnull
    public static ItemTextBlocks of(@Nullable String name, @Nullable List<String> type, @Nullable List<String> description,
                                    @Nullable List<String> stats, @Nullable List<String> usage) {
        return new ItemTextBlocks(name, type, description, stats, usage);
    }

    /** A contribution that only renames the item and leaves every lore block as authored. */
    @Nonnull
    public static ItemTextBlocks name(@Nonnull String name) {
        return new ItemTextBlocks(name, null, null, null, null);
    }

    @Nullable
    String getName() {
        return name;
    }

    @Nullable
    List<String> getType() {
        return type;
    }

    @Nullable
    List<String> getDescription() {
        return description;
    }

    @Nullable
    List<String> getStats() {
        return stats;
    }

    @Nullable
    List<String> getUsage() {
        return usage;
    }
}
