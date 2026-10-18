package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import java.lang.reflect.Constructor;

import org.bukkit.Bukkit;

/** The server's own persistent data container (1.14+), which is plain data and needs no entity behind it. */
final class PersistentData {

    private PersistentData() {}

    static Object newContainer() {
        try {
            String craft = Bukkit.getServer().getClass().getPackage().getName() + ".persistence.";
            Class<?> registry = Class.forName(craft + "CraftPersistentDataTypeRegistry");
            Constructor<?> container = Class.forName(craft + "CraftPersistentDataContainer").getConstructor(registry);
            return container.newInstance(registry.getConstructor().newInstance());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not create a persistent data container", failure);
        }
    }
}
