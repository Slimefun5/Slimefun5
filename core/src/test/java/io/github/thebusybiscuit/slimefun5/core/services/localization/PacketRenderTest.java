package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import io.github.thebusybiscuit.slimefun5.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun5.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;
import org.bukkit.Material;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.core.attributes.Rechargeable;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.implementation.setup.SlimefunItemSetup;

class PacketRenderTest {

    private static Slimefun plugin;

    @BeforeAll
    public static void load() {
        MockBukkit.mock();
        plugin = MockBukkit.load(Slimefun.class);
        SlimefunItemSetup.setup(plugin);
        Slimefun.getItemTranslationService().loadBundled();
    }

    @AfterAll
    public static void unload() {
        MockBukkit.unmock();
    }

    @Test
    void unknownIdRendersNull() {
        Assertions.assertNull(Slimefun.getItemTranslationService()
            .renderForPacket("NOT_A_REAL_ITEM", "en", TranslationConfig.FallbackMode.ENGLISH, true));
    }

    /**
     * A RecipeType icon is a SlimefunItemStack that is never registered as a SlimefunItem. It must still
     * pick up its items.yml entry: otherwise the packet layer humanizes the raw id over it, which is how
     * SlimeTinker's icons came to read "Dummy Tinkers Smeltery" despite shipping translations.
     */
    @Test
    @DisplayName("An unregistered id with an items.yml entry renders that entry, not a humanized id")
    void unregisteredIdWithTranslationRendersIt() {
        String yaml = String.join("\n",
            "DUMMY_TEST_RECIPE_ICON:",
            "  name: '&6Molten Metal'",
            "  lore:",
            "  - '&7Pour it in the smeltery'");

        ItemTranslationService service = Slimefun.getItemTranslationService();
        service.loadTranslationsForTest("en", new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        service.clearRenderCache();

        Assertions.assertNull(SlimefunItem.getById("DUMMY_TEST_RECIPE_ICON"), "the icon must NOT be a registered item");

        ItemTranslationService.RenderedDisplay display =
            service.renderForPacket("DUMMY_TEST_RECIPE_ICON", "en", TranslationConfig.FallbackMode.ENGLISH, true);

        Assertions.assertNotNull(display, "an unregistered id with a translation must still render");
        Assertions.assertEquals(ChatColor.GOLD + "Molten Metal", display.name);
        Assertions.assertEquals(1, display.lore.size());
        Assertions.assertEquals(ChatColor.GRAY + "Pour it in the smeltery", display.lore.get(0));
    }

    /**
     * An item whose display is composed by a resolver has no items.yml entry on purpose, so the boot audit
     * must not report it as untranslated - otherwise registering per-material variants buries the real
     * findings under hundreds of false positives.
     */
    @Test
    @DisplayName("The untranslated-name audit skips an item a resolver renders")
    void auditSkipsResolverRenderedItem() throws Exception {
        ItemGroup itemGroup = new ItemGroup(new NamespacedKey(plugin, "audit_resolver_group"), new ItemStack(Material.EMERALD));
        SlimefunItem probe = new SlimefunItem(itemGroup, new SlimefunItemStack("AUDIT_RESOLVER_ITEM", Material.PAPER), RecipeType.NULL, new ItemStack[9]);
        probe.register(plugin);

        ItemTranslationService service = Slimefun.getItemTranslationService();
        File out = File.createTempFile("audit-before", ".yml");
        service.auditUnmigratedLore(out);
        Assertions.assertTrue(readIds(out).contains("AUDIT_RESOLVER_ITEM"),
            "without a resolver the item must be reported as untranslated");

        service.registerResolver((item, itemId, languageId) ->
            "AUDIT_RESOLVER_ITEM".equals(itemId)
                ? ItemTextBlocks.name("Composed Name")
                : null);

        File after = File.createTempFile("audit-after", ".yml");
        service.auditUnmigratedLore(after);
        Assertions.assertFalse(readIds(after).contains("AUDIT_RESOLVER_ITEM"),
            "a resolver-rendered item must be exempt from the untranslated-name audit");
    }

    private static String readIds(File file) throws Exception {
        return file.exists() ? new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8) : "";
    }

    @Test
    void knownItemRendersAName() {
        SlimefunItem probe = SlimefunItem.getById("ELECTRIC_MOTOR");
        Assertions.assertNotNull(probe, "ELECTRIC_MOTOR must be registered");
        ItemTranslationService.RenderedDisplay d = Slimefun.getItemTranslationService()
            .renderForPacket("ELECTRIC_MOTOR", "en", TranslationConfig.FallbackMode.ENGLISH, true);
        Assertions.assertNotNull(d);
        Assertions.assertNotNull(d.name);
        Assertions.assertFalse(d.name.trim().isEmpty());
    }

    @Test
    void cacheReturnsEqualResultOnSecondCall() {
        ItemTranslationService svc = Slimefun.getItemTranslationService();
        // Give the probe a real (non-raw-id) English name: only genuine renders are cached - a raw-id
        // fallback render is deliberately never memoized (see rawIdRenderIsNotCached...), and the
        // MockBukkit harness has no English baseline for core items, so without this the render degrades
        // to the raw id.
        svc.loadTranslationsForTest("en", new java.io.ByteArrayInputStream(
            "ELECTRIC_MOTOR:\n  name: '&aCached Motor'\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        svc.clearRenderCache();

        ItemTranslationService.RenderedDisplay a = svc.renderForPacket("ELECTRIC_MOTOR", "en", TranslationConfig.FallbackMode.ENGLISH, true);
        ItemTranslationService.RenderedDisplay b = svc.renderForPacket("ELECTRIC_MOTOR", "en", TranslationConfig.FallbackMode.ENGLISH, true);
        Assertions.assertSame(a, b, "cache must return the same instance");
    }

    @Test
    void clearRenderCacheForcesANewInstance() {
        ItemTranslationService svc = Slimefun.getItemTranslationService();
        ItemTranslationService.RenderedDisplay a = svc.renderForPacket("ELECTRIC_MOTOR", "en", TranslationConfig.FallbackMode.ENGLISH, true);
        svc.clearRenderCache();
        ItemTranslationService.RenderedDisplay b = svc.renderForPacket("ELECTRIC_MOTOR", "en", TranslationConfig.FallbackMode.ENGLISH, true);
        Assertions.assertNotSame(a, b, "clearRenderCache() must force a repeat call to recompute a fresh instance");
    }

    @Test
    void idFallbackRendersRawIdForALanguageWithNoLabel() {
        ItemTranslationService svc = Slimefun.getItemTranslationService();
        // "zz" is not a shipped language, so it has no translation - the name must fall back to the raw id.
        ItemTranslationService.RenderedDisplay d = svc.renderForPacket("ELECTRIC_MOTOR", "zz", TranslationConfig.FallbackMode.ID, true);
        Assertions.assertNotNull(d);
        Assertions.assertEquals("ELECTRIC_MOTOR", d.name);
    }

    @Test
    void englishFallbackRendersRealEnglishNotRawId() {
        ItemTranslationService svc = Slimefun.getItemTranslationService();
        // Load an "en" name for a probe id but never load "zz" (not a shipped language) - the "zz"
        // viewer must fall back to the real English name (via the "en" map), not degrade to the raw id.
        String yaml = "ELECTRIC_MOTOR:\n  name: '&aTest Electric Motor'\n";
        Slimefun.getItemTranslationService().loadTranslationsForTest("en",
            new java.io.ByteArrayInputStream(yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        svc.clearRenderCache();

        ItemTranslationService.RenderedDisplay d = svc.renderForPacket("ELECTRIC_MOTOR", "zz", TranslationConfig.FallbackMode.ENGLISH, true);

        Assertions.assertNotNull(d);
        Assertions.assertEquals("Test Electric Motor", ChatColor.stripColor(d.name));
        Assertions.assertNotEquals("ELECTRIC_MOTOR", d.name);
    }

    @Test
    void nameAndLoreFallBackToTheSameLanguage() {
        ItemTranslationService svc = Slimefun.getItemTranslationService();
        // Only "en" carries a name + type block for this probe; "zz" is unshipped. Before the I-1 fix,
        // the name fell back to English (via an explicit "en" lookup) while the lore blocks fell back to
        // the SERVER DEFAULT language instead - which in this MockBukkit harness is null (see
        // Slimefun#onUnitTestStart(), which constructs LocalizationService with no default language), so
        // the old code's blockForLanguage() had nothing to fall back to and produced an EMPTY type block.
        // The fix makes the lore blocks fall back through the exact same chain as the name (English), so
        // the type block must render here too, built from the "en" content, not be empty.
        String yaml = "ELECTRIC_MOTOR:\n"
            + "  name: '&aConsistent Fallback Motor'\n"
            + "  type:\n"
            + "  - '&7Type: Consistent Fallback Motor'\n";
        svc.loadTranslationsForTest("en",
            new java.io.ByteArrayInputStream(yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        svc.clearRenderCache();

        ItemTranslationService.RenderedDisplay d = svc.renderForPacket("ELECTRIC_MOTOR", "zz", TranslationConfig.FallbackMode.ENGLISH, true);

        Assertions.assertNotNull(d);
        Assertions.assertEquals("Consistent Fallback Motor", ChatColor.stripColor(d.name));

        boolean hasTypeLine = false;
        for (String line : d.lore) {
            if ("Type: Consistent Fallback Motor".equals(ChatColor.stripColor(line))) {
                hasTypeLine = true;
                break;
            }
        }
        Assertions.assertTrue(hasTypeLine, "lore must fall back to the SAME (english) block as the name, not be empty: " + d.lore);
    }

    @Test
    void differentCacheKeysDoNotCollide() {
        ItemTranslationService svc = Slimefun.getItemTranslationService();
        ItemTranslationService.RenderedDisplay byLanguage = svc.renderForPacket("ELECTRIC_MOTOR", "en", TranslationConfig.FallbackMode.ENGLISH, true);
        ItemTranslationService.RenderedDisplay byOtherLanguage = svc.renderForPacket("ELECTRIC_MOTOR", "zz", TranslationConfig.FallbackMode.ENGLISH, true);
        ItemTranslationService.RenderedDisplay byFallback = svc.renderForPacket("ELECTRIC_MOTOR", "en", TranslationConfig.FallbackMode.ID, true);

        Assertions.assertNotSame(byLanguage, byOtherLanguage, "different languageId must not share a cache entry");
        Assertions.assertNotSame(byLanguage, byFallback, "different fallback mode must not share a cache entry");
        Assertions.assertNotSame(byOtherLanguage, byFallback);
    }

    @Test
    @DisplayName("a raw-id fallback render is never cached, so it heals once a translation loads")
    void rawIdRenderIsNotCachedSoItHealsWhenTranslationsLoad() {
        ItemTranslationService svc = Slimefun.getItemTranslationService();
        // "q1" is an unshipped language with no translation yet, so the ID fallback renders the raw id -
        // exactly the "item shows ENDER_HELMET" symptom seen when a render happens before translations are
        // ready. That degraded result must NOT be memoized.
        ItemTranslationService.RenderedDisplay stale = svc.renderForPacket("ELECTRIC_MOTOR", "q1", TranslationConfig.FallbackMode.ID, true);
        Assertions.assertEquals("ELECTRIC_MOTOR", stale.name);

        // The translation now arrives (as when an addon calls registerTranslations post-boot). Without any
        // explicit clearRenderCache, the very next render must reflect it - a raw-id render must self-heal.
        String yaml = "ELECTRIC_MOTOR:\n  name: '&aQ1 Motor'\n";
        svc.loadTranslationsForTest("q1", new java.io.ByteArrayInputStream(yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        ItemTranslationService.RenderedDisplay healed = svc.renderForPacket("ELECTRIC_MOTOR", "q1", TranslationConfig.FallbackMode.ID, true);
        Assertions.assertEquals("Q1 Motor", ChatColor.stripColor(healed.name),
            "a raw-id render must not be cached; once the translation loads the next render must use it");
    }

    @Test
    @DisplayName("registerTranslations invalidates the render cache so late translations take effect")
    void registerTranslationsClearsRenderCache() {
        ItemTranslationService svc = Slimefun.getItemTranslationService();
        // A genuine (non-raw-id) render IS cached. Load a real English name so the render doesn't degrade
        // to the raw id (which is never cached) in the baseline-less unit harness.
        svc.loadTranslationsForTest("en", new java.io.ByteArrayInputStream(
            "ELECTRIC_MOTOR:\n  name: '&aRegistered Motor'\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        svc.clearRenderCache();

        ItemTranslationService.RenderedDisplay a = svc.renderForPacket("ELECTRIC_MOTOR", "en", TranslationConfig.FallbackMode.ENGLISH, true);
        ItemTranslationService.RenderedDisplay a2 = svc.renderForPacket("ELECTRIC_MOTOR", "en", TranslationConfig.FallbackMode.ENGLISH, true);
        Assertions.assertSame(a, a2, "precondition: a non-raw-id render must be cached");

        // An addon registering its translations post-boot must drop the stale renders.
        svc.registerTranslations(plugin);

        ItemTranslationService.RenderedDisplay b = svc.renderForPacket("ELECTRIC_MOTOR", "en", TranslationConfig.FallbackMode.ENGLISH, true);
        Assertions.assertNotSame(a, b, "registerTranslations must clear the render cache");
    }

    @Test
    @DisplayName("renderForPacket honours includeDescription: false drops the description block")
    void includeDescriptionToggleControlsDescriptionBlock() {
        ItemTranslationService svc = Slimefun.getItemTranslationService();
        String id = "ELECTRIC_MOTOR";
        // MockBukkit's unit-test LocalizationService has no default language, so loadBundled() (called in
        // load()) never actually loads languages/en/items.yml here - load a type + description block for
        // "en" explicitly, the same way the other tests in this file inject translations.
        String yaml = "ELECTRIC_MOTOR:\n"
            + "  name: '&aDescribed Motor'\n"
            + "  type:\n"
            + "  - '&7&oComponent'\n"
            + "  description:\n"
            + "  - '&7A crafting component.'\n";
        svc.loadTranslationsForTest("en", new java.io.ByteArrayInputStream(yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        svc.clearRenderCache();

        ItemTranslationService.RenderedDisplay withDesc = svc.renderForPacket(id, "en", TranslationConfig.FallbackMode.ENGLISH, true);
        ItemTranslationService.RenderedDisplay noDesc = svc.renderForPacket(id, "en", TranslationConfig.FallbackMode.ENGLISH, false);
        Assertions.assertNotEquals(withDesc.lore, noDesc.lore,
            "includeDescription=false must produce different lore than includeDescription=true when a description exists");
        Assertions.assertTrue(withDesc.lore.size() >= noDesc.lore.size(),
            "dropping the description can only remove lines");
    }

    /**
     * Loads a minimal, self-contained "en" stats block for the given id with a {@code %charge%} token,
     * rather than relying on the bundled production {@code items.yml} - MockBukkit's unit-test
     * {@code LocalizationService} has no default language, so {@code loadBundled()} never actually loads
     * it here (see {@link #includeDescriptionToggleControlsDescriptionBlock()}).
     */
    private static void loadChargeTemplate(@Nonnull ItemTranslationService svc, @Nonnull String id) {
        String yaml = id + ":\n"
            + "  name: '&9Test Jetpack'\n"
            + "  stats:\n"
            + "  - '&7Charge: %charge% / %max_charge% J'\n";
        svc.loadTranslationsForTest("en", new java.io.ByteArrayInputStream(yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        svc.clearRenderCache();
    }

    @Test
    @DisplayName("a %charge% token renders the stack's real live charge, not the static 0 the old template had")
    void chargeTokenRendersLiveChargeFromTheStack() {
        ItemTranslationService svc = Slimefun.getItemTranslationService();
        SlimefunItem probe = SlimefunItem.getById("REINFORCED_ALLOY_JETPACK");
        Assertions.assertNotNull(probe, "REINFORCED_ALLOY_JETPACK must be registered");
        Assertions.assertTrue(probe instanceof Rechargeable, "probe must be Rechargeable for this test to be meaningful");
        loadChargeTemplate(svc, "REINFORCED_ALLOY_JETPACK");

        Rechargeable rechargeable = (Rechargeable) probe;
        ItemStack stack = probe.getItem().clone();
        rechargeable.setItemCharge(stack, 42.5f);

        ItemTranslationService.RenderedDisplay display = svc.renderForPacketWithItem(
            stack, "REINFORCED_ALLOY_JETPACK", "en", TranslationConfig.FallbackMode.ENGLISH, true);

        Assertions.assertNotNull(display);
        boolean foundChargeLine = false;

        for (String line : display.lore) {
            Assertions.assertFalse(line.contains("%charge%"), "the %charge% token must be substituted: " + line);

            if (ChatColor.stripColor(line).contains("42.5")) {
                foundChargeLine = true;
            }
        }

        Assertions.assertTrue(foundChargeLine, "rendered lore must show the stack's real charge (42.5): " + display.lore);
    }

    @Test
    @DisplayName("two stacks of the same item with different live charge must not share a cached render")
    void differentChargeStacksDoNotServeEachOtherACachedRender() {
        ItemTranslationService svc = Slimefun.getItemTranslationService();
        SlimefunItem probe = SlimefunItem.getById("REINFORCED_ALLOY_JETPACK");
        Assertions.assertNotNull(probe);
        loadChargeTemplate(svc, "REINFORCED_ALLOY_JETPACK");
        Rechargeable rechargeable = (Rechargeable) probe;

        ItemStack low = probe.getItem().clone();
        rechargeable.setItemCharge(low, 5f);
        ItemStack high = probe.getItem().clone();
        rechargeable.setItemCharge(high, 95f);

        ItemTranslationService.RenderedDisplay lowDisplay = svc.renderForPacketWithItem(
            low, "REINFORCED_ALLOY_JETPACK", "en", TranslationConfig.FallbackMode.ENGLISH, true);
        ItemTranslationService.RenderedDisplay highDisplay = svc.renderForPacketWithItem(
            high, "REINFORCED_ALLOY_JETPACK", "en", TranslationConfig.FallbackMode.ENGLISH, true);

        Assertions.assertNotEquals(lowDisplay.lore, highDisplay.lore,
            "two instances with different live charge must render different lore, not silently share the (id, language) cached template's value");
    }

    @Test
    @DisplayName("an item with no dynamic tokens renders the exact same cached instance via renderForPacketWithItem")
    void itemWithoutDynamicTokensIsUnaffectedByTheSubstitutionPass() {
        ItemTranslationService svc = Slimefun.getItemTranslationService();
        // A real (non-raw-id) name is required: a raw-id fallback render is deliberately never cached
        // (see rawIdRenderIsNotCachedSoItHealsWhenTranslationsLoad above), which would make this assertion
        // flaky depending on what earlier tests left behind in the shared "en" translation map.
        svc.loadTranslationsForTest("en", new java.io.ByteArrayInputStream(
            "ELECTRIC_MOTOR:\n  name: '&aStatic Motor'\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        svc.clearRenderCache();
        SlimefunItem probe = SlimefunItem.getById("ELECTRIC_MOTOR");
        Assertions.assertNotNull(probe);
        Assertions.assertFalse(probe instanceof Rechargeable, "precondition: this probe must have no dynamic tokens");

        ItemTranslationService.RenderedDisplay viaId =
            svc.renderForPacket("ELECTRIC_MOTOR", "en", TranslationConfig.FallbackMode.ENGLISH, true);
        ItemTranslationService.RenderedDisplay viaItem = svc.renderForPacketWithItem(
            probe.getItem().clone(), "ELECTRIC_MOTOR", "en", TranslationConfig.FallbackMode.ENGLISH, true);

        Assertions.assertSame(viaId, viaItem,
            "an item with no %charge%/%uses% tokens must be entirely unaffected: same cached instance, no extra copy");
    }
}
