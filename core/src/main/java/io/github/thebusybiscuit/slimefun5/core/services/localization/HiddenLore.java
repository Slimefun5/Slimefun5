package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * Lore lines a Slimefun stack carries that no player ever sees: the packet layer replaces such a stack's lore with
 * its per-viewer render, so a line an addon added at runtime is lost unless it is {@link
 * io.github.thebusybiscuit.slimefun5.api.items.LiveLore}.
 *
 * @implNote A line matching the item's own English render is not reported: items from before the id-only change
 *           still carry that baked lore, and replacing it loses nothing.
 */
public final class HiddenLore {

    private static final String ENGLISH = "en";

    private HiddenLore() {}

    @Nonnull
    public static List<String> of(@Nonnull ItemStack stack, @Nonnull String id) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasLore() || PacketItemRewriter.isInternalChromeId(id)) {
            return Collections.emptyList();
        }
        Set<String> rendered = englishLines(id);
        List<String> hidden = new ArrayList<>();
        for (String line : meta.getLore()) {
            String plain = plain(line);
            if (!plain.isEmpty() && !rendered.contains(plain)) {
                hidden.add(plain);
            }
        }
        return hidden;
    }

    private static Set<String> englishLines(String id) {
        ItemTranslationService.RenderedDisplay display =
            Slimefun.getItemTranslationService().renderForPacket(id, ENGLISH, TranslationConfig.FallbackMode.ENGLISH, true);
        Set<String> lines = new HashSet<>();
        if (display != null) {
            lines.add(plain(display.name));
            for (String line : display.lore) {
                lines.add(plain(line));
            }
        }
        return lines;
    }

    private static String plain(String line) {
        String stripped = ChatColor.stripColor(line);
        return stripped == null ? "" : stripped.trim();
    }
}
