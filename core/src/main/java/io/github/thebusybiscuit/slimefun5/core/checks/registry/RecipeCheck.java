package io.github.thebusybiscuit.slimefun5.core.checks.registry;

import java.util.Iterator;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun5.core.checks.Check;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckContext;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckStep;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckThread;
import io.github.thebusybiscuit.slimefun5.core.checks.Findings;
import io.github.thebusybiscuit.slimefun5.core.checks.Stage;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * Every enabled item's declared recipe can actually be followed: it has a recipe type, it yields something,
 * and no Slimefun ingredient is unregistered or disabled, which would leave the item uncraftable.
 */
public final class RecipeCheck implements Check {

    private static final int ITEMS_PER_STEP = 200;

    @Override
    public String name() {
        return "recipe";
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
        return CheckBatches.stepsOver(Slimefun.getRegistry().getEnabledSlimefunItems(), ITEMS_PER_STEP,
            item -> checkItem(context.findings(), item));
    }

    private void checkItem(Findings findings, SlimefunItem item) {
        RecipeType type = item.getRecipeType();
        if (type == null) {
            findings.report(name(), item.getId(), "has no recipe type");
            return;
        }
        if (type == RecipeType.NULL) {
            return;
        }
        ItemStack output = item.getRecipeOutput();
        if (output == null || output.getType() == Material.AIR || output.getAmount() < 1) {
            findings.report(name(), item.getId(), "has a recipe that yields nothing");
        }
        ItemStack[] recipe = item.getRecipe();
        if (recipe == null) {
            return;
        }
        for (ItemStack ingredient : recipe) {
            checkIngredient(findings, item, ingredient);
        }
    }

    private void checkIngredient(Findings findings, SlimefunItem item, ItemStack ingredient) {
        if (ingredient == null || ingredient.getType() == Material.AIR) {
            return;
        }
        Optional<String> id = Slimefun.getItemDataService().getItemData(ingredient);
        if (!id.isPresent()) {
            return;
        }
        SlimefunItem used = SlimefunItem.getById(id.get());
        if (used == null) {
            findings.report(name(), item.getId(), "needs " + id.get() + ", which is not registered");
        } else if (used.isDisabled()) {
            findings.report(name(), item.getId(), "needs " + id.get() + ", which is disabled on this server");
        }
    }
}
