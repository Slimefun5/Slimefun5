package io.github.thebusybiscuit.slimefun5.core.checks.registry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.core.checks.Check;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckContext;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckStep;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckThread;
import io.github.thebusybiscuit.slimefun5.core.checks.Stage;
import io.github.thebusybiscuit.slimefun5.core.services.localization.ItemTranslationService;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * Holds the English name of every item whose plugin styles its names by tier to {@link EnglishNameLint}.
 */
public final class ItemNameLintCheck implements Check {

    private static final int ITEMS_PER_STEP = 200;

    @Override
    public String name() {
        return "item-name-lint";
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
        ItemTranslationService translations = Slimefun.getItemTranslationService();
        List<SlimefunItem> styled = new ArrayList<>();

        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            if (translations.getTiers().isStyled(item)) {
                styled.add(item);
            }
        }

        return CheckBatches.stepsOver(styled, ITEMS_PER_STEP, item -> {
            String english = translations.getAuthoredName("en", item.getId());

            if (english != null) {
                for (String problem : EnglishNameLint.problems(english)) {
                    context.findings().report(name(), item.getId(), "English name \"" + english + "\" " + problem);
                }
            }
        });
    }
}
