package io.github.thebusybiscuit.slimefun5.core.checks.registry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.github.thebusybiscuit.slimefun5.api.researches.Research;
import io.github.thebusybiscuit.slimefun5.core.checks.Check;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckContext;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckStep;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckThread;
import io.github.thebusybiscuit.slimefun5.core.checks.Findings;
import io.github.thebusybiscuit.slimefun5.core.checks.Stage;
import io.github.thebusybiscuit.slimefun5.core.services.localization.Language;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * Reports every enabled research that some loaded language has no name for.
 *
 * @implNote Reads each language's own file, never the English fallback: a research missing everywhere still
 *           shows a name in game (the code's or the English one), which is how Rune Anvil and Slot Lock stayed
 *           English in every language unnoticed (SF-40).
 */
public final class ResearchNameCheck implements Check {

    private static final int RESEARCHES_PER_STEP = 20;

    @Override
    public String name() {
        return "research-name";
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
        List<Language> languages = new ArrayList<>(Slimefun.getLocalization().getLanguages());
        List<Research> enabled = new ArrayList<>();
        for (Research research : Slimefun.getRegistry().getResearches()) {
            if (research.isEnabled()) {
                enabled.add(research);
            }
        }
        return CheckBatches.stepsOver(enabled, RESEARCHES_PER_STEP, research -> checkResearch(context.findings(), research, languages));
    }

    private void checkResearch(Findings findings, Research research, List<Language> languages) {
        List<String> missing = new ArrayList<>();
        for (Language language : languages) {
            if (Slimefun.getLocalization().getOwnResearchName(language, research.getKey()) == null) {
                missing.add(language.getId());
            }
        }
        if (!missing.isEmpty()) {
            String where = missing.size() == languages.size() ? "every language (" + languages.size() + ")"
                : missing.size() + " of " + languages.size() + " languages: " + String.join(", ", missing);
            findings.report(name(), research.getKey().toString(), "has no name in " + where + " (players see English)");
        }
    }
}
