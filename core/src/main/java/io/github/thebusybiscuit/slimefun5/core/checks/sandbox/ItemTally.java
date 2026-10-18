package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** How many of each distinct item a set of slots holds, where items count as the same when they stack. */
public final class ItemTally {

    private final List<ItemStack> kinds = new ArrayList<>();
    private final List<Long> counts = new ArrayList<>();

    public ItemTally add(ItemStack item) {
        if (!InventoryClicks.present(item)) {
            return this;
        }
        for (int i = 0; i < kinds.size(); i++) {
            if (kinds.get(i).isSimilar(item)) {
                counts.set(i, counts.get(i) + item.getAmount());
                return this;
            }
        }
        ItemStack kind = item.clone();
        kind.setAmount(1);
        kinds.add(kind);
        counts.add((long) item.getAmount());
        return this;
    }

    public ItemTally addAll(ItemStack[] items) {
        for (ItemStack item : items) {
            add(item);
        }
        return this;
    }

    public ItemTally addAll(Iterable<ItemStack> items) {
        for (ItemStack item : items) {
            add(item);
        }
        return this;
    }

    /** Each kind whose count differs from {@code before}, as {@code "+2 IRON_INGOT"} or {@code "-1 IRON_INGOT \"name\""}. */
    public List<String> changesSince(ItemTally before) {
        List<String> changes = new ArrayList<>();
        for (int i = 0; i < kinds.size(); i++) {
            long difference = counts.get(i) - before.countOf(kinds.get(i));
            if (difference != 0) {
                changes.add((difference > 0 ? "+" : "") + difference + " " + describe(kinds.get(i)));
            }
        }
        for (int i = 0; i < before.kinds.size(); i++) {
            if (countOf(before.kinds.get(i)) == 0) {
                changes.add("-" + before.counts.get(i) + " " + describe(before.kinds.get(i)));
            }
        }
        return changes;
    }

    private long countOf(ItemStack kind) {
        for (int i = 0; i < kinds.size(); i++) {
            if (kinds.get(i).isSimilar(kind)) {
                return counts.get(i);
            }
        }
        return 0;
    }

    private static String describe(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return item.getType() + " \"" + meta.getDisplayName() + "\"";
        }
        return item.getType().toString();
    }
}
