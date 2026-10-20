package io.github.thebusybiscuit.slimefun5.core.checks.registry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.core.checks.Check;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckContext;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckStep;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckThread;
import io.github.thebusybiscuit.slimefun5.core.checks.Findings;
import io.github.thebusybiscuit.slimefun5.core.checks.Stage;
import io.github.thebusybiscuit.slimefun5.core.services.localization.ItemNameStyle;
import io.github.thebusybiscuit.slimefun5.core.services.localization.ItemTiers;
import io.github.thebusybiscuit.slimefun5.core.services.localization.ItemTranslationService;
import io.github.thebusybiscuit.slimefun5.core.services.localization.Language;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * Holds every plugin that styles its names by tier to the rules that styling needs: no language writes a
 * colour or format code into a name, no two of the plugin's items share a name in one language, and every
 * id an {@code item-tiers.yml} declares exists.
 */
public final class ItemTierCheck implements Check {

    private static final Pattern FORMAT_CODE = Pattern.compile("[&§][0-9a-fk-orA-FK-OR]");

    @Override
    public String name() {
        return "item-tier";
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
        Findings findings = context.findings();
        ItemTranslationService translations = Slimefun.getItemTranslationService();
        List<SlimefunItem> styled = new ArrayList<>();
        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            if (translations.getTiers().isStyled(item)) {
                styled.add(item);
            }
        }

        Map<String, TreeSet<String>> codedLanguagesById = new TreeMap<>();
        Map<String, TreeSet<String>> languagesByDuplicate = new TreeMap<>();
        List<CheckStep> steps = new ArrayList<>();
        for (Language language : Slimefun.getLocalization().getLanguages()) {
            steps.add(() -> {
                checkLanguage(language.getId(), styled, translations, codedLanguagesById, languagesByDuplicate);
                return 0;
            });
        }
        steps.add(() -> {
            codedLanguagesById.forEach((id, languages) -> findings.report(name(), id, "writes colour codes into its name in " + languages));
            languagesByDuplicate.forEach((duplicate, languages) -> findings.report(name(), duplicate, "share one name in " + languages));
            reportUnknownIds(findings, translations.getTiers(), translations);
            return 0;
        });
        return steps.iterator();
    }

    private static void checkLanguage(@Nonnull String language, @Nonnull List<SlimefunItem> styled, @Nonnull ItemTranslationService translations,
            @Nonnull Map<String, TreeSet<String>> codedLanguagesById, @Nonnull Map<String, TreeSet<String>> languagesByDuplicate) {
        Map<String, TreeSet<String>> idsByPluginAndName = new TreeMap<>();

        for (SlimefunItem item : styled) {
            String authored = translations.getAuthoredName(language, item.getId());

            if (authored == null) {
                continue;
            }

            if (FORMAT_CODE.matcher(authored).find()) {
                codedLanguagesById.computeIfAbsent(item.getId(), id -> new TreeSet<>()).add(language);
            }

            String key = item.getAddon().getName() + " \"" + ItemNameStyle.plainText(authored) + "\"";
            idsByPluginAndName.computeIfAbsent(key, k -> new TreeSet<>()).add(item.getId());
        }

        idsByPluginAndName.forEach((key, ids) -> {
            if (ids.size() > 1) {
                languagesByDuplicate.computeIfAbsent(String.join(", ", ids), k -> new TreeSet<>()).add(language);
            }
        });
    }

    /**
     * @implNote An item the running version cannot have (Glow Berry Juice on 1.8) is unregistered but still
     *           translated, so only an id that no English entry names either is a typo.
     */
    private void reportUnknownIds(@Nonnull Findings findings, @Nonnull ItemTiers tiers, @Nonnull ItemTranslationService translations) {
        for (String id : new TreeSet<>(tiers.declaredIds())) {
            if (SlimefunItem.getById(id) == null && translations.getAuthoredName("en", id) == null) {
                findings.report(name(), id, "is declared in " + ItemTiers.FILE_NAME + " but no item has that id");
            }
        }
    }
}
