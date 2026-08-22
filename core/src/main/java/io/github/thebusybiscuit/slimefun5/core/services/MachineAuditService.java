package io.github.thebusybiscuit.slimefun5.core.services;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.configuration.file.YamlConfiguration;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun5.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun5.core.multiblocks.MultiBlockAssembler;
import io.github.thebusybiscuit.slimefun5.core.multiblocks.MultiBlockMachine;
import io.github.thebusybiscuit.slimefun5.core.services.localization.MenuTranslationService;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.implementation.items.blocks.UnplaceableBlock;

import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;

/**
 * Boot-time audit of machines and multiblocks that still use a superseded API, mirroring the
 * translation audit: a console warning per category plus the full per-addon list on disk, so a
 * migration gap stays visible instead of silently degrading in game.
 *
 * @see io.github.thebusybiscuit.slimefun5.core.services.localization.ItemTranslationService#auditUnmigratedLore(File)
 */
public class MachineAuditService {

    /** A menu whose GUI title colour is guessed rather than declared. */
    public static final String KEY_DEPRECATED_MENU = "deprecated_menu";

    /** A multiblock the assembler can never build, so cheating/placing its item does nothing. */
    public static final String KEY_UNASSEMBLABLE = "unassemblable_multiblock";

    private final Map<String, Map<String, List<String>>> findings = new TreeMap<>();

    /**
     * Runs every check and reports the result. Call once all addons have registered their items.
     *
     * @param out
     *            The file the full per-addon breakdown is written to
     */
    public void audit(@Nonnull File out) {
        findings.clear();

        auditMenuPresets();
        auditMultiBlocks();
        reportVariantGroups();

        if (findings.isEmpty()) {
            return;
        }

        report(out);
    }

    /**
     * Flags every {@link BlockMenuPreset} built through the deprecated
     * {@link me.mrCookieSlime.Slimefun.Objects.SlimefunItem.interfaces.InventoryBlock#createPreset(SlimefunItem, java.util.function.Consumer)}
     * path: it declares neither a header item slot nor an opt-out title colour, so its GUI title falls
     * back to a name-matching guess and usually renders gray.
     */
    private void auditMenuPresets() {
        for (BlockMenuPreset preset : Slimefun.getRegistry().getMenuPresets().values()) {
            try {
                if (preset.getExplicitHeaderSlot() != null || preset.getExplicitTitleColor() != null || preset.usesItemNameTitleColor()) {
                    continue;
                }

                record(KEY_DEPRECATED_MENU, SlimefunItem.getById(preset.getID()), preset.getID() + " -> " + MenuTranslationService.describeHeaderResolution(preset));
            } catch (Exception | LinkageError ignored) {
                // A single broken preset must not abort the audit.
            }
        }
    }

    /**
     * Flags an item the guide presents as a multiblock ({@link RecipeType#MULTIBLOCK}, "Build it in the
     * World") that nothing can actually build: {@link MultiBlockAssembler} only drives a
     * {@link MultiBlockMachine}, so anything else needs its own {@link ItemUseHandler} to assemble the
     * structure. An {@link UnplaceableBlock}'s handler only cancels the click, which is the inert
     * placeholder this check exists to catch - cheating such an item in hands the player a dead block.
     *
     * @implNote A companion "recipe type names a machine that was never registered" check was tried and
     *           removed: addons routinely build a {@link RecipeType} around an intentionally unregistered
     *           {@link io.github.thebusybiscuit.slimefun5.api.items.SlimefunItemStack} purely as a guide
     *           label ("obtain this from a chicken"), which is indistinguishable from a broken reference
     *           and produced 1311 entries of pure noise across two addons.
     */
    private void auditMultiBlocks() {
        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            try {
                if (item.getRecipeType() != RecipeType.MULTIBLOCK || item instanceof MultiBlockMachine) {
                    continue;
                }

                if (assemblesItself(item)) {
                    continue;
                }

                record(KEY_UNASSEMBLABLE, item, item.getId());
            } catch (Exception | LinkageError ignored) {
                // A single broken item must not abort the audit.
            }
        }
    }

    /** Whether right-clicking {@code item} runs behaviour of its own rather than doing nothing. */
    private static boolean assemblesItself(@Nonnull SlimefunItem item) {
        if (item instanceof UnplaceableBlock) {
            return false;
        }

        // Passing a no-op consumer: the return value is "an ItemUseHandler is registered", and the
        // handler itself is never invoked.
        return item.callItemHandler(ItemUseHandler.class, handler -> { });
    }

    /**
     * Reports how many {@link io.github.thebusybiscuit.slimefun5.core.guide.variants.VariantGroup}s are
     * registered and how many item tiles they save the guide.
     *
     * @implNote Logged unconditionally (not only on a finding) because a group that fails to register is
     *           invisible: the guide simply lists every variant as though grouping were never asked for,
     *           which reads identically to the feature not existing. This line is the difference between
     *           "no groups registered" and "groups registered but not collapsing".
     */
    private void reportVariantGroups() {
        try {
            java.util.Collection<io.github.thebusybiscuit.slimefun5.core.guide.variants.VariantGroup> groups =
                Slimefun.getVariantGroups().getGroups();

            int members = 0;

            for (io.github.thebusybiscuit.slimefun5.core.guide.variants.VariantGroup group : groups) {
                members += group.size();
            }

            // Counted through the SAME predicate the guide's listing filter uses, so this line proves
            // whether the collapse would actually happen rather than just that groups are registered.
            int collapsed = 0;
            int anchors = 0;

            for (io.github.thebusybiscuit.slimefun5.core.guide.variants.VariantGroup group : groups) {
                for (SlimefunItem variant : group.getVariants()) {
                    if (Slimefun.getVariantGroups().isCollapsedMember(variant.getId())) {
                        collapsed++;
                    } else {
                        anchors++;
                    }
                }
            }

            // Always logged, zero included: "0 group(s)" says something a missing line cannot.
            Slimefun.logger().log(Level.INFO, "[variants] {0} group(s), {1} members: {2} hidden by the listing filter, {3} kept as anchors",
                new Object[] { groups.size(), members, collapsed, anchors });
        } catch (Exception | LinkageError ignored) {
            // never break the audit over a diagnostic line
        }
    }

    private void record(@Nonnull String category, @Nullable SlimefunItem owner, @Nonnull String entry) {
        String addon = "unknown";

        if (owner != null && owner.getAddon() != null) {
            addon = owner.getAddon().getName();
        }

        findings.computeIfAbsent(category, k -> new TreeMap<>())
            .computeIfAbsent(addon, k -> new ArrayList<>())
            .add(entry);
    }

    private void report(@Nonnull File out) {
        warn(KEY_DEPRECATED_MENU, "[menus] {0} machine menu(s) still use the deprecated createPreset(item, setup) - they declare no header item slot and no title colour, so their GUI title falls back to gray. Full list: {1}");
        warn(KEY_UNASSEMBLABLE, "[multiblocks] {0} item(s) are shown as a multiblock but neither are a MultiBlockMachine nor assemble themselves, so cheating them in hands the player a dead block. Full list: {1}");

        YamlConfiguration config = new YamlConfiguration();
        config.options().pathSeparator('\u001F'); // dot-safe separator for addon/id keys

        char separator = config.options().pathSeparator();

        for (Map.Entry<String, Map<String, List<String>>> category : findings.entrySet()) {
            for (Map.Entry<String, List<String>> addon : category.getValue().entrySet()) {
                config.set(category.getKey() + separator + addon.getKey(), addon.getValue());
            }
        }

        try {
            config.save(out);
        } catch (IOException e) {
            Slimefun.logger().log(Level.WARNING, "Failed to write machine audit: {0}", e.getMessage());
        }
    }

    private void warn(@Nonnull String category, @Nonnull String message) {
        Map<String, List<String>> byAddon = findings.get(category);

        if (byAddon == null) {
            return;
        }

        int total = 0;

        for (List<String> entries : byAddon.values()) {
            total += entries.size();
        }

        Slimefun.logger().log(Level.WARNING, message, new Object[] { total, "machine-audit.yml" });

        for (Map.Entry<String, List<String>> addon : byAddon.entrySet()) {
            Slimefun.logger().log(Level.WARNING, "  {0}: {1}", new Object[] { addon.getKey(), addon.getValue().size() });
        }
    }

    /** The addons that appear anywhere in the last {@link #audit(File)} run. */
    @Nonnull
    public Set<String> getAuditedAddons() {
        Set<String> addons = new TreeSet<>();

        for (Map<String, List<String>> byAddon : findings.values()) {
            addons.addAll(byAddon.keySet());
        }

        return addons;
    }

    /** The entries recorded for {@code category}, keyed by addon - empty if that check found nothing. */
    @Nonnull
    public Map<String, List<String>> getFindings(@Nonnull String category) {
        return findings.getOrDefault(category, Collections.<String, List<String>>emptyMap());
    }
}
