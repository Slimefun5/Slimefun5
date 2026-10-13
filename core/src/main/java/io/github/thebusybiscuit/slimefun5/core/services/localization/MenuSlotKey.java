package io.github.thebusybiscuit.slimefun5.core.services.localization;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Identifies one decorative slot of one block-menu preset, as carried in a menu item's PDC so the
 * packet layer can translate it per viewer.
 *
 * @implNote Split on the LAST separator: a preset id is an item id and nothing forbids a slash in
 *           one, while the slot is always the final segment.
 */
public final class MenuSlotKey {

    private static final char SEPARATOR = '/';

    private final String presetId;
    private final int slot;

    private MenuSlotKey(@Nonnull String presetId, int slot) {
        this.presetId = presetId;
        this.slot = slot;
    }

    @Nonnull
    public static String format(@Nonnull String presetId, int slot) {
        return presetId + SEPARATOR + slot;
    }

    @Nullable
    public static MenuSlotKey parse(@Nullable String value) {
        if (value == null) {
            return null;
        }

        int separator = value.lastIndexOf(SEPARATOR);

        if (separator <= 0 || separator == value.length() - 1) {
            return null;
        }

        int slot;

        try {
            slot = Integer.parseInt(value.substring(separator + 1));
        } catch (NumberFormatException e) {
            return null;
        }

        return slot < 0 ? null : new MenuSlotKey(value.substring(0, separator), slot);
    }

    @Nonnull
    public String getPresetId() {
        return presetId;
    }

    public int getSlot() {
        return slot;
    }
}
