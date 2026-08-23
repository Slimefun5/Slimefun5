package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.core.attributes.Rechargeable;
import io.github.thebusybiscuit.slimefun5.implementation.items.LimitedUseItem;

/**
 * Substitutes per-instance dynamic tokens ({@code %charge%}, {@code %max_charge%}, {@code %uses%},
 * {@code %max_uses%}) into an already-rendered {@link ItemTranslationService.RenderedDisplay}, using the
 * actual {@link ItemStack} being sent to a viewer.
 *
 * @implNote Deliberately runs AFTER {@link ItemTranslationService}'s (id, language) render cache, not
 *           inside it: the cached template still carries the literal token text, since the token's value is
 *           per-instance (two Electric Jetpacks with different charge share one cached template but must
 *           not share a rendered charge). Never mutates the {@code display} passed in - a cache hit is
 *           shared across every viewer of that (id, language), so mutating it in place would leak one
 *           player's charge onto everyone else's tooltip. An item with none of these tokens in its name/lore
 *           is returned completely unchanged, so items without dynamic state pay no extra cost.
 *
 *           <p>Any {@link SlimefunItem} opts in simply by implementing {@link Rechargeable} or extending
 *           {@link LimitedUseItem} and writing the token into its {@code items.yml} entry - this is not
 *           special-cased per item, so addons get it for free.
 */
final class DynamicLoreValues {

    private static final String TOKEN_CHARGE = "%charge%";
    private static final String TOKEN_MAX_CHARGE = "%max_charge%";
    private static final String TOKEN_USES = "%uses%";
    private static final String TOKEN_MAX_USES = "%max_uses%";

    private DynamicLoreValues() {}

    @Nonnull
    static ItemTranslationService.RenderedDisplay substitute(@Nullable SlimefunItem item, @Nonnull ItemStack stack,
            @Nonnull ItemTranslationService.RenderedDisplay display) {
        if (!(item instanceof Rechargeable) && !(item instanceof LimitedUseItem)) {
            return display;
        }

        String newName = hasToken(display.name) ? substituteLine(item, stack, display.name) : display.name;
        List<String> newLore = display.lore;

        for (int i = 0; i < display.lore.size(); i++) {
            String line = display.lore.get(i);

            if (hasToken(line)) {
                if (newLore == display.lore) {
                    newLore = new ArrayList<>(display.lore);
                }

                newLore.set(i, substituteLine(item, stack, line));
            }
        }

        if (newName.equals(display.name) && newLore == display.lore) {
            return display;
        }

        return new ItemTranslationService.RenderedDisplay(newName, newLore);
    }

    private static boolean hasToken(@Nonnull String line) {
        return line.indexOf('%') >= 0;
    }

    @Nonnull
    private static String substituteLine(@Nonnull SlimefunItem item, @Nonnull ItemStack stack, @Nonnull String line) {
        String result = line;

        if (item instanceof Rechargeable) {
            Rechargeable rechargeable = (Rechargeable) item;

            if (result.contains(TOKEN_CHARGE)) {
                result = result.replace(TOKEN_CHARGE, readCharge(rechargeable, stack));
            }

            if (result.contains(TOKEN_MAX_CHARGE)) {
                result = result.replace(TOKEN_MAX_CHARGE, readMaxCharge(rechargeable, stack));
            }
        }

        if (item instanceof LimitedUseItem) {
            LimitedUseItem limitedUseItem = (LimitedUseItem) item;

            if (result.contains(TOKEN_USES)) {
                result = result.replace(TOKEN_USES, readUsesLeft(limitedUseItem, stack));
            }

            if (result.contains(TOKEN_MAX_USES)) {
                result = result.replace(TOKEN_MAX_USES, String.valueOf(limitedUseItem.getMaxUseCount()));
            }
        }

        return result;
    }

    @Nonnull
    private static String readCharge(@Nonnull Rechargeable rechargeable, @Nonnull ItemStack stack) {
        try {
            return formatFloat(rechargeable.getItemCharge(stack));
        } catch (Exception | LinkageError e) {
            return TOKEN_CHARGE; // Leave the token literal if the attribute cannot be read.
        }
    }

    @Nonnull
    private static String readMaxCharge(@Nonnull Rechargeable rechargeable, @Nonnull ItemStack stack) {
        try {
            return formatFloat(rechargeable.getMaxItemCharge(stack));
        } catch (Exception | LinkageError e) {
            return TOKEN_MAX_CHARGE;
        }
    }

    @Nonnull
    private static String readUsesLeft(@Nonnull LimitedUseItem limitedUseItem, @Nonnull ItemStack stack) {
        try {
            return String.valueOf(limitedUseItem.getUsesLeft(stack));
        } catch (Exception | LinkageError e) {
            return TOKEN_USES;
        }
    }

    /**
     * Formats a charge value the same way the previous hardcoded lore literals read ({@code 100} rather
     * than {@code 100.0}, {@code 99.52} kept as-is). A fresh {@link DecimalFormat} instance per call is
     * deliberate: this runs on the Netty thread for potentially many viewers concurrently, and
     * {@link DecimalFormat} is not thread-safe (unlike {@code NumberUtils.getCompactDouble}, which uses a
     * single shared instance and is documented as not safe for this call site).
     */
    @Nonnull
    private static String formatFloat(float value) {
        return new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(value);
    }
}
