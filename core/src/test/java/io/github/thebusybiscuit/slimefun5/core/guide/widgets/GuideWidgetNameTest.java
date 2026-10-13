package io.github.thebusybiscuit.slimefun5.core.guide.widgets;

import com.cryptomorin.xseries.XMaterial;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class GuideWidgetNameTest {

    private static ServerMock server;

    @BeforeAll
    public static void load() {
        server = MockBukkit.mock();
    }

    @AfterAll
    public static void unload() {
        MockBukkit.unmock();
    }

    private static GuideWidget widget() {
        return new GuideWidget("advancements", "&9Advancements", XMaterial.CHEST, 10, (player, profile) -> {});
    }

    @Test
    void withoutAResolverEveryViewerGetsTheDefaultName() {
        Player p = server.addPlayer();
        Assertions.assertEquals("&9Advancements", widget().getName(p));
    }

    @Test
    void aResolverNamesTheWidgetForItsViewer() {
        Player p = server.addPlayer();
        GuideWidget widget = widget().setNameResolver(viewer -> "&9Fortschritte");

        Assertions.assertEquals("&9Fortschritte", widget.getName(p));
    }

    @Test
    void theResolverSeesTheViewerItResolvesFor() {
        Player p = server.addPlayer();
        GuideWidget widget = widget().setNameResolver(viewer -> viewer.getName());

        Assertions.assertEquals(p.getName(), widget.getName(p));
    }

    @Test
    void noViewerMeansTheDefaultNameEvenWithAResolver() {
        GuideWidget widget = widget().setNameResolver(viewer -> "&9Fortschritte");

        Assertions.assertEquals("&9Advancements", widget.getName(null));
    }

    /** An addon resolver reaching a key it never shipped would otherwise render a nameless tile. */
    @Test
    void aResolverYieldingNothingFallsBackToTheDefaultName() {
        Player p = server.addPlayer();

        Assertions.assertEquals("&9Advancements", widget().setNameResolver(viewer -> null).getName(p));
        Assertions.assertEquals("&9Advancements", widget().setNameResolver(viewer -> "").getName(p));
    }

    @Test
    void theResolverCanBeChainedOntoTheConstructor() {
        GuideWidget widget = widget();

        Assertions.assertSame(widget, widget.setNameResolver(viewer -> "&9Fortschritte"));
    }
}
