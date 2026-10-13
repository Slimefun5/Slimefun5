package io.github.thebusybiscuit.slimefun5.core.services.localization;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.PdcCompat;

/**
 * Marks the item occupying a decorative block-menu slot with the slot it occupies, so the packet
 * layer can translate it for one viewer without writing any language into the shared inventory.
 *
 * @implNote Deliberately the opposite of {@code ChestMenuUtils.stripTranslationIdentity}, which
 *           removes the ITEM id from a display copy. Both keys may sit on one item: the item id says
 *           which Slimefun item a copy depicts, this one says which menu slot it fills.
 */
public final class MenuSlotStamp {

    private MenuSlotStamp() {}

    /**
     * @return the same item, stamped where the server supports PDC and untouched where it does not
     */
    @Nullable
    public static ItemStack apply(@Nullable ItemStack item, @Nonnull String presetId, int slot) {
        if (item == null || !PdcCompat.isSupported() || Slimefun.instance() == null) {
            return item;
        }

        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        PdcCompat.setString(meta, Slimefun.getRegistry().getMenuSlotKey(), MenuSlotKey.format(presetId, slot));
        item.setItemMeta(meta);
        return item;
    }
}
