package io.github.thebusybiscuit.slimefun5.utils.compatibility.packet;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import javax.annotation.Nullable;

/**
 * Describes an item-bearing packet and knows how to rewrite the item(s) it carries. Resolved reflectively
 * per server version; families whose class does not resolve are simply not produced.
 */
public final class PacketItemDescriptor {

    /**
     * One row per packet, holding that packet's name under each mapping; the first that resolves wins.
     *
     * @implNote Three spellings: Mojang names (Paper 1.20.5+), Spigot names in the 1.17+ Mojang
     *           package (Paper 1.17 to 1.20.4 and plain Spigot at any later version), and bare Spigot
     *           names, retried under the pre-1.17 {@code net.minecraft.server.<ver>} package. The cursor and
     *           player-inventory packets only exist from 1.21.2, where only the Mojang name is known.
     */
    private static final String[][] OUTBOUND_FAMILIES = {
        {
            "net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket",
            "net.minecraft.network.protocol.game.PacketPlayOutSetSlot",
            "PacketPlayOutSetSlot",
        },
        {
            "net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket",
            "net.minecraft.network.protocol.game.PacketPlayOutWindowItems",
            "PacketPlayOutWindowItems",
        },
        {
            "net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket",
        },
        {
            "net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket",
        },
    };

    /**
     * Packets in which a client sends back stacks it was shown.
     *
     * @implNote From 1.21.5 the click packet carries {@code HashedStack}s instead of stacks, so it still
     *           resolves there but has nothing to rewrite.
     */
    private static final String[][] INBOUND_FAMILIES = {
        {
            "net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket",
            "net.minecraft.network.protocol.game.PacketPlayInSetCreativeSlot",
            "PacketPlayInSetCreativeSlot",
        },
        {
            "net.minecraft.network.protocol.game.ServerboundContainerClickPacket",
            "net.minecraft.network.protocol.game.PacketPlayInWindowClick",
            "PacketPlayInWindowClick",
        },
    };

    private final Class<?> packetClass;
    private final Class<?> nmsItemClass;

    private PacketItemDescriptor(Class<?> packetClass, Class<?> nmsItemClass) {
        this.packetClass = packetClass;
        this.nmsItemClass = nmsItemClass;
    }

    static PacketItemDescriptor of(Class<?> packetClass, Class<?> nmsItemClass) {
        return new PacketItemDescriptor(packetClass, nmsItemClass);
    }

    public static List<PacketItemDescriptor> resolveAll() {
        return resolve(OUTBOUND_FAMILIES);
    }

    public static List<PacketItemDescriptor> resolveInbound() {
        return resolve(INBOUND_FAMILIES);
    }

    private static List<PacketItemDescriptor> resolve(String[][] families) {
        Class<?> nmsItem = resolveNmsItemClass();
        List<PacketItemDescriptor> out = new ArrayList<>();
        if (nmsItem == null) {
            return out;
        }
        String legacyPkg = legacyNmsPackage();
        for (String[] family : families) {
            for (String name : family) {
                Class<?> c = tryClass(name);
                if (c == null && legacyPkg != null && name.indexOf('.') < 0) {
                    c = tryClass(legacyPkg + '.' + name);
                }
                if (c != null) {
                    out.add(new PacketItemDescriptor(c, nmsItem));
                    break;
                }
            }
        }
        return out;
    }

    public boolean matches(Object packet) {
        return packet != null && packetClass.isInstance(packet);
    }

    /**
     * Rewrites every NMS item the packet carries via {@code itemRewriter}, returning either the same packet
     * changed in place or, for a record, a rebuilt copy. Never throws; on any failure the original packet is
     * returned.
     */
    public Object rewrite(Object packet, UnaryOperator<Object> itemRewriter) {
        RecordShape shape = RecordShape.of(packetClass);
        if (shape != null) {
            return rebuildRecord(shape, packet, itemRewriter);
        }
        rewriteFields(packet, itemRewriter);
        return packet;
    }

    /**
     * @implNote A record's fields cannot be set by reflection even after {@code setAccessible}, so the
     *           components are read through their accessors and passed to the canonical constructor. Lists
     *           are copied rather than changed in place, so a list the server still holds never receives a
     *           viewer's translation.
     */
    private Object rebuildRecord(RecordShape shape, Object packet, UnaryOperator<Object> itemRewriter) {
        try {
            Object[] components = shape.components(packet);
            boolean changed = false;
            for (int i = 0; i < components.length; i++) {
                Object rewritten = rewriteComponent(components[i], itemRewriter);
                if (rewritten != components[i]) {
                    components[i] = rewritten;
                    changed = true;
                }
            }
            return changed ? shape.construct(components) : packet;
        } catch (Throwable failure) {
            return packet;
        }
    }

    private Object rewriteComponent(Object value, UnaryOperator<Object> itemRewriter) {
        if (nmsItemClass.isInstance(value)) {
            return rewriteItem(value, itemRewriter);
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<Object> copy = null;
            for (int i = 0; i < list.size(); i++) {
                Object element = list.get(i);
                Object rewritten = nmsItemClass.isInstance(element) ? rewriteItem(element, itemRewriter) : element;
                if (rewritten != element && copy == null) {
                    copy = new ArrayList<>(list);
                }
                if (copy != null) {
                    copy.set(i, rewritten);
                }
            }
            return copy != null ? copy : value;
        }
        return value;
    }

    private static Object rewriteItem(Object item, UnaryOperator<Object> itemRewriter) {
        Object rewritten = itemRewriter.apply(item);
        return rewritten != null ? rewritten : item;
    }

    private void rewriteFields(Object packet, UnaryOperator<Object> itemRewriter) {
        try {
            // List payload (WindowItems / SetContent): mutate elements in place.
            Field listField = PacketReflect.firstFieldOfType(packet, List.class);
            if (listField != null) {
                Object listObj = listField.get(packet);
                if (listObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> list = (List<Object>) listObj;
                    for (int i = 0; i < list.size(); i++) {
                        Object el = list.get(i);
                        if (nmsItemClass.isInstance(el)) {
                            Object rewritten = itemRewriter.apply(el);
                            if (rewritten != null && rewritten != el) {
                                list.set(i, rewritten);
                            }
                        }
                    }
                }
            }
            // Array payload (early-legacy PacketPlayOutWindowItems carries its contents as an
            // ItemStack[] rather than a List): rewrite each element in place. General enough to cover any
            // other array-based container packet.
            Field arrayField = PacketReflect.firstFieldOfArrayType(packet, nmsItemClass);
            if (arrayField != null) {
                Object arrayObj = arrayField.get(packet);
                if (arrayObj != null && arrayObj.getClass().isArray()) {
                    int length = Array.getLength(arrayObj);
                    for (int i = 0; i < length; i++) {
                        Object el = Array.get(arrayObj, i);
                        if (nmsItemClass.isInstance(el)) {
                            Object rewritten = itemRewriter.apply(el);
                            if (rewritten != null && rewritten != el) {
                                Array.set(arrayObj, i, rewritten);
                            }
                        }
                    }
                }
            }
            // Map payload (the 1.17 to 1.21.4 click packet's changed slots, freshly decoded per packet).
            Field mapField = PacketReflect.firstFieldOfType(packet, Map.class);
            if (mapField != null) {
                Object mapObj = mapField.get(packet);
                if (mapObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<Object, Object> map = (Map<Object, Object>) mapObj;
                    for (Object slot : new ArrayList<>(map.keySet())) {
                        Object el = map.get(slot);
                        if (nmsItemClass.isInstance(el)) {
                            Object rewritten = itemRewriter.apply(el);
                            if (rewritten != null && rewritten != el) {
                                map.put(slot, rewritten);
                            }
                        }
                    }
                }
            }
            // Single-item payload (SetSlot / ContainerSetSlot, or the carried/cursor item
            // alongside the list on ClientboundContainerSetContentPacket): set the field if writable.
            Field itemField = PacketReflect.firstFieldOfType(packet, nmsItemClass);
            if (itemField != null) {
                Object current = itemField.get(packet);
                if (nmsItemClass.isInstance(current)) {
                    Object rewritten = itemRewriter.apply(current);
                    if (rewritten != null && rewritten != current) {
                        trySet(itemField, packet, rewritten);
                    }
                }
            }
        } catch (Throwable ignored) {
            // fall through to returning the original packet (fallback)
        }
    }

    private static boolean trySet(Field f, Object owner, Object value) {
        try {
            f.setAccessible(true);
            f.set(owner, value);
            return true;
        } catch (Throwable t) {
            return false; // final/immutable field on this version - leave packet unchanged
        }
    }

    @Nullable
    private static Class<?> resolveNmsItemClass() {
        Class<?> c = tryClass("net.minecraft.world.item.ItemStack");
        if (c != null) {
            return c;
        }
        String legacy = legacyNmsPackage();
        return legacy != null ? tryClass(legacy + ".ItemStack") : null;
    }

    @Nullable
    private static String legacyNmsPackage() {
        // Spigot: org.bukkit.craftbukkit.v1_8_R3 -> net.minecraft.server.v1_8_R3
        try {
            String cb = org.bukkit.Bukkit.getServer().getClass().getPackage().getName();
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
