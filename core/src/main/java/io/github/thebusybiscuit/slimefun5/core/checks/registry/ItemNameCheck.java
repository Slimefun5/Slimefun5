package io.github.thebusybiscuit.slimefun5.core.checks.registry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.bukkit.ChatColor;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.core.checks.Check;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckContext;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckStep;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckThread;
import io.github.thebusybiscuit.slimefun5.core.checks.Findings;
import io.github.thebusybiscuit.slimefun5.core.checks.Stage;
import io.github.thebusybiscuit.slimefun5.core.services.localization.ItemTranslationService.RenderedDisplay;
import io.github.thebusybiscuit.slimefun5.core.services.localization.Language;
import io.github.thebusybiscuit.slimefun5.core.services.localization.TranslationConfig.FallbackMode;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * Renders every enabled item in every loaded language exactly as the packet layer shows it to a player, and
 * reports the languages in which a player would see the raw id or an unfilled placeholder.
 *
 * @implNote Renders with the {@code ID} fallback, so a missing translation shows as the id instead of being
 *           hidden behind English.
 */
public final class ItemNameCheck implements Check {

    private static final int ITEMS_PER_STEP = 25;

    @Override
    public String name() {
        return "item-name";
    }

    @Override
    public Stage stage() {
        return Stage.LOW;
    }

    @Override
    public CheckThread thread() {
        return CheckThread.MAIN;
    }

    @Override
    public Iterator<CheckStep> steps(CheckContext context) {
        List<String> languages = new ArrayList<>();
        for (Language language : Slimefun.getLocalization().getLanguages()) {
            languages.add(language.getId());
        }
        return CheckBatches.stepsOver(Slimefun.getRegistry().getEnabledSlimefunItems(), ITEMS_PER_STEP,
            item -> checkItem(context.findings(), item, languages));
    }

    private void checkItem(Findings findings, SlimefunItem item, List<String> languages) {
        List<String> rawId = new ArrayList<>();
        List<String> placeholder = new ArrayList<>();
        for (String language : languages) {
            classify(item.getId(), language, rawId, placeholder);
        }
        if (!rawId.isEmpty()) {
            findings.report(name(), item.getId(), "has no name translation in " + summarise(rawId, languages)
                + " (players see English or the raw id, per translation.fallback)");
        }
        if (!placeholder.isEmpty()) {
            findings.report(name(), item.getId(), "shows an unfilled placeholder in " + summarise(placeholder, languages));
        }
    }

    private static void classify(String id, String language, List<String> rawId, List<String> placeholder) {
        RenderedDisplay display = Slimefun.getItemTranslationService().renderForPacket(id, language, FallbackMode.ID, true);
        String name = display == null || display.name == null ? "" : ChatColor.stripColor(display.name).trim();
        if (name.isEmpty() || name.equals(id)) {
            rawId.add(language);
        } else if (hasPlaceholder(name) || (display.lore != null && display.lore.stream().anyMatch(ItemNameCheck::hasPlaceholder))) {
            placeholder.add(language);
        }
    }

    /** {@code {0}}-style message arguments and {@code %MOB%}-style family tokens both mean a template went unfilled. */
    private static boolean hasPlaceholder(String text) {
        return text.matches(".*\\{[0-9a-zA-Z_]+\\}.*") || text.matches(".*%[A-Z_]+%.*");
    }

    private static String summarise(List<String> failing, List<String> all) {
        return failing.size() == all.size() ? "every language (" + all.size() + ")"
            : failing.size() + " of " + all.size() + " languages: " + String.join(", ", failing);
    }
}
