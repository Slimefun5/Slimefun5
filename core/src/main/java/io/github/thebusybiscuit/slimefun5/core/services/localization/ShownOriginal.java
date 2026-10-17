package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.PdcCompat;

/**
 * The server's own name and lore of a stack, carried on the translated copy a client is shown, so that a
 * stack the client sends back (creative mode, pre-1.21.5 clicks) can be returned to exactly what the
 * server sent instead of being stored with the viewer's translation baked in.
 *
 * @implNote It holds the server's original and never a translation, and it is only ever written onto the
 *           per-viewer copy that is discarded after sending, so no viewer's language reaches shared state.
 */
final class ShownOriginal {

    private static final char ABSENT = '~';

    @Nullable
    private final String name;
    @Nullable
    private final List<String> lore;

    private ShownOriginal(@Nullable String name, @Nullable List<String> lore) {
        this.name = name;
        this.lore = lore;
    }

    @Nonnull
    static ShownOriginal of(@Nonnull ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return new ShownOriginal(null, null);
        }
        return new ShownOriginal(meta.hasDisplayName() ? meta.getDisplayName() : null,
            meta.hasLore() ? new ArrayList<>(meta.getLore()) : null);
    }

    /**
     * Writes this original onto {@code shown} when its name or lore differs from it.
     *
     * @implNote A stack that already carries a stash keeps it: it can only have one if an earlier restore
     *           failed and the translated copy was stored, and then the existing stash is the true original.
     */
    void stashOnto(@Nonnull ItemStack shown) {
        ItemMeta meta = shown.getItemMeta();
        if (meta == null || matches(of(shown)) || PdcCompat.getString(meta, key()) != null) {
            return;
        }
        PdcCompat.setString(meta, key(), encode(name, lore));
        shown.setItemMeta(meta);
    }

    /** Returns {@code incoming} to the name and lore the server sent, if it carries a stash. */
    static boolean restore(@Nonnull ItemStack incoming) {
        ItemMeta meta = incoming.getItemMeta();
        if (meta == null) {
            return false;
        }
        ShownOriginal original = decode(PdcCompat.getString(meta, key()));
        if (original == null) {
            return false;
        }
        meta.setDisplayName(original.name);
        meta.setLore(original.lore);
        PdcCompat.remove(meta, key());
        incoming.setItemMeta(meta);
        return true;
    }

    private boolean matches(@Nonnull ShownOriginal other) {
        return Objects.equals(name, other.name) && Objects.equals(lore, other.lore);
    }

    @Nonnull
    private static io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey key() {
        return Slimefun.getRegistry().getShownOriginalKey();
    }

    /**
     * Length-prefixed, so no character a name or lore line may contain can break it: {@code ~} for an
     * absent name, else {@code <length>:<name>}; then {@code ~} for absent lore, else
     * {@code <count>#} followed by one {@code <length>:<line>} per line.
     */
    @Nonnull
    static String encode(@Nullable String name, @Nullable List<String> lore) {
        StringBuilder out = new StringBuilder();
        appendEntry(out, name);
        if (lore == null) {
            out.append(ABSENT);
        } else {
            out.append(lore.size()).append('#');
            for (String line : lore) {
                appendEntry(out, line);
            }
        }
        return out.toString();
    }

    private static void appendEntry(StringBuilder out, @Nullable String text) {
        if (text == null) {
            out.append(ABSENT);
        } else {
            out.append(text.length()).append(':').append(text);
        }
    }

    /** The inverse of {@link #encode}, or {@code null} for an absent or malformed value. */
    @Nullable
    static ShownOriginal decode(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        try {
            Cursor cursor = new Cursor(raw);
            String name = cursor.entry();
            List<String> lore = null;
            if (!cursor.takeAbsent()) {
                int count = cursor.number('#');
                lore = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    lore.add(cursor.entry());
                }
            }
            return cursor.atEnd() ? new ShownOriginal(name, lore) : null;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    @Nullable
    String name() {
        return name;
    }

    @Nullable
    List<String> lore() {
        return lore;
    }

    private static final class Cursor {

        private final String raw;
        private int position;

        Cursor(String raw) {
            this.raw = raw;
        }

        boolean takeAbsent() {
            if (raw.charAt(position) == ABSENT) {
                position++;
                return true;
            }
            return false;
        }

        @Nullable
        String entry() {
            if (takeAbsent()) {
                return null;
            }
            int length = number(':');
            String text = raw.substring(position, position + length);
            position += length;
            return text;
        }

        int number(char terminator) {
            int end = raw.indexOf(terminator, position);
            int value = Integer.parseInt(raw.substring(position, end));
            if (value < 0) {
                throw new IllegalArgumentException("negative length");
            }
            position = end + 1;
            return value;
        }

        boolean atEnd() {
            return position == raw.length();
        }
    }
}
