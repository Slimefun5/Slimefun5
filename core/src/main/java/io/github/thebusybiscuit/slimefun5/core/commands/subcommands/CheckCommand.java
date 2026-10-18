package io.github.thebusybiscuit.slimefun5.core.commands.subcommands;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import io.github.thebusybiscuit.slimefun5.core.checks.LiveChecks;
import io.github.thebusybiscuit.slimefun5.core.checks.Stage;
import io.github.thebusybiscuit.slimefun5.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun5.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

class CheckCommand extends SubCommand {

    private static final String RUNNABLE_STAGES = "low, medium, high, max";

    @ParametersAreNonnullByDefault
    CheckCommand(Slimefun plugin, SlimefunCommand cmd) {
        super(plugin, cmd, "check", false);
    }

    @Override
    protected @Nonnull String getDescription() {
        return "commands.check.description";
    }

    @Override
    public void onExecute(CommandSender sender, @Nonnull String[] args) {
        if (!sender.hasPermission(LiveChecks.PERMISSION) && !(sender instanceof ConsoleCommandSender)) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        if (args.length < 2) {
            printLatestReport(sender);
            return;
        }

        runStage(sender, args[1]);
    }

    private void runStage(CommandSender sender, String argument) {
        Optional<Stage> stage = Stage.parse(argument).filter(parsed -> parsed != Stage.OFF);
        if (!stage.isPresent()) {
            Slimefun.getLocalization().sendMessage(sender, "commands.check.unknown-stage", true, msg -> msg.replace("%stage%", argument).replace("%stages%", RUNNABLE_STAGES));
            return;
        }

        String stageName = stage.get().name().toLowerCase(Locale.ROOT);
        if (LiveChecks.start(stage.get(), sender)) {
            Slimefun.getLocalization().sendMessage(sender, "commands.check.started", true, msg -> msg.replace("%stage%", stageName));
        }
    }

    private void printLatestReport(CommandSender sender) {
        List<String> lines = LiveChecks.reportOf(plugin).latestLines();
        if (lines.isEmpty()) {
            Slimefun.getLocalization().sendMessage(sender, "commands.check.no-report", true);
            return;
        }
        lines.forEach(sender::sendMessage);
    }
}
