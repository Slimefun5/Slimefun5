package io.github.thebusybiscuit.slimefun5.core.commands.subcommands;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import io.github.thebusybiscuit.slimefun5.core.checks.LiveChecks;

class CheckCommandTest {

    private ServerMock server;
    private List<String> sentMessages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        sentMessages = new ArrayList<>();
        LiveChecks.useMessenger((recipient, key, placeholders) -> sentMessages.add(key + "|" + placeholders.apply("%shown%/%total%/%report%")));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static List<String> lines(int count) {
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            lines.add("[check:a] subject" + index + ": broke");
        }
        return lines;
    }

    private static int countMessages(PlayerMock player) {
        int count = 0;
        while (player.nextMessage() != null) {
            count++;
        }
        return count;
    }

    @Test
    void capsALongReportAndPointsAtTheFile() {
        PlayerMock player = server.addPlayer();

        CheckCommand.printCapped(player, lines(120), "plugins/Slimefun/checks/report-x.txt");

        Assertions.assertEquals(50, countMessages(player));
        Assertions.assertEquals(1, sentMessages.size());
        Assertions.assertEquals("commands.check.truncated|50/120/plugins/Slimefun/checks/report-x.txt", sentMessages.get(0));
    }

    @Test
    void printsAShortReportWholeWithoutTheTruncatedLine() {
        PlayerMock player = server.addPlayer();

        CheckCommand.printCapped(player, lines(50), "x");

        Assertions.assertEquals(50, countMessages(player));
        Assertions.assertTrue(sentMessages.isEmpty());
    }
}
