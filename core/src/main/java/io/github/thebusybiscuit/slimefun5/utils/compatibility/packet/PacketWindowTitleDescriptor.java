package io.github.thebusybiscuit.slimefun5.utils.compatibility.packet;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

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

    /** Candidate simple/class names across the version range (Spigot obf + Mojang-mapped Paper). */
    private static final String[] PACKET_CLASS_NAMES = {
        "net.minecraft.network.protocol.game.ClientboundOpenScreenPacket",
        "PacketPlayOutOpenWindow",
    };

    private final Class<?> packetClass;
    private final Field titleField;
    private final Method fromStringMethod;

    private PacketWindowTitleDescriptor(Class<?> packetClass, Field titleField, Method fromStringMethod) {
        this.packetClass = packetClass;
        this.titleField = titleField;
        this.fromStringMethod = fromStringMethod;
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

        Field titleField = PacketReflect.firstFieldOfType(packetClass, componentClass);
        if (titleField == null) {
            return null;
        }

        Method fromString = resolveFromStringMethod(componentClass);
        if (fromString == null) {
            return null;
        }

        return new PacketWindowTitleDescriptor(packetClass, titleField, fromString);
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
            Object[] components = (Object[]) fromStringMethod.invoke(null, text);
            if (components == null || components.length == 0 || components[0] == null) {
                return false;
            }
            titleField.set(packet, components[0]);
            return true;
        } catch (Throwable t) {
            return false;
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
        Class<?> c = tryClass("net.minecraft.network.chat.Component");
        if (c != null) {
            return c;
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
