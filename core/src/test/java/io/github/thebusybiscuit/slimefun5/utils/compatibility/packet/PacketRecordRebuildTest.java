package io.github.thebusybiscuit.slimefun5.utils.compatibility.packet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PacketRecordRebuildTest {

    static final class Item {
        final String value;

        Item(String value) {
            this.value = value;
        }
    }

    record OneItem(Item contents) {}

    record SlotAndItem(int slot, Item contents) {}

    record ListAndCarried(int containerId, int stateId, List<Item> items, Item carried) {}

    /** Mirrors {@code ServerboundSetCreativeModeSlotPacket}: an int convenience constructor beside the canonical short one. */
    record ShortSlotItem(short slot, Item item) {
        ShortSlotItem(int slot, Item item) {
            this((short) slot, item);
        }
    }

    record Refusing(Item contents) {
        Refusing {
            if (contents.value.startsWith("translated")) {
                throw new IllegalArgumentException("refused");
            }
        }
    }

    /** Mirrors the 1.17 to 1.21.4 click packet: a plain class with a carried item and a map of changed slots. */
    static final class ClickClass {
        private final Item carried;
        private final Map<Integer, Item> changed;

        ClickClass(Item carried, Map<Integer, Item> changed) {
            this.carried = carried;
            this.changed = changed;
        }
    }

    private static final UnaryOperator<Object> TRANSLATE = item -> new Item("translated " + ((Item) item).value);

    private static Object rewrite(Object packet, UnaryOperator<Object> rewriter) {
        return PacketItemDescriptor.of(packet.getClass(), Item.class).rewrite(packet, rewriter);
    }

    @Test
    void aSingleItemRecordIsRebuilt() {
        OneItem out = (OneItem) rewrite(new OneItem(new Item("a")), TRANSLATE);

        Assertions.assertEquals("translated a", out.contents().value);
    }

    @Test
    void otherComponentsSurviveTheRebuild() {
        SlotAndItem out = (SlotAndItem) rewrite(new SlotAndItem(7, new Item("a")), TRANSLATE);

        Assertions.assertEquals(7, out.slot());
        Assertions.assertEquals("translated a", out.contents().value);
    }

    @Test
    void aListAndItsCarriedItemAreBothRewritten() {
        ListAndCarried out = (ListAndCarried) rewrite(
            new ListAndCarried(1, 2, Arrays.asList(new Item("a"), new Item("b")), new Item("c")), TRANSLATE);

        Assertions.assertEquals(1, out.containerId());
        Assertions.assertEquals(2, out.stateId());
        Assertions.assertEquals("translated a", out.items().get(0).value);
        Assertions.assertEquals("translated b", out.items().get(1).value);
        Assertions.assertEquals("translated c", out.carried().value);
    }

    @Test
    void theOriginalListIsNeverWrittenInto() {
        List<Item> serverList = new ArrayList<>(Arrays.asList(new Item("a")));
        rewrite(new ListAndCarried(0, 0, serverList, new Item("c")), TRANSLATE);

        Assertions.assertEquals("a", serverList.get(0).value);
    }

    @Test
    void theCanonicalConstructorIsChosenOverAnOverload() {
        ShortSlotItem out = (ShortSlotItem) rewrite(new ShortSlotItem((short) 36, new Item("a")), TRANSLATE);

        Assertions.assertEquals((short) 36, out.slot());
        Assertions.assertEquals("translated a", out.item().value);
    }

    @Test
    void anUnchangedRecordIsReturnedAsTheSameInstance() {
        OneItem packet = new OneItem(new Item("a"));

        Assertions.assertSame(packet, rewrite(packet, item -> item));
    }

    @Test
    void aConstructorThatThrowsLeavesTheOriginalPacket() {
        Refusing packet = new Refusing(new Item("a"));

        Assertions.assertSame(packet, rewrite(packet, TRANSLATE));
    }

    @Test
    void aClassPacketHasItsCarriedItemAndChangedSlotsRewritten() {
        Map<Integer, Item> changed = new HashMap<>();
        changed.put(3, new Item("slot"));
        ClickClass packet = new ClickClass(new Item("cursor"), changed);

        Assertions.assertSame(packet, rewrite(packet, TRANSLATE));
        Assertions.assertEquals("translated cursor", packet.carried.value);
        Assertions.assertEquals("translated slot", packet.changed.get(3).value);
    }
}
