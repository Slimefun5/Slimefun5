package io.github.thebusybiscuit.slimefun5.core.guide.wiki;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * Pins the topic set now that it lives in {@code /wiki/topics.yml} rather than in Java, so the
 * refactor cannot silently drop, rename or reorder a topic. The web wiki generator reads the same
 * file and would lose pages for anything removed here.
 */
class WikiTopicsResourceTest {

    private static final List<String> EXPECTED_IDS = Arrays.asList(
        "getting_started", "research", "multiblocks", "ore_processing", "smeltery", "energy",
        "power_generation", "electric_machines", "cargo", "androids", "geo_mining", "gps",
        "talismans", "magic", "armor_gadgets", "backpacks", "food_farming", "soulbound");

    private static WikiText wikiText;

    @BeforeAll
    public static void load() {
        MockBukkit.mock();
        MockBukkit.load(Slimefun.class);

        wikiText = new WikiText();
        wikiText.loadBundled();
    }

    @AfterAll
    public static void unload() {
        MockBukkit.unmock();
    }

    @Test
    void topicsLoadFromTheBundledResourceInOrder() {
        List<String> ids = wikiText.getTopics().stream().map(WikiTopic::getId).collect(Collectors.toList());
        Assertions.assertEquals(EXPECTED_IDS, ids);
    }

    @Test
    void everyTopicHasATitleIconAndSummary() {
        for (WikiTopic topic : wikiText.getTopics()) {
            Assertions.assertFalse(topic.getDisplayName().isEmpty(), topic.getId() + " has no title");
            Assertions.assertNotNull(topic.getIcon(), topic.getId() + " has no icon");
            Assertions.assertFalse(topic.getSummary().isEmpty(), topic.getId() + " has no summary");
        }
    }

    @Test
    void theBundledResourceIsTheSourceOfTopics() {
        Assertions.assertNotNull(
            Slimefun.class.getResourceAsStream("/wiki/topics.yml"),
            "topics.yml must be bundled");
    }

    @Test
    void knownTitlesSurviveTheMoveOutOfJava() {
        WikiTopic cargo = wikiText.getTopics().stream()
            .filter(t -> "cargo".equals(t.getId())).findFirst().orElse(null);

        Assertions.assertNotNull(cargo);
        Assertions.assertEquals("Cargo Networks", cargo.getDisplayName());
        Assertions.assertEquals("&7Move items automatically", cargo.getSummary());
    }
}
