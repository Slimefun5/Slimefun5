package io.github.thebusybiscuit.slimefun5.core.checks.scenarios;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import io.github.thebusybiscuit.slimefun5.core.checks.CheckContext;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckStep;
import io.github.thebusybiscuit.slimefun5.core.checks.Findings;
import io.github.thebusybiscuit.slimefun5.core.checks.Stage;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

class ItemPacketCheckTest {

    @BeforeAll
    static void load() {
        MockBukkit.mock();
        MockBukkit.load(Slimefun.class);
    }

    @AfterAll
    static void unload() {
        MockBukkit.unmock();
    }

    private static ItemStack named(Material type, String name, String... lore) {
        ItemStack stack = new ItemStack(type);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) {
            meta.setLore(Arrays.asList(lore));
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private static List<String> report(ItemPacketCheck.ShownStacks stacks, int languages) {
        Findings findings = new Findings(line -> { });
        stacks.report(new CheckContext(findings, Stage.MAX, false), "TEST_ITEM", languages);
        return findings.lines();
    }

    @Test
    void sameLookWithOtherCodesIsNotReported() {
        ItemPacketCheck.ShownStacks stacks = new ItemPacketCheck.ShownStacks("set-slot");
        ItemStack intended = named(Material.PAPER, "§bReactor", "§7Machine");
        List<ItemStack> fed = Collections.singletonList(intended);

        stacks.compare("de", intended, fed, Collections.singletonList(named(Material.PAPER, "§b§bReactor", "§7Machine")));

        Assertions.assertEquals(Collections.emptyList(), report(stacks, 1));
    }

    @Test
    void vanillaItemWithoutANameIsNotBlank() {
        ItemPacketCheck.ShownStacks stacks = new ItemPacketCheck.ShownStacks("set-slot");
        ItemStack elytra = new ItemStack(Material.ELYTRA);

        stacks.compare("de", elytra, Collections.singletonList(elytra), Collections.singletonList(new ItemStack(Material.ELYTRA)));

        Assertions.assertEquals(Collections.emptyList(), report(stacks, 1));
    }

    @Test
    void untranslatedNameIsReportedOncePerProblemWithItsLanguages() {
        ItemPacketCheck.ShownStacks stacks = new ItemPacketCheck.ShownStacks("set-slot");
        ItemStack intended = named(Material.PAPER, "§bReaktor");
        List<ItemStack> fed = Collections.singletonList(intended);

        stacks.compare("de", intended, fed, Collections.singletonList(named(Material.PAPER, "§fReactor")));
        stacks.compare("nl", intended, fed, Collections.singletonList(named(Material.PAPER, "§fReactor")));

        List<String> lines = report(stacks, 3);
        Assertions.assertEquals(1, lines.size(), lines.toString());
        Assertions.assertTrue(lines.get(0).contains("TEST_ITEM: through set-slot shows a different name than core intends in de, nl"), lines.get(0));
    }

    @Test
    void rawIdAndChangedNeighbourAreReported() {
        ItemPacketCheck.ShownStacks stacks = new ItemPacketCheck.ShownStacks("window-items");
        ItemStack intended = named(Material.PAPER, "Reactor");
        List<ItemStack> fed = Arrays.asList(intended, new ItemStack(Material.STONE), new ItemStack(Material.AIR));

        stacks.compare("en", intended, fed, Arrays.asList(named(Material.PAPER, "NUCLEAR_REACTOR"), named(Material.STONE, "Reactor"), new ItemStack(Material.AIR)));

        List<String> lines = report(stacks, 1);
        Assertions.assertEquals(2, lines.size(), lines.toString());
        Assertions.assertTrue(lines.get(0).contains("its name shows a raw id in every language"), lines.get(0));
        Assertions.assertTrue(lines.get(1).contains("changes the vanilla stack beside it in slot 1"), lines.get(1));
    }

    @Test
    void serverWithoutItemPacketsSaysSoInsteadOfPassing() throws Exception {
        Findings findings = new Findings(line -> { });
        Iterator<CheckStep> steps = new ItemPacketCheck().steps(new CheckContext(findings, Stage.MAX, false));
        while (steps.hasNext()) {
            steps.next().run();
        }

        Assertions.assertEquals(1, findings.lines().size(), findings.lines().toString());
        Assertions.assertTrue(findings.lines().get(0).contains("[check:item-packet] packets: this server's item packets cannot be built"), findings.lines().get(0));
    }
}
