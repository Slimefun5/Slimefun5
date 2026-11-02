package io.github.thebusybiscuit.slimefun5.core.checks.packets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun5.utils.compatibility.packet.PacketItemDescriptor;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.packet.PacketReflect;

/**
 * One kind of item packet of the running server, built with its own constructor so a check can feed stacks
 * through core's packet translation and read back what a client would be shown. Never sent to a network.
 */
public final class FedItemPacket {

    /** Any window id: a fed packet is never sent, so it needs no window of that id. */
    private static final int FED_WINDOW_ID = 1;

    private enum Shape {
        SLOT, WINDOW_CONTENTS, SINGLE_ITEM
    }

    private final String name;
    private final PacketItemDescriptor descriptor;
    private final Shape shape;

    private FedItemPacket(@Nonnull String name, @Nonnull PacketItemDescriptor descriptor, @Nonnull Shape shape) {
        this.name = name;
        this.descriptor = descriptor;
        this.shape = shape;
    }

    @Nonnull
    static FedItemPacket setSlot(@Nonnull PacketItemDescriptor descriptor) {
        return new FedItemPacket("set-slot", descriptor, Shape.SLOT);
    }

    @Nonnull
    static FedItemPacket windowContents(@Nonnull PacketItemDescriptor descriptor) {
        return new FedItemPacket("window-items", descriptor, Shape.WINDOW_CONTENTS);
    }

    @Nonnull
    static FedItemPacket singleItem(@Nonnull String name, @Nonnull PacketItemDescriptor descriptor) {
        return new FedItemPacket(name, descriptor, Shape.SINGLE_ITEM);
    }

    @Nonnull
    public String name() {
        return name;
    }

    /**
     * Whether a stack has an item form on this server at all; one that has none (1.8's block-only materials such
     * as {@code CAULDRON}) reaches a client as an empty slot through every packet.
     */
    public static boolean hasItemForm(@Nonnull ItemStack item) {
        return PacketReflect.asNms(item) != null;
    }

    /** Whether this packet holds several stacks; the others carry exactly one. */
    public boolean carriesSeveral() {
        return shape == Shape.WINDOW_CONTENTS;
    }

    /**
     * @param items one stack, or several where {@link #carriesSeveral()}
     * @throws IllegalStateException when this server's packet cannot be built with these stacks
     */
    @Nonnull
    public Object carrying(@Nonnull List<ItemStack> items) {
        if (shape != Shape.WINDOW_CONTENTS) {
            Object nmsItem = built(PacketReflect.asNms(items.get(0)), "item");
            Object packet = shape == Shape.SLOT ? descriptor.newSlotPacket(FED_WINDOW_ID, 0, nmsItem) : descriptor.newSingleItemPacket(nmsItem);
            return built(packet, name);
        }

        List<Object> nmsItems = new ArrayList<>();
        for (ItemStack item : items) {
            nmsItems.add(PacketReflect.asNms(item));
        }
        return built(descriptor.newListPacket(FED_WINDOW_ID, nmsItems, PacketReflect.asNms(new ItemStack(Material.AIR))), name);
    }

    /**
     * The stacks a client would be shown, in order; an empty slot reads as air.
     *
     * @throws IllegalStateException when the packet's stacks cannot be read
     */
    @Nonnull
    public List<ItemStack> itemsOf(@Nonnull Object packet) {
        if (shape != Shape.WINDOW_CONTENTS) {
            Object nmsItem = built(descriptor.itemOf(packet), name + " item");
            return Collections.singletonList(built(PacketReflect.asBukkit(nmsItem), "item"));
        }

        List<Object> nmsItems = built(descriptor.itemsOf(packet), name + " items");
        List<ItemStack> shown = new ArrayList<>();
        for (Object nmsItem : nmsItems) {
            shown.add(asBukkitOrAir(nmsItem));
        }
        return shown;
    }

    @Nonnull
    private static ItemStack asBukkitOrAir(@Nullable Object nmsItem) {
        ItemStack bukkit = nmsItem == null ? null : PacketReflect.asBukkit(nmsItem);
        return bukkit == null ? new ItemStack(Material.AIR) : bukkit;
    }

    @Nonnull
    private static <T> T built(@Nullable T value, @Nonnull String what) {
        if (value == null) {
            throw new IllegalStateException("This server's " + what + " could not be built or read");
        }
        return value;
    }
}
