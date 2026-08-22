package io.github.thebusybiscuit.slimefun5.core.guide.variants;

import java.util.Arrays;
import java.util.Collections;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import io.github.thebusybiscuit.slimefun5.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun5.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.bakedlibs.dough.data.persistent.VersionedPdc;
import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;

/**
 * Covers the guide's variant grouping: many registered items presented in one slot. The invariants that
 * matter are that exactly one member anchors the slot, that the others are hidden from both the item list
 * and search, and that a display copy carries the position the packet layer renders as "(n/total)".
 */
class VariantGroupTest {

    private static Slimefun plugin;

    @BeforeAll
    static void load() {
        MockBukkit.mock();
        plugin = MockBukkit.load(Slimefun.class);
    }

    @AfterAll
    static void unload() {
        MockBukkit.unmock();
    }

    private static SlimefunItem register(String id) {
        ItemGroup group = new ItemGroup(new NamespacedKey(plugin, id.toLowerCase() + "_group"), new ItemStack(Material.EMERALD));
        SlimefunItem item = new SlimefunItem(group, new SlimefunItemStack(id, Material.PAPER), RecipeType.NULL, new ItemStack[9]);
        item.register(plugin);
        return item;
    }

    private static VariantGroup group(String key, SlimefunItem... members) {
        return new VariantGroup(new NamespacedKey(plugin, key), Arrays.asList(members)).register();
    }

    @Test
    @DisplayName("The first member anchors the slot; the rest are collapsed out of the list and search")
    void testAnchorAndCollapsedMembers() {
        SlimefunItem iron = register("VARIANT_ROD_IRON");
        SlimefunItem gold = register("VARIANT_ROD_GOLD");
        SlimefunItem lead = register("VARIANT_ROD_LEAD");

        VariantGroup rods = group("rods", iron, gold, lead);

        Assertions.assertSame(iron, rods.getAnchor());
        Assertions.assertFalse(Slimefun.getVariantGroups().isCollapsedMember(iron.getId()), "the anchor keeps its slot");
        Assertions.assertTrue(Slimefun.getVariantGroups().isCollapsedMember(gold.getId()));
        Assertions.assertTrue(Slimefun.getVariantGroups().isCollapsedMember(lead.getId()));
    }

    @Test
    @DisplayName("An ungrouped item is never treated as a group member")
    void testUngroupedItemIsUntouched() {
        SlimefunItem standalone = register("VARIANT_STANDALONE");

        Assertions.assertNull(Slimefun.getVariantGroups().getGroup(standalone.getId()));
        Assertions.assertFalse(Slimefun.getVariantGroups().isCollapsedMember(standalone.getId()));
    }

    @Test
    @DisplayName("indexOf gives the 1-based cycle position, 0 for a non-member")
    void testIndexOf() {
        SlimefunItem a = register("VARIANT_PLATE_A");
        SlimefunItem b = register("VARIANT_PLATE_B");
        VariantGroup plates = group("plates", a, b);

        Assertions.assertEquals(1, plates.indexOf(a.getId()));
        Assertions.assertEquals(2, plates.indexOf(b.getId()));
        Assertions.assertEquals(0, plates.indexOf("VARIANT_NOT_A_MEMBER"));
    }

    @Test
    @DisplayName("A marked display copy reports its position, an unmarked stack reports none")
    void testDisplayMarkerRoundTrip() {
        ItemStack marked = new ItemStack(Material.PAPER);
        VariantDisplayMarker.mark(marked, 6, 14);

        Assertions.assertEquals("6/14", VariantDisplayMarker.read(marked.getItemMeta()));
        Assertions.assertNull(VariantDisplayMarker.read(new ItemStack(Material.PAPER).getItemMeta()));
    }

    @Test
    @DisplayName("A second group cannot steal a member already claimed by the first")
    void testMemberIsNotStolen() {
        SlimefunItem shared = register("VARIANT_SHARED");
        SlimefunItem other = register("VARIANT_OTHER");

        VariantGroup first = group("first", shared, other);
        group("second", shared);

        Assertions.assertSame(first, Slimefun.getVariantGroups().getGroup(shared.getId()));
    }

    /**
     * Persistent data set on the stack handed to {@code new SlimefunItemStack(id, stack)} must be readable
     * back from the registered item, because that is the only way an addon can register a variant that
     * carries per-variant identity (SlimeTinker's part material/class/type).
     */
    @Test
    @DisplayName("Persistent data on the source stack survives registration")
    void testPersistentDataSurvivesRegistration() {
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "variant_probe");

        ItemStack source = new ItemStack(Material.PAPER);
        ItemMeta sourceMeta = source.getItemMeta();
        sourceMeta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "ZINC");
        source.setItemMeta(sourceMeta);

        Assertions.assertEquals("ZINC",
            source.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING),
            "sanity: the source stack carries the data");

        ItemGroup itemGroup = new ItemGroup(new NamespacedKey(plugin, "pdc_probe_group"), new ItemStack(Material.EMERALD));
        SlimefunItem item = new SlimefunItem(itemGroup, new SlimefunItemStack("VARIANT_PDC_PROBE", source), RecipeType.NULL, new ItemStack[9]);
        item.register(plugin);

        ItemMeta registered = item.getItem().getItemMeta();
        Assertions.assertNotNull(registered);
        Assertions.assertEquals("ZINC",
            registered.getPersistentDataContainer().get(key, PersistentDataType.STRING),
            "identity set on the source stack must be readable from the registered template");
    }

    /**
     * The cheat picker pages through a group with more members than one screen holds, so the page maths
     * must cover every variant exactly once - an off-by-one here silently hides the last flavour.
     */
    @Test
    @DisplayName("Every variant of an oversized group falls on exactly one picker page")
    void testPickerPaginationCoversEveryVariant() {
        int slotsPerPage = 36;
        SlimefunItem[] members = new SlimefunItem[slotsPerPage + 7];

        for (int i = 0; i < members.length; i++) {
            members[i] = register("VARIANT_PAGED_" + i);
        }

        VariantGroup paged = group("paged", members);
        Assertions.assertEquals(members.length, paged.size());

        int pages = (paged.size() - 1) / slotsPerPage + 1;
        Assertions.assertEquals(2, pages);

        java.util.Set<String> seen = new java.util.HashSet<>();

        for (int page = 1; page <= pages; page++) {
            int offset = slotsPerPage * (page - 1);

            for (int i = 0; i < slotsPerPage && offset + i < paged.size(); i++) {
                SlimefunItem variant = paged.getVariants().get(offset + i);
                Assertions.assertTrue(seen.add(variant.getId()), "variant listed twice: " + variant.getId());
                Assertions.assertEquals(offset + i + 1, paged.indexOf(variant.getId()),
                    "the counter shown in the picker must match the variant's cycle position");
            }
        }

        Assertions.assertEquals(members.length, seen.size(), "every variant must appear on some page");
    }

    /**
     * The exact combination SlimeTinker needs: identity written through dough's {@code VersionedPdc}
     * (string-keyed, reflective) rather than Bukkit's typed API, read back off a REGISTERED item.
     *
     * @implNote Written because three attempts at this in SlimeTinker all read back null while the
     *           Bukkit-PDC equivalent above passed, so the two paths must be compared directly instead of
     *           inferred. Whichever of the three stages loses the value, this pins it.
     */
    @Test
    @DisplayName("VersionedPdc identity survives being wrapped and registered")
    void testVersionedPdcSurvivesRegistration() {
        String key = "slimetinker:st_class";

        ItemStack source = new ItemStack(Material.PAPER);
        ItemMeta sourceMeta = source.getItemMeta();
        VersionedPdc.setString(sourceMeta, key, "HEAD");
        source.setItemMeta(sourceMeta);

        Assertions.assertEquals("HEAD", VersionedPdc.getString(source.getItemMeta(), key),
            "stage 1: the plain source stack must read back");

        SlimefunItemStack wrapped = new SlimefunItemStack("VARIANT_VPDC_PROBE", source);
        Assertions.assertEquals("HEAD", VersionedPdc.getString(wrapped.getItemMeta(), key),
            "stage 2: wrapping in a SlimefunItemStack must not drop it");

        ItemGroup itemGroup = new ItemGroup(new NamespacedKey(plugin, "vpdc_probe_group"), new ItemStack(Material.EMERALD));
        SlimefunItem item = new SlimefunItem(itemGroup, wrapped, RecipeType.NULL, new ItemStack[9]);
        item.register(plugin);

        Assertions.assertEquals("HEAD", VersionedPdc.getString(item.getItem().getItemMeta(), key),
            "stage 3: the registered template must still carry it");

        // Stage 4: core re-bakes every registered template to its id-only display once addons have loaded
        // (canonicalizeToId -> bakeTranslatedDisplay). That pass rewrites the template's meta, so it is the
        // one place a per-variant identity could be silently dropped after registration.
        Slimefun.getItemTranslationService().canonicalizeToId();

        Assertions.assertEquals("HEAD", VersionedPdc.getString(item.getItem().getItemMeta(), key),
            "stage 4: the identity must survive the id-only display bake");
    }

    /**
     * Reproduces the real shape of a SlimeTinker part variant: the source stack is a clone of an ALREADY
     * REGISTERED template (as {@code PartTemplate#getStack} produces), stamped, then wrapped and registered
     * as a second item. This is the last difference between the passing tests above and the in-game
     * diagnostic that read back null.
     */
    @Test
    @DisplayName("Identity survives when the source stack is a clone of another registered item")
    void testIdentityFromRegisteredTemplateClone() {
        String key = "slimetinker:st_class";

        ItemGroup templateGroup = new ItemGroup(new NamespacedKey(plugin, "clone_src_group"), new ItemStack(Material.EMERALD));
        SlimefunItem template = new SlimefunItem(templateGroup, new SlimefunItemStack("VARIANT_CLONE_TEMPLATE", Material.PLAYER_HEAD), RecipeType.NULL, new ItemStack[9]);
        template.register(plugin);

        // Exactly what PartTemplate#getStack does.
        ItemStack source = template.getItem().clone();
        ItemMeta sourceMeta = source.getItemMeta();
        VersionedPdc.setString(sourceMeta, key, "HEAD");
        source.setItemMeta(sourceMeta);

        Assertions.assertEquals("HEAD", VersionedPdc.getString(source.getItemMeta(), key),
            "the stamped clone must read back before it is wrapped");

        SlimefunItem variant = new SlimefunItem(templateGroup, new SlimefunItemStack("VARIANT_CLONE_CHILD", source), RecipeType.NULL, new ItemStack[9]);
        variant.register(plugin);
        Slimefun.getItemTranslationService().canonicalizeToId();

        Assertions.assertEquals("HEAD", VersionedPdc.getString(variant.getItem().getItemMeta(), key),
            "identity must survive when the source was a registered item's clone");
    }

    @Test
    @DisplayName("A group needs a key and at least one variant")
    void testValidation() {
        SlimefunItem only = register("VARIANT_ONLY");

        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new VariantGroup(new NamespacedKey(plugin, "empty"), Collections.<SlimefunItem>emptyList()));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new VariantGroup(null, Arrays.asList(only)));
    }
}
