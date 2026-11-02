package io.github.thebusybiscuit.slimefun5.core.checks.packets;

import java.util.Locale;
import java.util.TreeSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Legacy-coded text reduced to what a client draws: each visible character with its colour and formats, so two
 * spellings of one look compare equal.
 *
 * @implNote A server re-encodes a stack's text on its way through a packet (from 1.13 through JSON components,
 *           from 1.20.5 through data components) and may drop a redundant code or repeat one per run, which is
 *           not a difference a player can see.
 */
public final class StyledText {

    private static final char SECTION = '§';

    private StyledText() {
    }

    /** One {@code [colour+formats]} marker per run of identically styled characters, then the characters. */
    @Nonnull
    public static String canonical(@Nullable String legacy) {
        if (legacy == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(legacy.length());
        String colour = "";
        TreeSet<Character> formats = new TreeSet<>();
        String lastStyle = null;
        int i = 0;
        while (i < legacy.length()) {
            char c = legacy.charAt(i);
            if (c == SECTION && i + 1 < legacy.length()) {
                char code = Character.toLowerCase(legacy.charAt(i + 1));
                if (code == 'x' && isHexRun(legacy, i)) {
                    colour = hexOf(legacy, i);
                    formats.clear();
                    i += 14;
                    continue;
                }
                if (isColour(code)) {
                    colour = String.valueOf(code);
                    formats.clear();
                } else if (code == 'r') {
                    colour = "";
                    formats.clear();
                } else if (code >= 'k' && code <= 'o') {
                    formats.add(code);
                }
                i += 2;
                continue;
            }
            String style = colour + formats;
            if (!style.equals(lastStyle)) {
                out.append('[').append(style).append(']');
                lastStyle = style;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static boolean isColour(char code) {
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
    }

    /** {@code §x§r§r§g§g§b§b}: fourteen characters. */
    private static boolean isHexRun(String text, int start) {
        if (start + 14 > text.length()) {
            return false;
        }
        for (int i = start + 2; i < start + 14; i += 2) {
            char digit = Character.toLowerCase(text.charAt(i + 1));
            if (text.charAt(i) != SECTION || Character.digit(digit, 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static String hexOf(String text, int start) {
        StringBuilder hex = new StringBuilder("#");
        for (int i = start + 3; i < start + 14; i += 2) {
            hex.append(text.charAt(i));
        }
        return hex.toString().toLowerCase(Locale.ROOT);
    }
}
