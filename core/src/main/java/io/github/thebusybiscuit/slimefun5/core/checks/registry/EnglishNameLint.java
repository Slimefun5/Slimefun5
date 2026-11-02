package io.github.thebusybiscuit.slimefun5.core.checks.registry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import io.github.thebusybiscuit.slimefun5.core.services.localization.ItemNameStyle;

/**
 * The wording rules an English item name must follow, since every translation is made from it.
 *
 * @implNote Casing and singular/plural rules were measured over all 3,400 English names on 2026-11-02 and
 *           flagged only correct names (trait titles, {@code Boot Plates}, brand names such as {@code MagSteel}),
 *           so they are deliberately absent.
 */
final class EnglishNameLint {

    private static final Set<String> PLACEHOLDER_WORDS = new HashSet<>(Arrays.asList("todo", "fixme", "placeholder", "dummy", "test"));
    private static final Set<String> PLACEHOLDER_NAMES = new HashSet<>(Arrays.asList("item", "main item", "unnamed", "name"));
    private static final Pattern OLD_LEVEL_FORM = Pattern.compile("(\\s-\\s*[IVX]+|\\([IVX]+\\))$");
    private static final Pattern TRAILING_QUALIFIER = Pattern.compile("\\s*\\([^()]*\\)$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private EnglishNameLint() {
    }

    @Nonnull
    static List<String> problems(@Nonnull String authoredName) {
        String name = ItemNameStyle.plainText(authoredName);
        List<String> problems = new ArrayList<>();

        if (isPlaceholder(name)) {
            problems.add("is a placeholder name");
        }

        if (OLD_LEVEL_FORM.matcher(name).find()) {
            problems.add("writes its level as \"- II\" or \"(II)\" instead of a trailing \"II\"");
        }

        if (hasDoubledWord(TRAILING_QUALIFIER.matcher(name).replaceFirst(""))) {
            problems.add("repeats a word");
        }

        return problems;
    }

    private static boolean isPlaceholder(@Nonnull String name) {
        String lower = name.toLowerCase(Locale.ROOT);

        if (PLACEHOLDER_NAMES.contains(lower)) {
            return true;
        }

        for (String word : WHITESPACE.split(lower)) {
            if (PLACEHOLDER_WORDS.contains(word)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasDoubledWord(@Nonnull String name) {
        String[] words = WHITESPACE.split(name.toLowerCase(Locale.ROOT));

        for (int i = 1; i < words.length; i++) {
            if (!words[i].isEmpty() && words[i].equals(words[i - 1])) {
                return true;
            }
        }

        return false;
    }
}
