package io.github.thebusybiscuit.slimefun5.core.checks.registry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockbukkit.mockbukkit.MockBukkit;

import io.github.thebusybiscuit.slimefun5.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun5.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun5.core.checks.Check;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckContext;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckStep;
import io.github.thebusybiscuit.slimefun5.core.checks.Findings;
import io.github.thebusybiscuit.slimefun5.core.checks.Stage;
import io.github.thebusybiscuit.slimefun5.core.services.localization.ItemTranslationTestAccess;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.implementation.setup.SlimefunItemSetup;
import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RegistryChecksTest {

    private static Slimefun plugin;

    @BeforeAll
    static void load() {
        MockBukkit.mock();
        plugin = MockBukkit.load(Slimefun.class);
        Slimefun.getCfg().setValue("URID.enable-tickers", true);
        SlimefunItemSetup.setup(plugin);
        ItemTranslationTestAccess.enableBundledLanguages();
        Slimefun.getItemTranslationService().loadBundled();
    }

    @AfterAll
    static void unload() {
        MockBukkit.unmock();
    }

    private static List<String> run(Check check) throws Exception {
        Findings findings = new Findings(line -> { });
        Iterator<CheckStep> steps = check.steps(new CheckContext(findings, Stage.MAX, false));
        while (steps.hasNext()) {
            steps.next().run();
        }
        return findings.lines();
    }

    @Test
    @Order(1)
    void cleanRegistryHasNoFindings() throws Exception {
        Assertions.assertEquals(new ArrayList<String>(), run(new ItemNameCheck()));
        Assertions.assertEquals(new ArrayList<String>(), run(new ItemRegistrationCheck()));
        Assertions.assertEquals(new ArrayList<String>(), run(new RecipeCheck()));
        Assertions.assertEquals(new ArrayList<String>(), run(new MenuPresetCheck()));
    }

    @Test
    @Order(2)
    void itemWithoutNameInALanguageIsReported() throws Exception {
        ItemGroup group = new ItemGroup(new NamespacedKey(plugin, "registry_checks_group"), new ItemStack(Material.EMERALD));
        new SlimefunItem(group, new SlimefunItemStack("REGISTRY_CHECK_EN_ONLY", Material.PAPER), RecipeType.NULL, new ItemStack[9]).register(plugin);
        ItemTranslationTestAccess.load("en", "REGISTRY_CHECK_EN_ONLY:\n  name: '&6English only'\n");

        List<String> lines = run(new ItemNameCheck());
        List<String> mine = lines.stream().filter(line -> line.contains("REGISTRY_CHECK_EN_ONLY")).collect(Collectors.toList());

        Assertions.assertEquals(1, mine.size(), lines.toString());
        Assertions.assertTrue(mine.get(0).startsWith("[check:item-name] REGISTRY_CHECK_EN_ONLY: has no name translation in "), mine.get(0));
        Assertions.assertTrue(mine.get(0).contains("languages: ") && mine.get(0).contains("de"), mine.get(0));
        Assertions.assertFalse(mine.get(0).contains(": en,") || mine.get(0).contains(", en,") || mine.get(0).contains(", en ("), mine.get(0));
    }

    @Test
    @Order(3)
    void presetWithInferredSizeIsNotReported() throws Exception {
        new InferredSizePreset();

        Assertions.assertTrue(Slimefun.getRegistry().getMenuPresets().containsKey("REGISTRY_CHECK_PRESET"));
        Assertions.assertEquals(new ArrayList<String>(), run(new MenuPresetCheck()));
    }

    private static final class InferredSizePreset extends BlockMenuPreset {

        InferredSizePreset() {
            super("REGISTRY_CHECK_PRESET", "REGISTRY_CHECK_PRESET");
        }

        @Override
        public void init() {
        }

        @Override
        public boolean canOpen(Block block, Player player) {
            return true;
        }

        @Override
        public int[] getSlotsAccessedByItemTransport(me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow flow) {
            return new int[0];
        }
    }
}
