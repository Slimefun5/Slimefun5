package io.github.thebusybiscuit.slimefun5.implementation.setup;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun5.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun5.core.guide.categories.ItemTypeClassifier;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.github.thebusybiscuit.slimefun5.api.events.SlimefunItemRegistryFinalizedEvent;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun5.implementation.items.multiblocks.GrindStone;
import io.github.thebusybiscuit.slimefun5.implementation.items.multiblocks.MakeshiftSmeltery;
import io.github.thebusybiscuit.slimefun5.implementation.items.multiblocks.OreCrusher;
import io.github.thebusybiscuit.slimefun5.implementation.items.multiblocks.Smeltery;
import io.github.thebusybiscuit.slimefun5.utils.JsonUtils;

import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;

public final class PostSetup {

    /** An ALL-CAPS token with no spaces: what a Slimefun id looks like once it leaks into a label. */
    private static final Pattern PLACEHOLDER_LABEL = Pattern.compile("[A-Z0-9][A-Z0-9_]*");

    private PostSetup() {}

    public static void setupWiki() {
        Slimefun.logger().log(Level.INFO, "Loading Wiki pages...");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Slimefun.class.getResourceAsStream("/wiki.json"), StandardCharsets.UTF_8))) {
            JsonElement element = JsonUtils.parseString(reader.lines().collect(Collectors.joining("")));
            JsonObject json = element.getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                SlimefunItem item = SlimefunItem.getById(entry.getKey());

                if (item != null) {
                    item.addOfficialWikipage(entry.getValue().getAsString());
                }
            }
        } catch (IOException e) {
            Slimefun.logger().log(Level.SEVERE, "Failed to load wiki.json file", e);
        }
    }

    /**
     * Warns about any item group whose icon carries no real label, so it would render in the guide as a
     * bare id ("DUMMY_ID", "MY_GROUP_ICON") instead of a category name.
     *
     * @implNote An addon hits this by building the icon with {@code SlimefunItemStack}, whose display name
     *           is always overwritten with the raw id (the "name is always the id" rule). Category icons
     *           are decoration and belong in a {@code CustomItemStack}, or need an item-group translation.
     *           Reported rather than corrected: only the addon knows the intended name.
     */
    private static void lintItemGroupLabels() {
        for (ItemGroup group : Slimefun.getRegistry().getAllItemGroups()) {
            try {
                String name = group.getUnlocalizedName();

                if (name == null || name.trim().isEmpty()) {
                    Slimefun.logger().log(Level.WARNING,
                        "Item group {0} has no label at all, so the guide shows its raw material - give its icon a display name.",
                        group.getKey());
                } else if (PLACEHOLDER_LABEL.matcher(name).matches()) {
                    Slimefun.logger().log(Level.WARNING,
                        "Item group {0} shows the placeholder label \"{1}\" - build its icon with CustomItemStack (a SlimefunItemStack icon is renamed to its id), or register an item-group translation.",
                        new Object[] { group.getKey(), name });
                }
            } catch (Exception | LinkageError ignored) {
                // A broken group must not stop the boot lint.
            }
        }
    }

    /**
     * Reports, per addon, how many of its items land in Misc because nothing said where they belong.
     *
     * @implNote Summarised per addon rather than logged per item: a large addon would otherwise print
     *           hundreds of lines. Items are still shown in the guide either way, so this is advice, not
     *           an error. An addon fixes it with {@code SlimefunItem#setGuideType} or by giving its item
     *           group a category; the classifier already handles the obvious cases (armor, machines).
     */
    private static void lintUncategorizedItems() {
        Map<String, Integer> uncategorized = new java.util.TreeMap<>();

        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            try {
                if (item.getAddon() == null || item.isHidden()) {
                    continue;
                }

                if (ItemTypeClassifier.classify(item) == null) {
                    uncategorized.merge(item.getAddon().getName(), 1, Integer::sum);
                }
            } catch (Exception | LinkageError ignored) {
                // A broken item must not stop the boot lint.
            }
        }

        for (Map.Entry<String, Integer> entry : uncategorized.entrySet()) {
            Slimefun.logger().log(Level.WARNING,
                "[Guide] {0} of {1}''s items have no guide category and fall back to Misc. Set one with SlimefunItem#setGuideType or on their item group.",
                new Object[] { entry.getValue(), entry.getKey() });
        }
    }

    /**
     * Pulls every installed addon's bundled wiki content into the shared {@code WikiText}.
     *
     * @implNote Driven from here rather than from each addon's {@code onEnable} so an addon gets its wiki
     *           topics listed without shipping a registration call, which is what makes the feature work
     *           for third-party addons too. Runs once, after every addon has enabled.
     */
    private static void loadAddonWikis() {
        for (org.bukkit.plugin.Plugin addon : Slimefun.getInstalledAddons()) {
            if (addon instanceof org.bukkit.plugin.java.JavaPlugin) {
                try {
                    Slimefun.getWikiText().registerWiki((org.bukkit.plugin.java.JavaPlugin) addon);
                } catch (Exception | LinkageError x) {
                    Slimefun.logger().log(Level.WARNING, x, () -> "Could not load the wiki content of addon " + addon.getName());
                }
            }
        }
    }

    public static void loadItems() {
        Iterator<SlimefunItem> iterator = Slimefun.getRegistry().getEnabledSlimefunItems().iterator();

        while (iterator.hasNext()) {
            SlimefunItem item = iterator.next();

            if (item == null) {
                Slimefun.logger().log(Level.WARNING, "Removed bugged Item ('NULL?')");
                iterator.remove();
            } else {
                try {
                    item.load();
                } catch (Exception | LinkageError x) {
                    item.error("Failed to properly load this Item", x);
                }
            }
        }

        Bukkit.getPluginManager().callEvent(new SlimefunItemRegistryFinalizedEvent());

        loadAddonWikis();
        lintItemGroupLabels();
        lintUncategorizedItems();
        loadOreGrinderRecipes();
        loadSmelteryRecipes();

        CommandSender sender = Bukkit.getConsoleSender();

        int total = Slimefun.getRegistry().getEnabledSlimefunItems().size();
        int slimefunOnly = countNonAddonItems();

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GREEN + "######################### - Slimefun v" + Slimefun.getVersion() + " - #########################");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GREEN + "Successfully loaded " + total + " Items and " + Slimefun.getRegistry().getResearches().size() + " Researches");
        sender.sendMessage(ChatColor.GREEN + "( " + slimefunOnly + " Items from Slimefun, " + (total - slimefunOnly) + " Items from " + Slimefun.getInstalledAddons().size() + " Addons )");

        // Report every addon in one uniform format instead of each printing its own banner. Counts are
        // keyed by addon name: getInstalledAddons() yields Plugins, but an item's addon is a SlimefunAddon.
        java.util.Map<String, Integer> addonItemCounts = new java.util.HashMap<>();

        for (io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem sfItem : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            io.github.thebusybiscuit.slimefun5.api.SlimefunAddon owningAddon = sfItem.getAddon();

            if (owningAddon != null) {
                addonItemCounts.merge(owningAddon.getName(), 1, Integer::sum);
            }
        }

        java.util.List<org.bukkit.plugin.Plugin> installedAddons = new java.util.ArrayList<>(Slimefun.getInstalledAddons());
        installedAddons.sort(java.util.Comparator.comparing(org.bukkit.plugin.Plugin::getName, String.CASE_INSENSITIVE_ORDER));

        if (!installedAddons.isEmpty()) {
            sender.sendMessage("");

            for (org.bukkit.plugin.Plugin addon : installedAddons) {
                sender.sendMessage(ChatColor.GREEN + "  - " + addon.getName() + " v" + addon.getDescription().getVersion() + ChatColor.GRAY + " (" + addonItemCounts.getOrDefault(addon.getName(), 0) + " items)");
            }
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GREEN + "Slimefun is an Open-Source project that is kept alive by a large community.");
        sender.sendMessage(ChatColor.GREEN + "Consider helping us maintain this project by contributing on GitHub!");

        if (Slimefun.getUpdater().getBranch().isOfficial()) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.GREEN + " - Source Code:  https://github.com/Slimefun5/Slimefun5");
            sender.sendMessage(ChatColor.GREEN + " - Wiki:         https://github.com/Slimefun5/Slimefun5/wiki");
            sender.sendMessage(ChatColor.GREEN + " - Addons:       https://github.com/Slimefun5/Slimefun5/wiki/Addons");
            sender.sendMessage(ChatColor.GREEN + " - Bug Reports:  https://github.com/Slimefun5/Slimefun5/issues");
            sender.sendMessage(ChatColor.GREEN + " - Discord:      https://discord.gg/CbBYZBEWdR");
        } else {
            sender.sendMessage(ChatColor.GREEN + " - UNOFFICIALLY MODIFIED BUILD - NO OFFICIAL SUPPORT GIVEN");
        }

        sender.sendMessage("");

        Slimefun.getItemCfg().save();
        Slimefun.getResearchCfg().save();
        Slimefun.getRegistry().setAutoLoadingMode(true);
    }

    /**
     * This method counts the amount of {@link SlimefunItem SlimefunItems} registered
     * by Slimefun itself and not by any addons.
     * 
     * @return The amount of {@link SlimefunItem SlimefunItems} added by Slimefun itself
     */
    private static int countNonAddonItems() {
        // @formatter:off
        return (int) Slimefun.getRegistry().getEnabledSlimefunItems().stream()
                        .filter(item -> item.getAddon() instanceof Slimefun)
                        .count();
        // @formatter:on
    }

    private static void loadOreGrinderRecipes() {
        List<ItemStack[]> grinderRecipes = new ArrayList<>();

        GrindStone grinder = (GrindStone) SlimefunItems.GRIND_STONE.getItem();
        if (grinder != null) {
            ItemStack[] input = null;

            for (ItemStack[] recipe : grinder.getRecipes()) {
                if (input == null) {
                    input = recipe;
                } else {
                    if (input[0] != null && recipe[0] != null) {
                        grinderRecipes.add(new ItemStack[] { input[0], recipe[0] });
                    }

                    input = null;
                }
            }
        }

        OreCrusher crusher = (OreCrusher) SlimefunItems.ORE_CRUSHER.getItem();
        if (crusher != null) {
            ItemStack[] input = null;

            for (ItemStack[] recipe : crusher.getRecipes()) {
                if (input == null) {
                    input = recipe;
                } else {
                    if (input[0] != null && recipe[0] != null) {
                        grinderRecipes.add(new ItemStack[] { input[0], recipe[0] });
                    }

                    input = null;
                }
            }
        }

        // Favour 8 Cobblestone -> 1 Sand Recipe over 1 Cobblestone -> 1 Gravel Recipe
        Stream<ItemStack[]> stream = grinderRecipes.stream();

        if (!Slimefun.getCfg().getBoolean("options.legacy-ore-grinder")) {
            stream = stream.sorted((a, b) -> Integer.compare(b[0].getAmount(), a[0].getAmount()));
        }

        stream.forEach(recipe -> registerMachineRecipe("ELECTRIC_ORE_GRINDER", 4, new ItemStack[] { recipe[0] }, new ItemStack[] { recipe[1] }));
    }

    private static void loadSmelteryRecipes() {
        Smeltery smeltery = (Smeltery) SlimefunItems.SMELTERY.getItem();

        if (smeltery != null && !smeltery.isDisabled()) {
            MakeshiftSmeltery makeshiftSmeltery = ((MakeshiftSmeltery) SlimefunItems.MAKESHIFT_SMELTERY.getItem());
            ItemStack[] input = null;

            for (ItemStack[] output : smeltery.getRecipes()) {
                if (input == null) {
                    input = output;
                } else {
                    if (input[0] != null && output[0] != null) {
                        addSmelteryRecipe(input, output, makeshiftSmeltery);
                    }

                    input = null;
                }
            }

            for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
                if (item instanceof AContainer) {
                    AContainer machine = (AContainer) item;                    if (machine.getMachineIdentifier().equals("ELECTRIC_SMELTERY")) {
                        List<MachineRecipe> recipes = machine.getMachineRecipes();
                        Collections.sort(recipes, Comparator.comparingInt(recipe -> recipe == null ? 0 : -recipe.getInput().length));
                    }
                }
            }
        }
    }

    private static void addSmelteryRecipe(ItemStack[] input, ItemStack[] output, MakeshiftSmeltery makeshiftSmeltery) {
        List<ItemStack> ingredients = new ArrayList<>();

        // Filter out 'null' items
        for (ItemStack item : input) {
            if (item != null) {
                ingredients.add(item);
            }
        }

        // We want to redirect Dust to Ingot Recipes
        if (ingredients.size() == 1 && isDust(ingredients.get(0))) {
            makeshiftSmeltery.addRecipe(new ItemStack[] { ingredients.get(0) }, output[0]);

            registerMachineRecipe("ELECTRIC_INGOT_FACTORY", 8, new ItemStack[] { ingredients.get(0) }, new ItemStack[] { output[0] });
            registerMachineRecipe("ELECTRIC_INGOT_PULVERIZER", 3, new ItemStack[] { output[0] }, new ItemStack[] { ingredients.get(0) });
        } else {
            registerMachineRecipe("ELECTRIC_SMELTERY", 12, ingredients.toArray(new ItemStack[0]), new ItemStack[] { output[0] });
        }
    }

    private static boolean isDust(@Nonnull ItemStack item) {
        SlimefunItem sfItem = SlimefunItem.getByItem(item);
        return sfItem != null && sfItem.getId().endsWith("_DUST");
    }

    private static void registerMachineRecipe(String machine, int seconds, ItemStack[] input, ItemStack[] output) {
        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            if (item instanceof AContainer && ((AContainer) item).getMachineIdentifier().equals(machine)) {
                AContainer container = (AContainer) item;                container.registerRecipe(seconds, input, output);
            }
        }
    }
}

