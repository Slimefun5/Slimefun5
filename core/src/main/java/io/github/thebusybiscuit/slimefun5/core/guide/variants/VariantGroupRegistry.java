package io.github.thebusybiscuit.slimefun5.core.guide.variants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;

/**
 * Holds every registered {@link VariantGroup} and answers the two questions the guide asks while laying out
 * an item list: is this item part of a group, and is it the member that anchors the group's slot.
 */
public class VariantGroupRegistry {

    private final Map<String, VariantGroup> byKey = new ConcurrentHashMap<>();

    /** Member item id -> its group, so the guide's layout loop is a map lookup per item. */
    private final Map<String, VariantGroup> byMember = new ConcurrentHashMap<>();

    /**
     * Registers {@code group}. A member already claimed by another group is skipped rather than stolen, so a
     * double registration cannot silently break the first group's layout.
     */
    public void add(@Nonnull VariantGroup group) {
        byKey.put(group.getKey().toString(), group);

        for (SlimefunItem variant : group.getVariants()) {
            byMember.putIfAbsent(variant.getId(), group);
        }
    }

    /** The group {@code itemId} belongs to, or null if it is an ordinary standalone item. */
    @Nullable
    public VariantGroup getGroup(@Nonnull String itemId) {
        return byMember.get(itemId);
    }

    /**
     * Whether {@code itemId} is a group member that the guide should NOT give its own slot - i.e. it is in a
     * group but is not that group's anchor.
     */
    public boolean isCollapsedMember(@Nonnull String itemId) {
        VariantGroup group = byMember.get(itemId);
        return group != null && !group.getAnchor().getId().equals(itemId);
    }

    @Nonnull
    public Collection<VariantGroup> getGroups() {
        return Collections.unmodifiableCollection(new ArrayList<VariantGroup>(byKey.values()));
    }

    /** Drops every registration (reload). */
    public void clear() {
        byKey.clear();
        byMember.clear();
    }

    /** The registered groups' keys, for diagnostics. */
    @Nonnull
    public List<String> getKeys() {
        return Collections.unmodifiableList(new ArrayList<String>(byKey.keySet()));
    }
}
