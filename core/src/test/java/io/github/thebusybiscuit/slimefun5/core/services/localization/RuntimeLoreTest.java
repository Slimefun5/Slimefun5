package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RuntimeLoreTest {

    private static final Set<String> NO_TEMPLATE = Collections.emptySet();

    @Test
    void keepsLinesTheTemplateDoesNotHave() {
        List<String> stack = Arrays.asList("§7Voiding: §eTrue", "§7Amount: §e1016768");

        Assertions.assertEquals(stack, RuntimeLore.extraLines(stack, NO_TEMPLATE, line -> false));
    }

    @Test
    void dropsBakedTemplateLinesWhateverTheirNumbersAndColours() {
        Set<String> template = RuntimeLore.normalizeAll(Arrays.asList("&7Uses left: &e%uses%", "&8⇨ &e⚡ &7%charge% / %max_charge% J"));
        List<String> stack = Arrays.asList("§7Uses left: §e3", "§8⇨ §e⚡ §712.5 / 100 J", "§7DNA: §bBBCCDdFFSSWW");

        Assertions.assertEquals(Collections.singletonList("§7DNA: §bBBCCDdFFSSWW"), RuntimeLore.extraLines(stack, template, line -> false));
    }

    @Test
    void dropsLinesTheCallerAlwaysTreatsAsBaked() {
        List<String> stack = Arrays.asList("§7ID: 00000000-0000-0000-0000-000000000000#4", "§7Owner: Steve");

        Assertions.assertEquals(Collections.singletonList("§7Owner: Steve"), RuntimeLore.extraLines(stack, NO_TEMPLATE, line -> line.startsWith("§7ID: ")));
    }

    @Test
    void trimsBlankEdgesAndCollapsesBlankRuns() {
        List<String> stack = Arrays.asList("", "§7Health: 4", "", "§r", "§7Type: Feather", "");

        Assertions.assertEquals(Arrays.asList("§7Health: 4", "", "§7Type: Feather"), RuntimeLore.extraLines(stack, NO_TEMPLATE, line -> false));
    }

    @Test
    void returnsNothingWhenEveryLineIsBaked() {
        Set<String> template = RuntimeLore.normalizeAll(Collections.singletonList("&7A chicken, made portable."));

        Assertions.assertTrue(RuntimeLore.extraLines(Arrays.asList("", "§7A chicken, made portable.", ""), template, line -> false).isEmpty());
    }
}
