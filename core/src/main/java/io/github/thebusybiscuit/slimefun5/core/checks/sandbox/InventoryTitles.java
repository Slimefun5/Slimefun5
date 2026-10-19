package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.bukkit.inventory.Inventory;

/**
 * The title an inventory was created with, which is the title a viewer's window opens with.
 *
 * @implNote {@code Inventory#getTitle} was removed in 1.14; after that the title lives only on the server's
 *           own container behind the Bukkit wrapper, as a string or a chat component depending on the version.
 *           An inventory that still has a {@code getTitle}, such as MockBukkit's, may answer with a component too.
 */
final class InventoryTitles {

    private InventoryTitles() {}

    static String of(Inventory inventory) {
        try {
            Method getTitle = inventory.getClass().getMethod("getTitle");
            return asText(getTitle.invoke(inventory));
        } catch (NoSuchMethodException removed) {
            return fromContainer(inventory);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not read the title of " + inventory, failure);
        }
    }

    private static String fromContainer(Inventory inventory) {
        try {
            Object container = field(inventory.getClass(), "inventory").get(inventory);
            Field title = field(container.getClass(), "title");
            if (title == null) {
                return "";
            }
            return asText(title.get(container));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not read the title of " + inventory, failure);
        }
    }

    private static String asText(Object title) throws ReflectiveOperationException {
        if (title == null || title instanceof String) {
            return (String) title;
        }
        Class<?> adventure = loadOrNull("net.kyori.adventure.text.Component");
        if (adventure != null && adventure.isInstance(title)) {
            Class<?> serializer = Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
            Object legacy = serializer.getMethod("legacySection").invoke(null);
            return (String) serializer.getMethod("serialize", adventure).invoke(legacy, title);
        }
        Class<?> chat = loadOrNull(craftChatMessage());
        if (chat != null) {
            for (Method method : chat.getMethods()) {
                if (method.getName().equals("fromComponent") && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isInstance(title)) {
                    return (String) method.invoke(null, title);
                }
            }
        }
        return title.toString();
    }

    private static String craftChatMessage() {
        String serverPackage = org.bukkit.Bukkit.getServer().getClass().getPackage().getName();
        return serverPackage + ".util.CraftChatMessage";
    }

    private static Class<?> loadOrNull(String name) {
        try {
            return Class.forName(name, false, InventoryTitles.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError absent) {
            return null;
        }
    }

    private static Field field(Class<?> owner, String name) {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException absent) {
            }
        }
        return null;
    }
}
