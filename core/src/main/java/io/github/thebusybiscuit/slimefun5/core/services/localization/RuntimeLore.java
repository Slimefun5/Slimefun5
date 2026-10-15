package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import org.bukkit.ChatColor;

/**
 * Picks out the lore lines the server wrote onto one stack at runtime (a Networks amount, a chicken's
 * DNA, the soulbound marker), so the packet layer can show them beneath the composed lore instead of
 * replacing them.
 *
 * @implNote Old stacks still carry English lore baked in before the id-only rule, and nothing strips it,
 *           so any line that matches a known rendering of the item's template is treated as baked and
 *           dropped. Lines are compared with colours removed and every number and {@code %token%} masked,
 *           which makes a baked {@code Uses left: 3} match the template's {@code Uses left: %uses%}.
 */
final class RuntimeLore {

    private static final Pattern TOKEN = Pattern.compile("%[A-Za-z_]+%");
    private static final Pattern NUMBER = Pattern.compile("[0-9]+([.,][0-9]+)*");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private RuntimeLore() {}

    /**
     * @param stackLore
     *            the lore currently on the stack
     * @param templateLines
     *            {@link #normalize normalized} lines of every known rendering of the stack's template
     * @param alwaysBaked
     *            lines to drop whatever the template says, such as a legacy backpack id line
     * @return the runtime lines in their original order, blank edges trimmed and blank runs collapsed
     */
    @Nonnull
    static List<String> extraLines(@Nonnull List<String> stackLore, @Nonnull Set<String> templateLines, @Nonnull Predicate<String> alwaysBaked) {
        List<String> extra = new ArrayList<>();

        for (String line : stackLore) {
            if (isBlank(line)) {
                if (!extra.isEmpty() && !endsBlank(extra)) {
                    extra.add("");
                }
            } else if (!templateLines.contains(normalize(line)) && !alwaysBaked.test(line)) {
                extra.add(line);
            }
        }

        if (endsBlank(extra)) {
            extra.remove(extra.size() - 1);
        }

        return extra;
    }

    private static boolean endsBlank(@Nonnull List<String> lines) {
        return !lines.isEmpty() && lines.get(lines.size() - 1).isEmpty();
    }

    @Nonnull
    static Set<String> normalizeAll(@Nonnull Collection<String> lines) {
        Set<String> normalized = new HashSet<>();

        for (String line : lines) {
            normalized.add(normalize(line));
        }

        normalized.remove("");
        return normalized;
    }

    @Nonnull
    static String normalize(@Nonnull String line) {
        String plain = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', line));
        plain = TOKEN.matcher(plain).replaceAll("#");
        plain = NUMBER.matcher(plain).replaceAll("#");
        return WHITESPACE.matcher(plain).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(@Nonnull String line) {
        return normalize(line).isEmpty();
    }
}
