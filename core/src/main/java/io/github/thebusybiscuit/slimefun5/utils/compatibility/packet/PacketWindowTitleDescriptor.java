package io.github.thebusybiscuit.slimefun5.utils.compatibility.packet;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;

/**
 * Describes the clientbound open-window packet and knows how to retitle it in place, so a vanilla
 * container's title can be corrected before the client ever renders it (rather than one tick later via
 * {@code InventoryView#setTitle}, which still lets the wrong title flash for a frame). Resolved reflectively
 * per server version, in the same spirit as {@link PacketItemDescriptor}: unresolvable pieces simply leave
 * this descriptor unavailable, never a broken packet.
 */
public final class PacketWindowTitleDescriptor {

    /** The open-window packet under each mapping, in the order {@link PacketItemDescriptor} documents. */
    private static final String[] PACKET_CLASS_NAMES = {
        "net.minecraft.network.protocol.game.ClientboundOpenScreenPacket",
        "net.minecraft.network.protocol.game.PacketPlayOutOpenWindow",
        "PacketPlayOutOpenWindow",
    };

    /** The window type a 1.8 open-window packet names a chest by. */
    private static final String CHEST_WINDOW_TYPE = "minecraft:chest";

    private final Class<?> packetClass;
    private final Class<?> componentClass;
    private final Field titleField;
    private final Method fromStringMethod;
    @Nullable
    private final Method fromComponentMethod;

    private PacketWindowTitleDescriptor(Class<?> packetClass, Class<?> componentClass, Field titleField, Method fromStringMethod, @Nullable Method fromComponentMethod) {
        this.packetClass = packetClass;
        this.componentClass = componentClass;
        this.titleField = titleField;
        this.fromStringMethod = fromStringMethod;
        this.fromComponentMethod = fromComponentMethod;
    }

    /**
     * This descriptor for a packet class located some other way, such as a test's stand-in, or {@code null} when
     * the packet has no title field of the component type.
     *
     * @param fromString turns a legacy-formatted string into an array of components, like {@code CraftChatMessage.fromString}
     * @param fromComponent turns a component back into a legacy-formatted string, or {@code null} when titles are never read
     */
    @Nullable
    public static PacketWindowTitleDescriptor of(@Nonnull Class<?> packetClass, @Nonnull Class<?> componentClass, @Nonnull Method fromString, @Nullable Method fromComponent) {
        Field titleField = PacketReflect.firstFieldOfType(packetClass, componentClass);
        if (titleField == null) {
            return null;
        }
        fromString.setAccessible(true);
        if (fromComponent != null) {
            fromComponent.setAccessible(true);
        }
        return new PacketWindowTitleDescriptor(packetClass, componentClass, titleField, fromString, fromComponent);
    }

    /**
     * Resolves this descriptor for the running server, or {@code null} if any of the packet class, its
     * single component-typed title field, or a way to build a component from a plain string cannot be
     * found - in which case window retitling stays on the (slower, one-tick-later) fallback path.
     *
     * @implNote The title field is looked up by TYPE, never by name, and only trusted when the packet
     *           class declares exactly one such field - on server versions where the title is instead a
     *           raw (possibly JSON-encoded) {@code String} field, that would be ambiguous against the
     *           packet's other {@code String} fields (e.g. its window-type identifier), so this
     *           deliberately does not attempt a {@code String}-field fallback: getting that wrong would
     *           corrupt an unrelated field instead of just failing to retitle.
     */
    @Nullable
    public static PacketWindowTitleDescriptor resolve() {
        Class<?> packetClass = resolvePacketClass();
        if (packetClass == null) {
            return null;
        }

        Class<?> componentClass = resolveComponentClass();
        if (componentClass == null) {
            return null;
        }

        Method fromString = resolveFromStringMethod(componentClass);
        if (fromString == null) {
            return null;
        }

        return of(packetClass, componentClass, fromString, resolveFromComponentMethod(componentClass));
    }

    public boolean matches(@Nullable Object packet) {
        return packet != null && packetClass.isInstance(packet);
    }

    /**
     * Rewrites the packet's title field to {@code text}, translated into whatever chat-component
     * representation this server version expects. Never throws - on any failure the packet is left
     * exactly as it was (its vanilla-supplied title), which is no worse than the pre-existing flash.
     */
    public boolean retitle(@Nonnull Object packet, @Nonnull String text) {
        try {
            Object component = component(text);
            if (component == null) {
                return false;
            }
            titleField.set(packet, component);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * A new open-window packet for a chest of {@code size} slots titled {@code text}, built with this server's own
     * constructor and never sent anywhere, or {@code null} when no constructor can be filled.
     *
     * @implNote The constructor with the most parameters that can all be filled is used, so 1.8's
     *           {@code (id, type, title, size)} wins over {@code (id, type, title)}; from 1.14 the only one that
     *           takes a title is {@code (id, menu type, title)}.
     */
    @Nullable
    public Object newPacket(int windowId, @Nonnull String text, int size) {
        Object component = component(text);
        if (component == null) {
            return null;
        }
        List<Constructor<?>> constructors = new ArrayList<>(Arrays.asList(packetClass.getDeclaredConstructors()));
        constructors.sort(Comparator.comparingInt((Constructor<?> constructor) -> constructor.getParameterCount()).reversed());
        for (Constructor<?> constructor : constructors) {
            Object[] arguments = argumentsFor(constructor.getParameterTypes(), windowId, component, size);
            if (arguments != null) {
                try {
                    constructor.setAccessible(true);
                    return constructor.newInstance(arguments);
                } catch (Throwable refused) {
                    // try the next constructor
                }
            }
        }
        return null;
    }

    /** The packet's title as a legacy-formatted string, or {@code null} when it cannot be read. */
    @Nullable
    public String titleOf(@Nonnull Object packet) {
        if (fromComponentMethod == null || !matches(packet)) {
            return null;
        }
        try {
            Object component = titleField.get(packet);
            return component == null ? null : (String) fromComponentMethod.invoke(null, component);
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    private Object component(@Nonnull String text) {
        try {
            Object[] components = (Object[]) fromStringMethod.invoke(null, text);
            return components == null || components.length == 0 ? null : components[0];
        } catch (Throwable t) {
            return null;
        }
    }

    /** One title component, the window id then the size as ints, and a chest as the window type; {@code null} if anything else is asked for. */
    @Nullable
    private Object[] argumentsFor(Class<?>[] parameterTypes, int windowId, Object component, int size) {
        Object[] arguments = new Object[parameterTypes.length];
        int[] ints = { windowId, size };
        int intsUsed = 0;
        boolean hasTitle = false;
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> type = parameterTypes[i];
            if (type == int.class && intsUsed < ints.length) {
                arguments[i] = ints[intsUsed++];
            } else if (!hasTitle && type.isAssignableFrom(componentClass)) {
                arguments[i] = component;
                hasTitle = true;
            } else if (type == String.class) {
                arguments[i] = CHEST_WINDOW_TYPE;
            } else if (type.isPrimitive()) {
                return null;
            } else {
                arguments[i] = chestMenuType(type, size);
                if (arguments[i] == null) {
                    return null;
                }
            }
        }
        return hasTitle && intsUsed > 0 ? arguments : null;
    }

    /**
     * The menu type of a chest with {@code size} slots, from the menu-type class's own constants.
     *
     * @implNote Found by name where the server is Mojang-mapped ({@code GENERIC_9x3}); under Spigot's obfuscated
     *           names the six chest types are the class's first six constants, in order of rows.
     */
    @Nullable
    private static Object chestMenuType(Class<?> menuTypeClass, int size) {
        int rows = Math.max(1, Math.min(6, size / 9));
        List<Field> constants = new ArrayList<>();
        for (Field field : menuTypeClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && menuTypeClass.isAssignableFrom(field.getType())) {
                constants.add(field);
            }
        }
        Field chosen = null;
        for (Field constant : constants) {
            if (constant.getName().equalsIgnoreCase("GENERIC_9x" + rows)) {
                chosen = constant;
            }
        }
        if (chosen == null && constants.size() >= rows) {
            chosen = constants.get(rows - 1);
        }
        if (chosen == null) {
            return null;
        }
        try {
            chosen.setAccessible(true);
            return chosen.get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    private static Class<?> resolvePacketClass() {
        String legacyPkg = legacyNmsPackage();
        for (String name : PACKET_CLASS_NAMES) {
            Class<?> c = tryClass(name);
            if (c == null && legacyPkg != null && name.indexOf('.') < 0) {
                c = tryClass(legacyPkg + '.' + name);
            }
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    @Nullable
    private static Class<?> resolveComponentClass() {
        for (String name : new String[] { "net.minecraft.network.chat.Component", "net.minecraft.network.chat.IChatBaseComponent" }) {
            Class<?> c = tryClass(name);
            if (c != null) {
                return c;
            }
        }
        String legacy = legacyNmsPackage();
        return legacy != null ? tryClass(legacy + ".IChatBaseComponent") : null;
    }

    /**
     * {@code CraftChatMessage.fromString(String)} converts a legacy-formatted plain string into this
     * server's chat-component array - the same stable CraftBukkit utility used to build chat/book/sign
     * components across the whole 1.8-to-current range, versioned pre-1.20.5 and unversioned after (see
     * {@code PacketReflect#resolveCraftItemMethod}, which resolves {@code CraftItemStack} the same way).
     */
    @Nullable
    private static Method resolveFromStringMethod(@Nonnull Class<?> componentClass) {
        try {
            String pkg = Bukkit.getServer().getClass().getPackage().getName();
            for (String cls : new String[] { pkg + ".util.CraftChatMessage", "org.bukkit.craftbukkit.util.CraftChatMessage" }) {
                Class<?> c = tryClass(cls);
                if (c == null) {
                    continue;
                }
                for (Method m : c.getMethods()) {
                    if (m.getName().equals("fromString") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == String.class
                            && m.getReturnType().isArray()
                            && componentClass.isAssignableFrom(m.getReturnType().getComponentType())) {
                        return m;
                    }
                }
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** {@code CraftChatMessage.fromComponent(component)}, the way back from {@link #resolveFromStringMethod}. */
    @Nullable
    private static Method resolveFromComponentMethod(@Nonnull Class<?> componentClass) {
        try {
            String pkg = Bukkit.getServer().getClass().getPackage().getName();
            for (String cls : new String[] { pkg + ".util.CraftChatMessage", "org.bukkit.craftbukkit.util.CraftChatMessage" }) {
                Class<?> c = tryClass(cls);
                if (c == null) {
                    continue;
                }
                for (Method m : c.getMethods()) {
                    if (m.getName().equals("fromComponent") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0].isAssignableFrom(componentClass)
                            && m.getReturnType() == String.class) {
                        return m;
                    }
                }
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    private static String legacyNmsPackage() {
        // Spigot: org.bukkit.craftbukkit.v1_8_R3 -> net.minecraft.server.v1_8_R3
        try {
            String cb = Bukkit.getServer().getClass().getPackage().getName();
            int i = cb.lastIndexOf('.');
            if (i < 0) {
                return null;
            }
            String ver = cb.substring(i + 1);
            return ver.startsWith("v") ? "net.minecraft.server." + ver : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    private static Class<?> tryClass(String n) {
        try {
            return Class.forName(n);
        } catch (Throwable t) {
            return null;
        }
    }
}
