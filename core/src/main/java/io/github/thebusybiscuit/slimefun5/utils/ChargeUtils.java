package io.github.thebusybiscuit.slimefun5.utils;

import io.github.thebusybiscuit.slimefun5.utils.compatibility.PdcCompat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import org.apache.commons.lang.Validate;
import org.bukkit.ChatColor;
import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun5.core.attributes.Rechargeable;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * This is just a simple helper class to provide static methods to the {@link Rechargeable}
 * interface.
 *
 * @author TheBusyBiscuit
 * @author WalshyDev
 *
 * @see Rechargeable
 *
 */
public final class ChargeUtils {

    private static final String LORE_PREFIX = ChatColors.color("&8\u21E8 &e\u26A1 &7");
    private static final Pattern REGEX = Pattern.compile(ChatColors.color("(&c&o)?" + LORE_PREFIX) + "[0-9.]+ / [0-9.]+ J");

    private ChargeUtils() {}

    /**
     * @implNote Charge is persisted only via {@link NamespacedKey}/{@link PdcCompat} - the item's own
     *           lore is never written here. A live charge value is shown by rendering a {@code %charge%}
     *           token (see {@code DynamicLoreValues}) at packet send time, per viewer; baking it into the
     *           stack's lore would violate the "no baked lore" rule on every recharge/discharge.
     */
    public static void setCharge(@Nonnull ItemMeta meta, float charge, float capacity) {
        Validate.notNull(meta, "Meta cannot be null!");
        Validate.isTrue(charge >= 0, "Charge has to be equal to or greater than 0!");
        Validate.isTrue(capacity > 0, "Capacity has to be greater than 0!");
        Validate.isTrue(charge <= capacity, "Charge may not be bigger than the capacity!");

        BigDecimal decimal = BigDecimal.valueOf(charge).setScale(2, RoundingMode.HALF_UP);
        float value = decimal.floatValue();

        NamespacedKey key = Slimefun.getRegistry().getItemChargeDataKey();
        PdcCompat.set(meta, key, "FLOAT", value);
    }

    public static float getCharge(@Nonnull ItemMeta meta) {
        Validate.notNull(meta, "Meta cannot be null!");

        NamespacedKey key = Slimefun.getRegistry().getItemChargeDataKey();
        Float value = (Float) PdcCompat.get(meta, key, "FLOAT");

        // If persistent data is available, we just return this value
        if (value != null) {
            return value;
        }

        // If no persistent data exists, we will just fall back to the lore
        if (meta.hasLore()) {
            for (String line : meta.getLore()) {
                if (REGEX.matcher(line).matches()) {
                    String data = ChatColor.stripColor(PatternUtils.SLASH_SEPARATOR.split(line)[0].replace(LORE_PREFIX, ""));

                    float loreValue = Float.parseFloat(data);
                    PdcCompat.set(meta, key, "FLOAT", loreValue);
                    return loreValue;
                }
            }
        }

        return 0;
    }
}

