package io.github.thebusybiscuit.slimefun5.core.checks.registry;

import java.util.Collections;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EnglishNameLintTest {

    @ParameterizedTest
    @ValueSource(strings = { "Electric Jetpack II", "Anti-Missile Missile", "Molten Redstone Alloy (Alloy)", "Tin Item Casing",
        "Boot Plates (Part)", "Who needs pressure plates? (Mail Links)", "&6MagSteel Plate", "Programmable Android (Butcher)" })
    void acceptsNamesInUseToday(String name) {
        Assertions.assertEquals(Collections.emptyList(), EnglishNameLint.problems(name));
    }

    @ParameterizedTest
    @ValueSource(strings = { "Main item", "Item", "TODO Generator", "Dummy Block", "Placeholder" })
    void rejectsPlaceholderNames(String name) {
        Assertions.assertEquals(Collections.singletonList("is a placeholder name"), EnglishNameLint.problems(name));
    }

    @ParameterizedTest
    @ValueSource(strings = { "Electric Jetpack - VII", "Food Composter (II)", "&eEnder Lump &7- III" })
    void rejectsOldLevelForms(String name) {
        Assertions.assertEquals(1, EnglishNameLint.problems(name).size(), name);
    }

    @ParameterizedTest
    @ValueSource(strings = { "Copper Copper Wire", "Block block (Part)" })
    void rejectsDoubledWords(String name) {
        Assertions.assertEquals(Collections.singletonList("repeats a word"), EnglishNameLint.problems(name));
    }
}
