package io.github.thebusybiscuit.slimefun5.core.checks.packets;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.Sandbox;
import io.github.thebusybiscuit.slimefun5.core.services.localization.PacketTranslationService;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.packet.PacketItemDescriptor;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.packet.PacketWindowTitleDescriptor;

/**
 * @implNote MockBukkit has no NMS, so the packets here are stand-ins shaped like the real ones: the 1.8.8
 *           open-window packet (window id, type string, title component, size) beside a decoding constructor,
 *           and the 1.17+ set-slot packet (window id, state id, slot, item).
 */
class FedPacketsTest {

    public static final class Chat {

        final String text;

        Chat(String text) {
            this.text = text;
        }

        public static Chat[] fromString(String text) {
            return new Chat[] { new Chat(text) };
        }

        public static String fromComponent(Chat component) {
            return component.text;
        }
    }

    public static final class OpenWindow {

        final int windowId;
        final String type;
        final Chat title;
        final int size;

        public OpenWindow(int windowId, String type, Chat title, int size) {
            this.windowId = windowId;
            this.type = type;
            this.title = title;
            this.size = size;
        }

        public OpenWindow(StringBuilder decoded) {
            this(0, decoded.toString(), null, 0);
        }
    }

    public static final class Item {

        final String name;

        Item(String name) {
            this.name = name;
        }
    }

    public static final class SetSlot {

        final int windowId;
        final int stateId;
        final int slot;
        final Item item;

        public SetSlot(int windowId, int stateId, int slot, Item item) {
            this.windowId = windowId;
            this.stateId = stateId;
            this.slot = slot;
            this.item = item;
        }
    }

    private ServerMock server;
    private Sandbox sandbox;
    private PacketWindowTitleDescriptor openWindow;
    private FedPackets fed;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        server = MockBukkit.mock();
        MockBukkit.load(Slimefun.class);
        sandbox = Sandbox.open(true);
        openWindow = PacketWindowTitleDescriptor.of(OpenWindow.class, Chat.class,
            Chat.class.getMethod("fromString", String.class), Chat.class.getMethod("fromComponent", Chat.class));
        fed = new FedPackets(openWindow, PacketItemDescriptor.of(SetSlot.class, Item.class));
    }

    @AfterEach
    void tearDown() {
        Sandbox.closeOpen();
        MockBukkit.unmock();
    }

    @Test
    void sandboxPlayerTakesPacketPath() {
        PacketTranslationService packets = PacketTranslationService.withoutChannels(openWindow);
        Player sandboxPlayer = sandbox.join("fed_viewer", "en").player();
        Player realPlayer = server.addPlayer();

        Assertions.assertTrue(packets.canRetitleWindowFor(sandboxPlayer));
        Assertions.assertFalse(packets.canRetitleWindowFor(realPlayer), "a real player without the handler on its channel must keep setTitle");
    }

    @Test
    void pendingTitleIsAppliedToFedPacket() {
        PacketTranslationService packets = PacketTranslationService.withoutChannels(openWindow);
        Player viewer = sandbox.join("fed_viewer", "en").player();
        packets.notifyWindowTitle(viewer, "Resolved Title");

        Assertions.assertTrue(packets.hasPendingWindowTitle(viewer));
        Object packet = fed.openWindow(7, "Stored Title", 27);
        Object sent = packets.translateOutbound(viewer, packet);

        Assertions.assertEquals("Resolved Title", fed.titleOf(sent));
        Assertions.assertFalse(packets.hasPendingWindowTitle(viewer), "the pending title is taken by the packet it retitled");
        Assertions.assertEquals("Stored Title", fed.titleOf(packets.translateOutbound(viewer, fed.openWindow(8, "Stored Title", 27))));
    }

    @Test
    void openWindowIsBuiltWithTheServersOwnConstructor() {
        OpenWindow packet = (OpenWindow) fed.openWindow(7, "A Title", 27);

        Assertions.assertEquals(7, packet.windowId);
        Assertions.assertEquals(27, packet.size);
        Assertions.assertEquals("A Title", packet.title.text);
        Assertions.assertNotNull(packet.type);
    }

    @Test
    void setSlotCarriesTheWindowSlotAndItem() {
        PacketItemDescriptor setSlot = PacketItemDescriptor.of(SetSlot.class, Item.class);
        Item item = new Item("a");

        SetSlot packet = (SetSlot) setSlot.newSlotPacket(3, 12, item);

        Assertions.assertEquals(3, packet.windowId);
        Assertions.assertEquals(12, packet.slot);
        Assertions.assertSame(item, setSlot.itemOf(packet));
    }

    @Test
    void noPacketsAreFedWhereTheServerHasNone() {
        Assertions.assertFalse(FedPackets.forRunningVersion().isPresent());
    }
}
