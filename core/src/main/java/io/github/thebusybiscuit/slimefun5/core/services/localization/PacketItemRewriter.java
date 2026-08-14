package io.github.thebusybiscuit.slimefun5.core.services.localization;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * The pure, bukkit-only half of the packet translation rewrite: given an already-deserialized
 * {@link ItemStack} and its Slimefun id, decide what its name/lore should become. Deliberately has no
 * dependency on netty/NMS (unlike {@link PacketTranslationService}, which converts the packet's NMS item
 * to/from bukkit around this), so it can be unit-tested without a real server's netty classes on the
 * classpath.
 */
final class PacketItemRewriter {

    private PacketItemRewriter() {}

    /**
     * Mutates {@code bukkit}'s meta (name/lore) in place per the resolved translation for {@code id}, or
     * leaves it untouched for an internal chrome id ({@link #isInternalChromeId}) or a player-renamed item.
     */
    static void applyPacketTranslation(@Nonnull ItemStack bukkit, @Nonnull String id, @Nullable String language,
            @Nonnull TranslationConfig.FallbackMode fallback, boolean includeDescription) {
        if (isInternalChromeId(id)) {
            return;
        }

        // WithItem: passes the actual stack so a per-instance resolver (e.g. SlimeTinker tools, whose
        // name depends on their PDC parts) can compose a per-viewer display; id-keyed items are unaffected.
        ItemTranslationService.RenderedDisplay display =
            Slimefun.getItemTranslationService().renderForPacketWithItem(bukkit, id, language, fallback, includeDescription);
        if (display == null) {
            // The stack carries a Slimefun id but nothing resolves it (an orphaned template: an addon
            // item whose id changed, or an item from an addon that is no longer loaded). Under the id-only
            // architecture such a template has no name/lore, so on 1.20.5+ the client renders the base
            // material plus a raw "minecraft:<id> / N component(s)" debug tooltip. Give it a clean,
            // human-readable name derived from the id instead of leaking that debug readout.
            applyOrphanedTemplateName(bukkit, id);
            return;
        }

        ItemMeta meta = bukkit.getItemMeta();
        if (meta == null) {
            return;
        }
        // A player-renamed item keeps its custom name (only its lore is translated); overwriting the name
        // here would undo the rename for every viewer.
        if (!RenamedItems.isRenamed(meta)) {
            meta.setDisplayName(display.name);
        }
        meta.setLore(display.lore.isEmpty() ? null : display.lore);
        // Vanilla attribute lines (real Attack Damage / Attack Speed) are intentionally left visible so a
        // player can see what a weapon/tool actually does; they render below our composed lore.
        bukkit.setItemMeta(meta);
    }

    /**
     * Internal GUI chrome ids (see {@code ChestMenuUtils}, e.g. {@code _UI_MENU}) mark stacks that are
     * never registered as a {@link SlimefunItem} - they are pure menu decoration, not orphaned items - so
     * the packet layer must leave their code-defined name/lore alone instead of treating the missing
     * registration as an orphaned template and humanizing the id over it.
     */
    static boolean isInternalChromeId(@Nonnull String id) {
        return id.startsWith("_");
    }

    private static void applyOrphanedTemplateName(@Nonnull ItemStack bukkit, @Nonnull String id) {
        ItemMeta meta = bukkit.getItemMeta();
        if (meta == null) {
            return;
        }
        // A player-renamed item keeps its own name; only clean the debug lore for those.
        if (!RenamedItems.isRenamed(meta)) {
            meta.setDisplayName(ChatColor.WHITE + humanizeId(id));
        }
        meta.setLore(null);
        bukkit.setItemMeta(meta);
    }

    /** "CHISELED_POLISHED_BLACKSTONE" / "my_addon:cool_gadget" -> "Chiseled Polished Blackstone" / "Cool Gadget". */
    private static String humanizeId(String id) {
        String bare = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String[] words = bare.replace('-', '_').split("_");
        StringBuilder out = new StringBuilder(bare.length());

        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                out.append(word.substring(1).toLowerCase(java.util.Locale.ROOT));
            }
        }

        return out.length() == 0 ? id : out.toString();
    }
}
