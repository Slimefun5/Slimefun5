package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.ChatColor;

/**
 * One authored {@code menus.yml} line that may carry {@code {0}}-style captures, matched against the
 * colourless text of a live menu item. Everything around a capture is matched literally.
 */
final class MenuTextPattern {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)\\}");

    private final String key;
    private final Pattern pattern;
    private final List<Integer> groupIndices;

    private MenuTextPattern(@Nonnull String key, @Nonnull Pattern pattern, @Nonnull List<Integer> groupIndices) {
        this.key = key;
        this.pattern = pattern;
        this.groupIndices = groupIndices;
    }

    @Nonnull
    static MenuTextPattern compile(@Nonnull String authored) {
        String key = colourless(authored);
        StringBuilder regex = new StringBuilder();
        List<Integer> groupIndices = new ArrayList<>();
        Matcher placeholder = PLACEHOLDER.matcher(key);
        int literalStart = 0;

        while (placeholder.find()) {
            regex.append(Pattern.quote(key.substring(literalStart, placeholder.start()))).append("(.+?)");
            groupIndices.add(Integer.parseInt(placeholder.group(1)));
            literalStart = placeholder.end();
        }

        regex.append(Pattern.quote(key.substring(literalStart)));
        return new MenuTextPattern(key, Pattern.compile(regex.toString()), groupIndices);
    }

    /** The colourless authored text, which is what two patterns are compared by. */
    @Nonnull
    String key() {
        return key;
    }

    @Nonnull
    Set<Integer> captureIndices() {
        return new TreeSet<>(groupIndices);
    }

    /**
     * @return whether {@code live} matches; only a match records its captures, and a capture already
     *         recorded by an earlier pattern must agree with it
     */
    boolean matchInto(@Nullable String live, @Nonnull Map<Integer, String> captures) {
        Matcher matcher = pattern.matcher(colourless(live));

        if (!matcher.matches()) {
            return false;
        }

        Map<Integer, String> found = new HashMap<>();

        for (int group = 0; group < groupIndices.size(); group++) {
            int index = groupIndices.get(group);
            String value = matcher.group(group + 1);
            String earlier = found.containsKey(index) ? found.get(index) : captures.get(index);

            if (earlier != null && !earlier.equals(value)) {
                return false;
            }

            found.put(index, value);
        }

        captures.putAll(found);
        return true;
    }

    @Nonnull
    static Set<Integer> placeholdersIn(@Nullable String text) {
        Set<Integer> found = new TreeSet<>();

        if (text != null) {
            Matcher placeholder = PLACEHOLDER.matcher(text);

            while (placeholder.find()) {
                found.add(Integer.parseInt(placeholder.group(1)));
            }
        }

        return found;
    }

    @Nullable
    static String substitute(@Nullable String text, @Nonnull Map<Integer, String> captures) {
        if (text == null || captures.isEmpty()) {
            return text;
        }

        Matcher placeholder = PLACEHOLDER.matcher(text);
        StringBuffer out = new StringBuffer();

        while (placeholder.find()) {
            String value = captures.get(Integer.parseInt(placeholder.group(1)));
            placeholder.appendReplacement(out, Matcher.quoteReplacement(value != null ? value : placeholder.group()));
        }

        placeholder.appendTail(out);
        return out.toString();
    }

    @Nonnull
    static List<String> substituteAll(@Nonnull List<String> lines, @Nonnull Map<Integer, String> captures) {
        if (captures.isEmpty()) {
            return lines;
        }

        List<String> out = new ArrayList<>(lines.size());

        for (String line : lines) {
            out.add(substitute(line, captures));
        }

        return Collections.unmodifiableList(out);
    }

    /**
     * @implNote The authored text uses {@code &} codes while the live item carries the translated
     *           section-sign form, so neither side can be compared as written.
     */
    @Nonnull
    static String colourless(@Nullable String value) {
        return value == null ? "" : ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', value));
    }
}
