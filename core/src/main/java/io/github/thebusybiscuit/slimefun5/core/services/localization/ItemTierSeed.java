package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import javax.annotation.Nonnull;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.core.balance.BalanceService;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * Writes what a plugin's {@code item-tiers.yml} is seeded from: every enabled item with its plugin, effort,
 * power and English name, one tab-separated row each.
 */
public final class ItemTierSeed {

    private ItemTierSeed() {}

    public static void write(@Nonnull File file) {
        BalanceService balance = BalanceService.instance();
        ItemTranslationService translations = Slimefun.getItemTranslationService();

        try (PrintWriter out = new PrintWriter(file, StandardCharsets.UTF_8.name())) {
            out.println("plugin\tid\teffort\tpower\ttrivial\ttier\tname");

            for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
                ItemTranslationService.RenderedDisplay display = translations.renderForPacket(item.getId(), "en", TranslationConfig.FallbackMode.ENGLISH, false);
                String name = display != null ? display.name : null;
                out.println(String.join("\t", item.getAddon().getName(), item.getId(), String.valueOf(balance.effortOf(item)),
                    String.valueOf(balance.scoreOf(item).getScore()), String.valueOf(balance.isTrivial(item)),
                    item.getItemTier().name().toLowerCase(java.util.Locale.ROOT), ItemNameStyle.plainText(name != null ? name : item.getId())));
            }
        } catch (IOException | RuntimeException e) {
            Slimefun.logger().log(Level.WARNING, "Could not write {0}: {1}", new Object[] { file, e.getMessage() });
        }
    }
}
