package io.github.thebusybiscuit.slimefun5.test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockbukkit.mockbukkit.MockBukkit;

import io.github.thebusybiscuit.slimefun5.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun5.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.implementation.items.VanillaItem;
import io.github.thebusybiscuit.slimefun5.implementation.setup.SlimefunItemSetup;

/**
 * Headless boot check: enables Slimefun in a mocked server and runs the FULL item-catalogue setup (which
 * the unit-test boot path normally skips). This exercises the real registration path - the one the id-only
 * lore rebuild kept breaking (Talisman/hazmat null-lore NPEs aborting registration) - at BUILD time instead
 * of on a live boot. Note: {@code Slimefun.loadItems()} swallows exceptions in production, so we call
 * {@link SlimefunItemSetup#setup} directly here so any registration failure actually fails the test.
 *
 * @implNote Explicitly ordered: {@code testItemsRoundTripAfterBake} deliberately overwrites every item's
 *           shared, class-static template with fake baked text to test the bake primitive itself, which
 *           would otherwise pollute {@code testEveryItemIsIdOnly}'s view of the clean post-registration
 *           state for any test method ordered after it.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BootSmokeTest {

    private static Slimefun plugin;

    @BeforeAll
    public static void load() {
        MockBukkit.mock();
        plugin = MockBukkit.load(Slimefun.class);
        // Run the real item registration (skipped by the unit-test boot). Throws if any item ctor NPEs.
        SlimefunItemSetup.setup(plugin);
    }

    @AfterAll
    public static void unload() {
        MockBukkit.unmock();
    }

    @Test
    @Order(1)
    @DisplayName("Slimefun enables cleanly")
    void testEnabled() {
        Assertions.assertNotNull(plugin, "Slimefun failed to load under MockBukkit");
        Assertions.assertTrue(plugin.isEnabled(), "Slimefun is not enabled after load");
    }

    @Test
    @Order(2)
    @DisplayName("Item registration completes and registers a bulk of items (no aborted setup)")
    void testItemsRegister() {
        int count = Slimefun.getRegistry().getEnabledSlimefunItems().size();
        // A 1.21 mock legitimately can't build a few items whose material is newer, so this is a floor, not
        // the exact catalogue size; the point is that setup ran to completion rather than aborting early.
        Assertions.assertTrue(count > 300, "expected 300+ registered items, got " + count
            + " - item registration likely aborted mid-setup (e.g. a null-lore NPE)");
    }

    @Test
    @Order(3)
    @DisplayName("Every registered item exposes a usable template (getItem() never throws / returns null)")
    void testItemsExposeTemplate() {
        // id-only items legitimately have NO template lore/name here (the resolver fills the display from
        // en/items.yml at runtime, which the unit-test boot doesn't run). What we guard is that getItem()
        // itself is sound for every registered item - a null/throwing template is what shows as a blank
        // "glass pane" in the guide.
        List<String> offenders = new ArrayList<>();

        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            try {
                ItemStack stack = item.getItem();

                if (stack == null || stack.getType() == null) {
                    offenders.add(item.getId());
                }
            } catch (Exception | LinkageError e) {
                offenders.add(item.getId() + " (" + e.getClass().getSimpleName() + ")");
            }
        }

        Assertions.assertTrue(offenders.isEmpty(),
            offenders.size() + " item(s) have a broken template: " + offenders.subList(0, Math.min(15, offenders.size())));
    }

    @Test
    @Order(4)
    @DisplayName("Every item identifies itself: getByItem(item.getItem()) round-trips to the same item")
    void testItemsRoundTrip() {
        // If our id/PDC/distinctive changes broke an item's self-identification, its own template no longer
        // resolves back to it - which is a whole class of "the item doesn't work anymore" migration breakage.
        List<String> offenders = new ArrayList<>();

        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            // VanillaItem entries are real vanilla items shown in the guide for their recipe - they carry
            // no Slimefun PDC id (you get the actual vanilla item), so not identifying is correct.
            if (item instanceof VanillaItem) {
                continue;
            }

            try {
                SlimefunItem resolved = SlimefunItem.getByItem(item.getItem());

                if (resolved == null || !resolved.getId().equals(item.getId())) {
                    offenders.add(item.getId() + " -> " + (resolved == null ? "null" : resolved.getId()));
                }
            } catch (Exception | LinkageError e) {
                offenders.add(item.getId() + " (" + e.getClass().getSimpleName() + ")");
            }
        }

        Assertions.assertTrue(offenders.isEmpty(),
            offenders.size() + " item(s) do not identify themselves: " + offenders.subList(0, Math.min(15, offenders.size())));
    }

    @Test
    @Order(6)
    @DisplayName("Regression: every item still identifies AFTER the display bake/compose pass")
    void testItemsRoundTripAfterBake() {
        // The boot-time bake (canonicalizeToId -> bakeTranslatedDisplay) rewrites the
        // name/lore of every physical template. It must never drop the identity tag - if it does, every
        // affected item "reverts to vanilla" (getByItem == null) after one boot. The unit-test boot has
        // no languages loaded, so we invoke the mutation primitive itself on every item.
        List<String> offenders = new ArrayList<>();

        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            if (item instanceof VanillaItem) {
                continue;
            }

            try {
                item.bakeTranslatedDisplay("&aBaked " + item.getId(),
                    java.util.Arrays.asList("&8⇨ &7Baked Type", "", "&fBaked description line"));

                SlimefunItem resolved = SlimefunItem.getByItem(item.getItem());

                if (resolved == null || !resolved.getId().equals(item.getId())) {
                    offenders.add(item.getId() + " -> " + (resolved == null ? "null" : resolved.getId()));
                }
            } catch (Exception | LinkageError e) {
                offenders.add(item.getId() + " (" + e.getClass().getSimpleName() + ")");
            }
        }

        Assertions.assertTrue(offenders.isEmpty(),
            offenders.size() + " item(s) lost their identity in the bake/compose pass: "
                + offenders.subList(0, Math.min(15, offenders.size())));
    }

    @Test
    @Order(5)
    @DisplayName("Every item's baked template is id-only: display name == id, and no lore")
    void testEveryItemIsIdOnly() {
        // Hard rule: a persisted ItemStack never carries baked player-facing text. Its display name must
        // be exactly the raw id and it must carry no lore; all translated/composed text is produced per
        // viewer at render time by the packet translation layer, never baked into the template.
        List<String> offenders = new ArrayList<>();

        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            // VanillaItem entries intentionally show the vanilla client's own name/lore, so skip them.
            if (item instanceof VanillaItem) {
                continue;
            }

            ItemMeta meta = item.getItem().getItemMeta();
            boolean nameIsId = meta != null && meta.hasDisplayName() && item.getId().equals(meta.getDisplayName());
            boolean hasLore = meta != null && meta.getLore() != null && !meta.getLore().isEmpty();

            if (!nameIsId || hasLore) {
                String badName = meta == null ? "no meta" : (meta.hasDisplayName() ? meta.getDisplayName() : "no name");
                offenders.add(item.getId() + (nameIsId ? "" : " [name=" + badName + "]") + (hasLore ? " [has lore]" : ""));
            }
        }

        Assertions.assertTrue(offenders.isEmpty(),
            offenders.size() + " item(s) violate the id-only display rule (name must equal id, no lore): "
                + offenders.subList(0, Math.min(15, offenders.size())));
    }

    @Test
    @Order(7)
    @DisplayName("Every addon with items owns an item group (so it appears in the wiki's Browse-by-Addon)")
    void testEveryAddonIsWikiReachable() {
        // The wiki's addon browser lists addons by the item groups they register (ItemGroup.getAddon()).
        // An addon that registers items but no group of its own would have no page there - this pins that
        // every addon shipping custom items is reachable.
        Set<String> addonsOwningAGroup = new HashSet<>();

        for (ItemGroup group : Slimefun.getRegistry().getAllItemGroups()) {
            SlimefunAddon addon = group.getAddon();
            if (addon != null) {
                addonsOwningAGroup.add(addon.getName());
            }
        }

        List<String> uncovered = new ArrayList<>();

        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            if (item instanceof VanillaItem) {
                continue;
            }

            SlimefunAddon addon = item.getAddon();
            if (addon != null && !addonsOwningAGroup.contains(addon.getName()) && !uncovered.contains(addon.getName())) {
                uncovered.add(addon.getName());
            }
        }

        Assertions.assertTrue(uncovered.isEmpty(),
            uncovered.size() + " addon(s) have items but no browsable item group (unreachable in Browse-by-Addon): " + uncovered);
    }

    /**
     * The canonical, upstream-compatible way to stamp identity: raw Bukkit PDC under the shared
     * {@code slimefun:slimefun_item} key. Written independently of the fork's own PdcCompat writer on
     * purpose - this proves getByItem reads a tag written by any Slimefun 4/5 version or foreign plugin,
     * which is what "items transfer from an old server" depends on.
     */
    private static ItemStack upstreamStack(Material material, String id) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "slimefun_item"), PersistentDataType.STRING, id);
        stack.setItemMeta(meta);
        return stack;
    }

    @Test
    @Order(8)
    @DisplayName("Migration: upstream-tagged stacks (raw Bukkit PDC) resolve back to the correct item")
    void testUpstreamTaggedItemsResolve() {
        List<String> offenders = new ArrayList<>();

        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            if (item instanceof VanillaItem) {
                continue;
            }

            try {
                SlimefunItem resolved = SlimefunItem.getByItem(upstreamStack(item.getItem().getType(), item.getId()));

                if (resolved == null || !resolved.getId().equals(item.getId())) {
                    offenders.add(item.getId() + " -> " + (resolved == null ? "null" : resolved.getId()));
                }
            } catch (Exception | LinkageError e) {
                offenders.add(item.getId() + " (" + e.getClass().getSimpleName() + ")");
            }
        }

        Assertions.assertTrue(offenders.isEmpty(),
            offenders.size() + " item(s) from an old server would NOT resolve on this fork: "
                + offenders.subList(0, Math.min(15, offenders.size())));
    }

    @Test
    @Order(9)
    @DisplayName("Migration: an unknown/foreign id resolves to null rather than a wrong item")
    void testUnknownIdIsNullNotError() {
        Assertions.assertNull(SlimefunItem.getByItem(upstreamStack(Material.PAPER, "SOME_UNINSTALLED_ADDON_ITEM")),
            "An id from an uninstalled addon must resolve to null (the migrationcheck signal), not a wrong item");
    }

    @Test
    @Order(10)
    @DisplayName("New guide UI message keys resolve to non-empty English strings")
    void testNewGuideKeysResolve() {
        // The unit-test boot never loads any Language (onUnitTestStart() passes a null
        // serverDefaultLanguage), so Slimefun.getLocalization() has nothing to resolve against here.
        // Read the bundled en/messages.yml straight off the classpath instead, same as testEveryItemIsIdOnly.
        YamlConfiguration en = new YamlConfiguration();

        try (InputStream in = getClass().getResourceAsStream("/languages/en/messages.yml")) {
            Assertions.assertNotNull(in, "en/messages.yml not found on the classpath");
            en.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            Assertions.fail("Could not read en/messages.yml: " + e);
        }

        String[] keys = { "guide.research.unlock", "guide.research.cost", "guide.recipe.error",
            "guide.recipe.needs-unlock", "guide.recipe.no-permission" };
        for (String key : keys) {
            String v = en.getString(key);
            Assertions.assertTrue(v != null && !v.trim().isEmpty(), "missing/blank en message key: " + key);
        }
        Assertions.assertFalse(en.getStringList("guide.options.machine-messages.enabled.text").isEmpty(),
            "machine-messages.enabled.text must be a non-empty list");
        Assertions.assertFalse(en.getStringList("guide.back.page").isEmpty(),
            "guide.back.page must be a non-empty list");
    }
}
