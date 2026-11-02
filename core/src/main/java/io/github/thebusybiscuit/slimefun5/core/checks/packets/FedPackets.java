package io.github.thebusybiscuit.slimefun5.core.checks.packets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;

import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun5.utils.compatibility.packet.PacketItemDescriptor;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.packet.PacketReflect;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.packet.PacketWindowTitleDescriptor;

/**
 * Real open-window and set-slot packets of the running server, built with its own constructors so a check can
 * feed them through core's packet translation and read back what a client would be shown. Nothing built here is
 * ever sent to a network.
 */
public final class FedPackets {

    private final PacketWindowTitleDescriptor openWindow;
    private final PacketItemDescriptor setSlot;
    private final List<FedItemPacket> itemPackets;

    FedPackets(@Nonnull PacketWindowTitleDescriptor openWindow, @Nonnull PacketItemDescriptor setSlot) {
        this(openWindow, setSlot, null, null, null);
    }

    FedPackets(@Nonnull PacketWindowTitleDescriptor openWindow, @Nonnull PacketItemDescriptor setSlot, PacketItemDescriptor windowItems,
            PacketItemDescriptor cursorItem, PacketItemDescriptor playerInventory) {
        this.openWindow = openWindow;
        this.setSlot = setSlot;
        List<FedItemPacket> packets = new ArrayList<>();
        packets.add(FedItemPacket.setSlot(setSlot));
        if (windowItems != null) {
            packets.add(FedItemPacket.windowContents(windowItems));
        }
        if (cursorItem != null) {
            packets.add(FedItemPacket.singleItem("cursor-item", cursorItem));
        }
        if (playerInventory != null) {
            packets.add(FedItemPacket.singleItem("player-inventory", playerInventory));
        }
        this.itemPackets = Collections.unmodifiableList(packets);
    }

    /** The running server's packets, or empty when either packet does not resolve on this version. */
    @Nonnull
    public static Optional<FedPackets> forRunningVersion() {
        PacketWindowTitleDescriptor openWindow = PacketWindowTitleDescriptor.resolve();
        PacketItemDescriptor setSlot = PacketItemDescriptor.resolveSetSlot();
        if (openWindow == null || setSlot == null) {
            return Optional.empty();
        }
        return Optional.of(new FedPackets(openWindow, setSlot, PacketItemDescriptor.resolveWindowItems(), PacketItemDescriptor.resolveCursorItem(),
            PacketItemDescriptor.resolvePlayerInventory()));
    }

    /** Every item packet this server has, set-slot first; the cursor and player-inventory packets exist from 1.21.2. */
    @Nonnull
    public List<FedItemPacket> itemPackets() {
        return itemPackets;
    }

    /** @throws IllegalStateException when this server's open-window packet cannot be built */
    @Nonnull
    public Object openWindow(int windowId, @Nonnull String title, int size) {
        return built(openWindow.newPacket(windowId, title, size), "open-window");
    }

    /** @throws IllegalStateException when the packet's title cannot be read */
    @Nonnull
    public String titleOf(@Nonnull Object openWindowPacket) {
        return built(openWindow.titleOf(openWindowPacket), "open-window title");
    }

    /** @throws IllegalStateException when this server's set-slot packet cannot be built */
    @Nonnull
    public Object setSlot(int windowId, int slot, @Nonnull ItemStack item) {
        Object nmsItem = built(PacketReflect.asNms(item), "item");
        return built(setSlot.newSlotPacket(windowId, slot, nmsItem), "set-slot");
    }

    /** @throws IllegalStateException when the packet's item cannot be read */
    @Nonnull
    public ItemStack itemOf(@Nonnull Object setSlotPacket) {
        Object nmsItem = built(setSlot.itemOf(setSlotPacket), "set-slot item");
        return built(PacketReflect.asBukkit(nmsItem), "item");
    }

    @Nonnull
    private static <T> T built(T value, String what) {
        if (value == null) {
            throw new IllegalStateException("This server's " + what + " could not be built or read");
        }
        return value;
    }
}
