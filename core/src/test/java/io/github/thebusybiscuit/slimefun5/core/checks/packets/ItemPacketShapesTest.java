package io.github.thebusybiscuit.slimefun5.core.checks.packets;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.github.thebusybiscuit.slimefun5.utils.compatibility.packet.PacketItemDescriptor;

/**
 * @implNote Stand-ins shaped like each server's real item packets, since MockBukkit has no NMS: 1.8's window
 *           items (a list in, an array kept), 1.11 to 1.20.4's (Mojang's fixed-size list with a static factory and a
 *           carried stack), 1.20.5+'s record, and 1.21.2's cursor and player-inventory records.
 */
class ItemPacketShapesTest {

    public static final class Item {

        final String name;

        Item(String name) {
            this.name = name;
        }
    }

    public static final class LegacyWindowItems {

        final int windowId;
        final Item[] items;

        public LegacyWindowItems(int windowId, List<Item> items) {
            this.windowId = windowId;
            this.items = items.toArray(new Item[0]);
        }
    }

    public static final class FixedList<E> extends AbstractList<E> {

        private final Object[] elements;

        private FixedList(int size, E fill) {
            elements = new Object[size];
            Arrays.fill(elements, fill);
        }

        public static <E> FixedList<E> withSize(int size, E fill) {
            return new FixedList<>(size, fill);
        }

        @Override
        @SuppressWarnings("unchecked")
        public E get(int index) {
            return (E) elements[index];
        }

        @Override
        public E set(int index, E element) {
            E previous = get(index);
            elements[index] = element;
            return previous;
        }

        @Override
        public int size() {
            return elements.length;
        }
    }

    public static final class FixedListWindowItems {

        final int windowId;
        final int stateId;
        final FixedList<Item> items;
        final Item carried;

        public FixedListWindowItems(int windowId, int stateId, FixedList<Item> items, Item carried) {
            this.windowId = windowId;
            this.stateId = stateId;
            this.items = FixedList.withSize(items.size(), carried);
            for (int i = 0; i < items.size(); i++) {
                this.items.set(i, items.get(i));
            }
            this.carried = carried;
        }
    }

    public record RecordWindowItems(int windowId, int stateId, List<Item> items, Item carried) {
    }

    public record CursorItem(Item contents) {
    }

    public record PlayerInventory(int slot, Item contents) {
    }

    private static final List<Object> ITEMS = Arrays.asList(new Item("sword"), new Item("stone"), new Item("empty"));

    private static List<Object> roundTrip(Class<?> packetClass) {
        PacketItemDescriptor descriptor = PacketItemDescriptor.of(packetClass, Item.class);
        Object packet = descriptor.newListPacket(4, new ArrayList<>(ITEMS), new Item("carried"));
        Assertions.assertNotNull(packet, packetClass.getSimpleName() + " was not built");
        return descriptor.itemsOf(packet);
    }

    @Test
    void legacyWindowItemsKeepTheirOrderThroughTheArray() {
        Assertions.assertEquals(ITEMS, roundTrip(LegacyWindowItems.class));
    }

    @Test
    void fixedListWindowItemsAreBuiltThroughTheListsFactory() {
        Assertions.assertEquals(ITEMS, roundTrip(FixedListWindowItems.class));
    }

    @Test
    void recordWindowItemsAreBuiltAndRead() {
        Assertions.assertEquals(ITEMS, roundTrip(RecordWindowItems.class));
    }

    @Test
    void singleItemPacketsCarryTheirOneStack() {
        Item item = new Item("a");
        for (Class<?> packetClass : Arrays.asList(CursorItem.class, PlayerInventory.class)) {
            PacketItemDescriptor descriptor = PacketItemDescriptor.of(packetClass, Item.class);
            Object packet = descriptor.newSingleItemPacket(item);

            Assertions.assertNotNull(packet, packetClass.getSimpleName());
            Assertions.assertSame(item, descriptor.itemOf(packet));
        }
    }

    @Test
    void singleItemPacketIsNotMistakenForSetSlot() {
        Assertions.assertNull(PacketItemDescriptor.of(CursorItem.class, Item.class).newSlotPacket(1, 2, new Item("a")));
    }
}
