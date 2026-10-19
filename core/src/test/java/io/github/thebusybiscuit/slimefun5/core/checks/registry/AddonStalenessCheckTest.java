package io.github.thebusybiscuit.slimefun5.core.checks.registry;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.github.thebusybiscuit.slimefun5.core.checks.CheckContext;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckStep;
import io.github.thebusybiscuit.slimefun5.core.checks.Findings;
import io.github.thebusybiscuit.slimefun5.core.checks.Stage;

class AddonStalenessCheckTest {

    private static List<String> run(String coreVersion, AddonStalenessCheck.AddonInfo... addons) throws Exception {
        AddonStalenessCheck check = new AddonStalenessCheck(() -> Arrays.asList(addons), () -> coreVersion);
        Findings findings = new Findings(line -> { });
        Iterator<CheckStep> steps = check.steps(new CheckContext(findings, Stage.MAX, false));
        while (steps.hasNext()) {
            steps.next().run();
        }
        return findings.lines();
    }

    @Test
    void olderApiIsStale() throws Exception {
        List<String> lines = run("5.3.3.3", new AddonStalenessCheck.AddonInfo("DynaTech", "5.3.2.10"));

        Assertions.assertEquals(Collections.singletonList(
            "[check:addon-staleness] DynaTech: built against Slimefun 5.3.2.10, running on 5.3.3.3"), lines);
    }

    @Test
    void sameOrNewerIsNotStale() throws Exception {
        Assertions.assertEquals(Collections.emptyList(), run("5.3.3.3",
            new AddonStalenessCheck.AddonInfo("Same", "5.3.3.3"),
            new AddonStalenessCheck.AddonInfo("Newer", "5.3.4"),
            new AddonStalenessCheck.AddonInfo("Prefixed", "v5.3.3.3-UNOFFICIAL")));
    }

    @Test
    void unparseableVersionIsNotStale() throws Exception {
        Assertions.assertEquals(Collections.emptyList(), run("5.3.3.3",
            new AddonStalenessCheck.AddonInfo("A", "DEV"),
            new AddonStalenessCheck.AddonInfo("B", "development-abc123"),
            new AddonStalenessCheck.AddonInfo("C", "")));
        Assertions.assertEquals(Collections.emptyList(), run("DEV - abc",
            new AddonStalenessCheck.AddonInfo("D", "5.3.2.10")));
    }

    @Test
    void missingKeysAreOneFinding() throws Exception {
        List<String> lines = run("5.3.3.3",
            new AddonStalenessCheck.AddonInfo("One", null),
            new AddonStalenessCheck.AddonInfo("Two", null),
            new AddonStalenessCheck.AddonInfo("Three", null));

        Assertions.assertEquals(Collections.singletonList(
            "[check:addon-staleness] addons: built before slimefun-api was recorded: One, Two, Three"), lines);
    }

    @Test
    void missingKeysAreStillReportedWhenCoreVersionIsUnparseable() throws Exception {
        Assertions.assertEquals(1, run("DEV - abc", new AddonStalenessCheck.AddonInfo("One", null)).size());
    }

    @Test
    void apiVersionParsing() {
        Assertions.assertFalse(ApiVersion.parse("DEV").isPresent());
        Assertions.assertFalse(ApiVersion.parse("").isPresent());
        Assertions.assertTrue(ApiVersion.parse("5.3.3.3-UNOFFICIAL").get().compareTo(ApiVersion.parse("5.3.3.3").get()) == 0);
        Assertions.assertTrue(ApiVersion.parse("5.3.10").get().compareTo(ApiVersion.parse("5.3.9.9").get()) > 0);
    }

    @Test
    void equalVersionsWithTrailingZerosHashAlike() {
        ApiVersion shortForm = ApiVersion.parse("5.3").get();
        ApiVersion longForm = ApiVersion.parse("5.3.0").get();

        Assertions.assertEquals(shortForm, longForm);
        Assertions.assertEquals(shortForm.hashCode(), longForm.hashCode());
    }
}
