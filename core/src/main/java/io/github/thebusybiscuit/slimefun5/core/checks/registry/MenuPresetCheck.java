package io.github.thebusybiscuit.slimefun5.core.checks.registry;

import java.util.Iterator;
import java.util.Map;

import org.bukkit.ChatColor;

import io.github.thebusybiscuit.slimefun5.core.checks.Check;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckContext;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckStep;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckThread;
import io.github.thebusybiscuit.slimefun5.core.checks.Findings;
import io.github.thebusybiscuit.slimefun5.core.checks.Stage;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;

/**
 * Every registered machine menu has a title and, when it fixes its own size, one a chest inventory can have.
 *
 * @implNote The stored title is not what a player reads: under the id-only rule it is the machine's baked
 *           name, and core translates it per viewer when the menu opens, so the title each viewer is shown
 *           is checked separately.
 */
public final class MenuPresetCheck implements Check {

    /** {@link BlockMenuPreset#getSize()} before the size is inferred from the slots in use. */
    private static final int INFERRED_SIZE = -1;
    private static final int PRESETS_PER_STEP = 100;

    @Override
    public String name() {
        return "menu-preset";
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
        return CheckBatches.stepsOver(Slimefun.getRegistry().getMenuPresets().entrySet(), PRESETS_PER_STEP,
            entry -> checkPreset(context.findings(), entry));
    }

    private void checkPreset(Findings findings, Map.Entry<String, BlockMenuPreset> entry) {
        BlockMenuPreset preset = entry.getValue();
        String title = preset.getTitle() == null ? "" : ChatColor.stripColor(preset.getTitle()).trim();
        if (title.isEmpty()) {
            findings.report(name(), entry.getKey(), "has an empty title");
        }
        int size = preset.getSize();
        if (size != INFERRED_SIZE && (size < 9 || size > 54 || size % 9 != 0)) {
            findings.report(name(), entry.getKey(), "has a size of " + size + ", which no chest inventory has");
        }
    }
}
