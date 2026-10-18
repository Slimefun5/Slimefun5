package io.github.thebusybiscuit.slimefun5.core.checks.registry;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun5.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.api.researches.Research;
import io.github.thebusybiscuit.slimefun5.core.checks.Check;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckContext;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckStep;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckThread;
import io.github.thebusybiscuit.slimefun5.core.checks.Findings;
import io.github.thebusybiscuit.slimefun5.core.checks.Stage;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/** Every enabled item has a real stack, sits in a registered item group, and unlocks through a registered research. */
public final class ItemRegistrationCheck implements Check {

    private static final int ITEMS_PER_STEP = 200;

    @Override
    public String name() {
        return "item-registration";
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
        Set<ItemGroup> groups = new HashSet<>(Slimefun.getRegistry().getAllItemGroups());
        Set<Research> researches = new HashSet<>(Slimefun.getRegistry().getResearches());
        return CheckBatches.stepsOver(Slimefun.getRegistry().getEnabledSlimefunItems(), ITEMS_PER_STEP,
            item -> checkItem(context.findings(), item, groups, researches));
    }

    private void checkItem(Findings findings, SlimefunItem item, Set<ItemGroup> groups, Set<Research> researches) {
        ItemStack stack = item.getItem();
        if (stack == null || stack.getType() == Material.AIR) {
            findings.report(name(), item.getId(), "has no item stack");
        }
        if (item.getItemGroup() == null || !groups.contains(item.getItemGroup())) {
            findings.report(name(), item.getId(), "is in an item group that is not registered");
        }
        Research research = item.getResearch();
        if (research != null && !researches.contains(research)) {
            findings.report(name(), item.getId(), "unlocks through research " + research.getKey() + ", which is not registered");
        }
    }
}
