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
import io.github.thebusybiscuit.slimefun5.core.checks.packets.TextProblems;
import io.github.thebusybiscuit.slimefun5.core.services.localization.DynamicLoreValues;
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
            classify(item, language, rawId, placeholder);
        }
        if (!rawId.isEmpty()) {
            findings.report(name(), item.getId(), "has no name translation in " + summarise(rawId, languages)
                + " (players see English or the raw id, per translation.fallback)");
        }
        if (!placeholder.isEmpty()) {
            findings.report(name(), item.getId(), "shows an unfilled placeholder in " + summarise(placeholder, languages));
        }
    }

    /**
     * @implNote The per-stack tokens such as {@code %charge%} are filled from the item's own stack, as the packet
     *           layer fills them from the stack it sends, so only a token no stack can fill counts as unfilled.
     */
    private static void classify(SlimefunItem item, String language, List<String> rawId, List<String> placeholder) {
        String id = item.getId();
        RenderedDisplay template = Slimefun.getItemTranslationService().renderForPacket(id, language, FallbackMode.ID, true);
        RenderedDisplay display = template == null ? null : DynamicLoreValues.substitute(item, item.getItem(), template);
        String name = display == null || display.name == null ? "" : ChatColor.stripColor(display.name).trim();
        if (name.isEmpty() || name.equals(id) || TextProblems.looksLikeRawId(name)) {
            rawId.add(language);
        } else if (TextProblems.hasPlaceholder(name) || (display.lore != null && display.lore.stream().anyMatch(ItemNameCheck::loreHasPlaceholder))) {
            placeholder.add(language);
        }
    }

    private static boolean loreHasPlaceholder(String line) {
        return line != null && TextProblems.hasPlaceholder(ChatColor.stripColor(line));
    }

    private static String summarise(List<String> failing, List<String> all) {
        return failing.size() == all.size() ? "every language (" + all.size() + ")"
            : failing.size() + " of " + all.size() + " languages: " + String.join(", ", failing);
    }
}
