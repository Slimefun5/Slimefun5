package io.github.thebusybiscuit.slimefun5.core.guide.variants;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.PdcCompat;

/**
 * Marks a guide display copy as "variant {@code n} of {@code total}" so the rendered name can carry a
 * {@code (6/14)} counter.
 *
 * @implNote The counter is NOT written into the stack's display name: names are resolved per viewer from the
 *           item id (see the id-only architecture), so a baked counter would be a second, untranslatable
 *           source of truth. The marker travels on the stack instead and the packet layer appends the
 *           counter after resolving the name, exactly like the live charge/uses tokens.
 */
public final class VariantDisplayMarker {

    private static NamespacedKey key;

    private VariantDisplayMarker() {}

    @Nonnull
    private static NamespacedKey key() {
        if (key == null) {
            key = new NamespacedKey(Slimefun.instance(), "variant_position");
        }

        return key;
    }

    /** Stamps "{@code index}/{@code total}" onto a guide display copy of a variant. */
    public static void mark(@Nonnull ItemStack stack, int index, int total) {
        ItemMeta meta = stack.getItemMeta();

        if (meta == null) {
            return;
        }

        PdcCompat.setString(meta, key(), index + "/" + total);
        stack.setItemMeta(meta);
    }

    /** The "{@code index}/{@code total}" this stack was marked with, or null if it carries no marker. */
    @Nullable
    public static String read(@Nullable ItemMeta meta) {
        if (meta == null || !PdcCompat.isSupported()) {
            return null;
        }

        try {
            return PdcCompat.getString(meta, key());
        } catch (Exception | LinkageError ignored) {
            return null;
        }
    }
}
