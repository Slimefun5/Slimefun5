package io.github.thebusybiscuit.slimefun5.api.items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.thebusybiscuit.slimefun5.core.services.localization.RenamedItems;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.PdcCompat;

/**
 * Lore lines that describe one item stack at runtime, such as how much a storage holds, rendered per viewer in
 * the viewer's language below the item's own lore.
 * <p>
 * Write these instead of adding lines to an item's lore: the packet layer re-renders every Slimefun item's lore
 * from its id, so a line added to the stack itself never reaches a player. Each line is a key in the addon's own
 * {@code languages/<lang>/messages.yml}, whose {@code {0}}, {@code {1}}, ... are replaced by the line's arguments;
 * pass {@link #itemName(ItemStack)} as an argument to name an item in the viewer's language.
 *
 * @implNote Stored as one persistent-data string so it survives on every version, including the NBT path below
 *           1.14. Lines and fields are separated by the ASCII record and unit separators, which no key or
 *           argument may contain.
 */
public final class LiveLore {

    private static final NamespacedKey KEY = new NamespacedKey("slimefun", "live_lore");
    private static final char LINE_SEPARATOR = '\u001e';
    private static final char FIELD_SEPARATOR = '\u001f';
    private static final char REFERENCE_MARKER = '\u0002';
    private static final String SLIMEFUN_PREFIX = REFERENCE_MARKER + "sf:";
    private static final String MATERIAL_PREFIX = REFERENCE_MARKER + "mc:";

    private LiveLore() {}

    /**
     * An argument that shows an item's name in each viewer's language: a Slimefun item by its translated name, a
     * vanilla item by its vanilla name, and an item a player renamed by that name as it is.
     */
    @Nonnull
    public static Object itemName(@Nonnull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (RenamedItems.isRenamed(meta)) {
            return meta.getDisplayName();
        }
        Optional<String> id = Slimefun.getItemDataService().getItemData(item);
        if (id.isPresent()) {
            return SLIMEFUN_PREFIX + id.get();
        }
        return meta != null && meta.hasDisplayName() ? meta.getDisplayName() : MATERIAL_PREFIX + item.getType().name();
    }

    /**
     * One stored argument as the renderer reads it.
     */
    public static final class Argument {

        public enum Kind {
            TEXT, SLIMEFUN_ITEM, MATERIAL
        }

        private final Kind kind;
        private final String value;

        private Argument(@Nonnull Kind kind, @Nonnull String value) {
            this.kind = kind;
            this.value = value;
        }

        @Nonnull
        public static Argument parse(@Nonnull String stored) {
            if (stored.startsWith(SLIMEFUN_PREFIX)) {
                return new Argument(Kind.SLIMEFUN_ITEM, stored.substring(SLIMEFUN_PREFIX.length()));
            }
            if (stored.startsWith(MATERIAL_PREFIX)) {
                return new Argument(Kind.MATERIAL, stored.substring(MATERIAL_PREFIX.length()));
            }
            return new Argument(Kind.TEXT, stored);
        }

        @Nonnull
        public Kind getKind() {
            return kind;
        }

        @Nonnull
        public String getValue() {
            return value;
        }
    }

    /**
     * One line: an addon's message key and the values for its placeholders.
     */
    public static final class Line {

        private final String addonId;
        private final String key;
        private final List<String> arguments;

        private Line(@Nonnull String addonId, @Nonnull String key, @Nonnull List<String> arguments) {
            this.addonId = addonId;
            this.key = key;
            this.arguments = Collections.unmodifiableList(arguments);
        }

        @Nonnull
        public static Line of(@Nonnull JavaPlugin addon, @Nonnull String key, @Nonnull Object... arguments) {
            List<String> values = new ArrayList<>(arguments.length);
            for (Object argument : arguments) {
                values.add(clean(String.valueOf(argument)));
            }
            return new Line(addon.getName().toLowerCase(Locale.ROOT), clean(key), values);
        }

        @Nonnull
        public String getAddonId() {
            return addonId;
        }

        @Nonnull
        public String getKey() {
            return key;
        }

        @Nonnull
        public List<String> getArguments() {
            return arguments;
        }

        private String encode() {
            StringBuilder encoded = new StringBuilder(addonId).append(FIELD_SEPARATOR).append(key);
            for (String argument : arguments) {
                encoded.append(FIELD_SEPARATOR).append(argument);
            }
            return encoded.toString();
        }

        @Nullable
        private static Line decode(@Nonnull String encoded) {
            String[] fields = encoded.split(String.valueOf(FIELD_SEPARATOR), -1);
            if (fields.length < 2) {
                return null;
            }
            return new Line(fields[0], fields[1], new ArrayList<>(Arrays.asList(fields).subList(2, fields.length)));
        }

        private static String clean(String text) {
            return text.replace(LINE_SEPARATOR, ' ').replace(FIELD_SEPARATOR, ' ');
        }
    }

    /**
     * Replaces the item's live lines; an empty list removes them.
     */
    public static void set(@Nonnull ItemMeta meta, @Nonnull List<Line> lines) {
        if (lines.isEmpty()) {
            clear(meta);
            return;
        }
        StringBuilder encoded = new StringBuilder();
        for (Line line : lines) {
            if (encoded.length() > 0) {
                encoded.append(LINE_SEPARATOR);
            }
            encoded.append(line.encode());
        }
        PdcCompat.setString(meta, KEY, encoded.toString());
    }

    public static void clear(@Nonnull ItemMeta meta) {
        PdcCompat.remove(meta, KEY);
    }

    @Nonnull
    public static List<Line> get(@Nullable ItemMeta meta) {
        String encoded = meta == null ? null : PdcCompat.getString(meta, KEY);
        if (encoded == null || encoded.isEmpty()) {
            return Collections.emptyList();
        }
        List<Line> lines = new ArrayList<>();
        for (String part : encoded.split(String.valueOf(LINE_SEPARATOR), -1)) {
            Line line = Line.decode(part);
            if (line != null) {
                lines.add(line);
            }
        }
        return lines;
    }
}
