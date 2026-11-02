package io.github.thebusybiscuit.slimefun5.core.checks.scenarios;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.thebusybiscuit.slimefun5.core.checks.packets.StyledText;

/** A stack's name and lore as a client draws them ({@link StyledText}), beside the raw text for a report. */
final class ShownDisplay {

    final String rawName;
    final String name;
    final List<String> rawLore;
    final List<String> lore;

    private ShownDisplay(String rawName, List<String> rawLore) {
        this.rawName = rawName;
        this.name = StyledText.canonical(rawName);
        this.rawLore = rawLore;
        List<String> canonicalLore = new ArrayList<>();
        for (String line : rawLore) {
            canonicalLore.add(StyledText.canonical(line));
        }
        this.lore = canonicalLore;
    }

    @Nonnull
    static ShownDisplay of(@Nonnull ItemStack stack) {
        ItemMeta meta = stack.hasItemMeta() ? stack.getItemMeta() : null;
        String name = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "";
        List<String> lore = meta != null && meta.hasLore() && meta.getLore() != null ? meta.getLore() : Collections.emptyList();
        return new ShownDisplay(name, lore);
    }
}
