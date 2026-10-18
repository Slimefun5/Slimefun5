package io.github.thebusybiscuit.slimefun5.core.checks;

import java.util.function.UnaryOperator;

import javax.annotation.Nonnull;

import org.bukkit.command.CommandSender;

@FunctionalInterface
public interface CheckMessenger {

    void send(@Nonnull CommandSender recipient, @Nonnull String key, @Nonnull UnaryOperator<String> placeholders);
}
