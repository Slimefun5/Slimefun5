package io.github.thebusybiscuit.slimefun5.core.guide.categories;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Material;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun5.core.multiblocks.MultiBlockMachine;
import io.github.thebusybiscuit.slimefun5.implementation.items.armor.SlimefunArmorPiece;

/**
 * Heuristic type for an ADDON item, used to split an addon's items across the shared guide categories.
 * Emits only ids it can confidently detect from the item's Slimefun type or material family; anything
 * else returns null so the caller files it under the addon's Misc section. Version-safe (matches on
 * {@link Material#name()}), and never guesses an axe as a weapon (an axe is a weapon only "if designed to
 * be one", which a material heuristic can't tell - so axes are Tools).
 */
public final class ItemTypeClassifier {

    private ItemTypeClassifier() {}

    @Nullable
    public static String classify(@Nonnull SlimefunItem item) {
        // An explicit addon-declared type wins over the heuristic (the caller validates it against the
        // category registry, so an unknown id still lands in Misc).
        if (item.getGuideType() != null) {
            return item.getGuideType();
        }

        if (item instanceof SlimefunArmorPiece) {
            return DefaultGuideCategories.ARMOR;
        }

        if (item instanceof EnergyNetComponent || item instanceof MultiBlockMachine) {
            return DefaultGuideCategories.MACHINES;
        }

        Material material = null;

        try {
            if (item.getItem() != null) {
                material = item.getItem().getType();
            }
        } catch (Exception | LinkageError ignored) {
            // a broken item must not break the whole menu
        }

        return material == null ? null : classifyMaterial(material);
    }

    @Nullable
    static String classifyMaterial(@Nonnull Material material) {
        String name = material.name();

        if (name.endsWith("_SWORD") || name.equals("BOW") || name.endsWith("CROSSBOW") || name.equals("TRIDENT") || name.equals("MACE")) {
            return DefaultGuideCategories.WEAPONS;
        }

        // Axes are TOOLS: a heuristic can't tell a "designed weapon" axe from a utility axe.
        if (name.endsWith("_PICKAXE") || name.endsWith("_AXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE")
            || name.equals("SHEARS") || name.equals("FISHING_ROD") || name.equals("FLINT_AND_STEEL") || name.equals("SPYGLASS") || name.equals("BRUSH")) {
            return DefaultGuideCategories.TOOLS;
        }

        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS") || name.equals("ELYTRA")) {
            return DefaultGuideCategories.ARMOR;
        }

        try {
            if (material.isEdible()) {
                return DefaultGuideCategories.FOOD;
            }
        } catch (Exception | LinkageError ignored) {
            // isEdible() can touch the registry on some versions; ignore and fall through
        }

        if (isFarming(name)) {
            return DefaultGuideCategories.FOOD;
        }

        if (isResource(name)) {
            return DefaultGuideCategories.RESOURCES;
        }

        if (isDecoration(name)) {
            return DefaultGuideCategories.DECORATION;
        }

        return null;
    }

    /** Farming/crop items (seeds, saplings, raw crops) that belong under Food &amp; Farming, not Misc. */
    private static boolean isFarming(@Nonnull String name) {
        if (name.endsWith("_SEEDS") || name.endsWith("_SAPLING")) {
            return true;
        }

        switch (name) {
            case "WHEAT":
            case "SUGAR_CANE":
            case "BAMBOO":
            case "KELP":
            case "CACTUS":
            case "NETHER_WART":
            case "COCOA_BEANS":
            case "SWEET_BERRIES":
            case "GLOW_BERRIES":
            case "MELON_SLICE":
            case "BONE_MEAL":
                return true;
            default:
                return false;
        }
    }

    /** Decorative & building blocks (wool, glass, banners, planks, stairs, stone families, ...) - not Misc. */
    private static boolean isDecoration(@Nonnull String name) {
        if (name.endsWith("_WOOL") || name.endsWith("_CARPET") || name.endsWith("_TERRACOTTA")
            || name.endsWith("_CONCRETE") || name.endsWith("_CONCRETE_POWDER") || name.endsWith("_STAINED_GLASS")
            || name.endsWith("_STAINED_GLASS_PANE") || name.endsWith("_BANNER") || name.endsWith("_BED")
            || name.endsWith("_CANDLE") || name.endsWith("_SHULKER_BOX")) {
            return true;
        }

        // Building-block families: an addon's cosmetic/building blocks share these vanilla suffixes/prefixes.
        if (name.endsWith("_PLANKS") || name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_STEM")
            || name.endsWith("_LEAVES") || name.endsWith("_STAIRS") || name.endsWith("_SLAB") || name.endsWith("_WALL")
            || name.endsWith("_FENCE") || name.endsWith("_FENCE_GATE") || name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR")
            || name.endsWith("_SIGN") || name.endsWith("_PRESSURE_PLATE") || name.endsWith("_BUTTON")
            || name.endsWith("_BRICKS") || name.endsWith("_TILES") || name.endsWith("_PILLAR") || name.endsWith("_GLAZED_TERRACOTTA")) {
            return true;
        }

        if (name.startsWith("POLISHED_") || name.startsWith("CHISELED_") || name.startsWith("SMOOTH_")
            || name.startsWith("CUT_") || name.startsWith("MOSSY_") || name.startsWith("CRACKED_") || name.startsWith("INFESTED_")) {
            return true;
        }

        switch (name) {
            case "GLASS":
            case "GLASS_PANE":
            case "TINTED_GLASS":
            case "PAINTING":
            case "ITEM_FRAME":
            case "GLOW_ITEM_FRAME":
            case "FLOWER_POT":
            case "ARMOR_STAND":
            case "LANTERN":
            case "SOUL_LANTERN":
            case "CANDLE":
            case "BELL":
            case "STONE":
            case "COBBLESTONE":
            case "GRANITE":
            case "DIORITE":
            case "ANDESITE":
            case "DEEPSLATE":
            case "COBBLED_DEEPSLATE":
            case "CALCITE":
            case "TUFF":
            case "BASALT":
            case "BLACKSTONE":
            case "SANDSTONE":
            case "RED_SANDSTONE":
            case "PRISMARINE":
            case "BRICKS":
            case "BOOKSHELF":
            case "QUARTZ_BLOCK":
            case "OBSIDIAN":
                return true;
            default:
                return false;
        }
    }

    /** Raw crafting resources (ingots, nuggets, gems, dusts, ores, shards) that would otherwise fall to Misc. */
    private static boolean isResource(@Nonnull String name) {
        if (name.endsWith("_INGOT") || name.endsWith("_NUGGET") || name.endsWith("_ORE") || name.startsWith("RAW_")
            || name.endsWith("_DUST") || name.endsWith("_SCRAP") || name.endsWith("_SHARD") || name.endsWith("_CRYSTAL")
            || name.endsWith("_CRYSTALS") || name.endsWith("_GEM")) {
            return true;
        }

        switch (name) {
            case "DIAMOND":
            case "EMERALD":
            case "QUARTZ":
            case "AMETHYST_SHARD":
            case "COAL":
            case "CHARCOAL":
            case "REDSTONE":
            case "LAPIS_LAZULI":
            case "GLOWSTONE_DUST":
            case "GUNPOWDER":
            case "NETHERITE_SCRAP":
            case "PRISMARINE_SHARD":
            case "PRISMARINE_CRYSTALS":
            case "FLINT":
            case "CLAY_BALL":
            case "STICK":
            case "STRING":
            case "LEATHER":
            case "PAPER":
            case "BLAZE_ROD":
            case "BLAZE_POWDER":
            case "ENDER_PEARL":
            case "SLIME_BALL":
            case "HONEYCOMB":
            case "ECHO_SHARD":
                return true;
            default:
                return false;
        }
    }

    @Nonnull
    public static String typeSingular(@Nonnull String categoryId) {
        switch (categoryId) {
            case DefaultGuideCategories.WEAPONS:
                return "Weapon";
            case DefaultGuideCategories.TOOLS:
                return "Tool";
            case DefaultGuideCategories.ARMOR:
                return "Armor";
            case DefaultGuideCategories.MACHINES:
                return "Machine";
            case DefaultGuideCategories.ENERGY_TECH:
                return "Energy";
            case DefaultGuideCategories.RESOURCES:
                return "Resource";
            case DefaultGuideCategories.MAGIC:
                return "Magic";
            case DefaultGuideCategories.FOOD:
                return "Food";
            case DefaultGuideCategories.LOGISTICS:
                return "Logistics";
            case DefaultGuideCategories.DECORATION:
                return "Decoration";
            default:
                return "Misc";
        }
    }
}
