package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

/** {@code Metadatable} for simulated blocks and players, with Bukkit's one-value-per-plugin rule. */
final class MetadataStore {

    private final Map<String, List<MetadataValue>> values = new HashMap<>();

    void setMetadata(String key, MetadataValue value) {
        List<MetadataValue> list = values.computeIfAbsent(key, ignored -> new ArrayList<>());
        list.removeIf(existing -> existing.getOwningPlugin() == value.getOwningPlugin());
        list.add(value);
    }

    List<MetadataValue> getMetadata(String key) {
        List<MetadataValue> list = values.get(key);
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    boolean hasMetadata(String key) {
        List<MetadataValue> list = values.get(key);
        return list != null && !list.isEmpty();
    }

    void removeMetadata(String key, Plugin plugin) {
        List<MetadataValue> list = values.get(key);
        if (list == null) {
            return;
        }
        for (Iterator<MetadataValue> iterator = list.iterator(); iterator.hasNext();) {
            if (iterator.next().getOwningPlugin() == plugin) {
                iterator.remove();
            }
        }
    }
}
