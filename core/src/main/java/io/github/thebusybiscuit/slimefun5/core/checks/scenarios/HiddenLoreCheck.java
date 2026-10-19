package io.github.thebusybiscuit.slimefun5.core.checks.scenarios;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun5.core.checks.Check;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckContext;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckStep;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckThread;
import io.github.thebusybiscuit.slimefun5.core.checks.Stage;
import io.github.thebusybiscuit.slimefun5.core.services.localization.HiddenLore;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * Reads every slot of every placed and ticked machine menu for Slimefun stacks whose lore the packet layer replaces,
 * which is lore an addon wrote at runtime that no player ever sees.
 *
 * @implNote The harness feeds no item packets, so this is how a harness run finds what the passive packet watch
 *           reports on a live server; both decide through {@link HiddenLore}.
 */
public final class HiddenLoreCheck implements Check {

    private static final String NAME = "hidden-lore";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Stage stage() {
        return Stage.MEDIUM;
    }

    @Override
    public CheckThread thread() {
        return CheckThread.MAIN;
    }

    @Override
    public Iterator<CheckStep> steps(CheckContext context) {
        MachinePlacement placement = MachinePlacement.in(context.sandbox());
        StepQueue steps = new StepQueue();
        placement.planInto(steps, context);
        steps.thenPlanned(() -> {
            List<CheckStep> planned = new ArrayList<>();
            for (MachinePlacement.PlacedMenu placed : placement.menus()) {
                planned.add(() -> {
                    context.describe(NAME + " " + placed.preset.getID());
                    scan(context, placed);
                    return 0;
                });
            }
            return planned;
        });
        return steps;
    }

    private static void scan(CheckContext context, MachinePlacement.PlacedMenu placed) {
        ItemStack[] contents = placed.menu.toInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            Optional<String> id = stack == null ? Optional.empty() : Slimefun.getItemDataService().getItemData(stack);
            if (!id.isPresent()) {
                continue;
            }
            List<String> hidden = HiddenLore.of(stack, id.get());
            if (!hidden.isEmpty()) {
                context.findings().report(NAME, placed.preset.getID(),
                    "slot " + slot + " shows " + id.get() + " with lore no player sees: \"" + hidden.get(0) + "\"; write it as LiveLore");
            }
        }
    }
}
