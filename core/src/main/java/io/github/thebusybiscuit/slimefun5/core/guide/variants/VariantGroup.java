package io.github.thebusybiscuit.slimefun5.core.guide.variants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;

/**
 * An ordered set of {@link SlimefunItem}s that are the same thing in different flavours - one part shape in
 * every material, one machine in every tier - and that the guide should therefore show in a single slot
 * instead of one slot each.
 * <p>
 * Every member stays a fully registered {@link SlimefunItem} with its own id, recipe and research; grouping
 * changes only how the guide presents them.
 *
 * @see VariantGroupRegistry
 */
public final class VariantGroup {

    private final NamespacedKey key;
    private final List<SlimefunItem> variants;

    /**
     * @param key
     *            This group's unique key
     * @param variants
     *            The group's members, in the order the guide should cycle them; the first is the member
     *            whose position in its {@link io.github.thebusybiscuit.slimefun5.api.items.ItemGroup}
     *            decides where the group's single slot appears
     */
    public VariantGroup(@Nonnull NamespacedKey key, @Nonnull List<SlimefunItem> variants) {
        if (key == null) {
            throw new IllegalArgumentException("A VariantGroup needs a key!");
        }

        if (variants == null || variants.isEmpty()) {
            throw new IllegalArgumentException("A VariantGroup needs at least one variant!");
        }

        this.key = key;
        this.variants = Collections.unmodifiableList(new ArrayList<>(variants));
    }

    @Nonnull
    public NamespacedKey getKey() {
        return key;
    }

    /** The members, in cycle order. Never empty. */
    @Nonnull
    public List<SlimefunItem> getVariants() {
        return variants;
    }

    /**
     * The member that anchors the group's guide slot - the first one registered. The guide draws the group
     * where this member would have appeared and hides the rest.
     */
    @Nonnull
    public SlimefunItem getAnchor() {
        return variants.get(0);
    }

    public int size() {
        return variants.size();
    }

    /** The 1-based position of {@code itemId} within this group, or 0 if it is not a member. */
    public int indexOf(@Nonnull String itemId) {
        for (int i = 0; i < variants.size(); i++) {
            if (variants.get(i).getId().equals(itemId)) {
                return i + 1;
            }
        }

        return 0;
    }

    /**
     * Registers this group so the guide starts collapsing it.
     *
     * @return this group
     */
    @Nonnull
    public VariantGroup register() {
        io.github.thebusybiscuit.slimefun5.implementation.Slimefun.getVariantGroups().add(this);
        return this;
    }
}
