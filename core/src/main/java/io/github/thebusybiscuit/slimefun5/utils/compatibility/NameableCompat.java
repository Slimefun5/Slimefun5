package io.github.thebusybiscuit.slimefun5.utils.compatibility;

import javax.annotation.Nullable;

import org.bukkit.Nameable;

/**
 * {@link Nameable}, which Bukkit added in 1.11.
 *
 * @implNote Before 1.11 the class does not exist, so an {@code instanceof Nameable} on any non-null object
 *           throws {@code NoClassDefFoundError}. Testing the flag first never reaches the missing class.
 */
public final class NameableCompat {

    private static final boolean AVAILABLE = classExists("org.bukkit.Nameable");

    private NameableCompat() {}

    /** Whether {@code object} can carry a custom name; never on a server without {@link Nameable}. */
    public static boolean isNameable(@Nullable Object object) {
        return AVAILABLE && object instanceof Nameable;
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, NameableCompat.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError absent) {
            return false;
        }
    }
}
