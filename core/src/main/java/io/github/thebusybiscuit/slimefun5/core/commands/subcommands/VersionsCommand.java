package io.github.thebusybiscuit.slimefun5.core.commands.subcommands;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import io.github.thebusybiscuit.slimefun5.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun5.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun5.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.utils.NumberUtils;
import io.papermc.lib.PaperLib;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * This is our class for the /sf versions subcommand.
 * 
 * @author TheBusyBiscuit
 * @author Walshy
 *
 */
class VersionsCommand extends SubCommand {

    /**
     * This is the Java version we recommend to use.
     * Bump as necessary and adjust the warning.
     */
    private static final int RECOMMENDED_JAVA_VERSION = 16;

    @ParametersAreNonnullByDefault
    VersionsCommand(Slimefun plugin, SlimefunCommand cmd) {
        super(plugin, cmd, "versions", false);
    }

    @Override
    protected @Nonnull String getDescription() {
        return "commands.versions.description";
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (sender.hasPermission("slimefun.command.versions") || sender instanceof ConsoleCommandSender) {
            /*
             * After all these years... Spigot still displays as "CraftBukkit".
             * so we will just fix this inconsistency for them :)
             */
            String serverSoftware = PaperLib.isSpigot() && !PaperLib.isPaper() ? "Spigot" : Bukkit.getName();
            ComponentBuilder builder = new ComponentBuilder("");

            // @formatter:off
            builder.append(msg(sender, "commands.versions.header") + "\n")
                .color(ChatColor.GRAY)
                .append(serverSoftware)
                .color(ChatColor.GREEN)
                .append(" " + Bukkit.getVersion() + '\n')
                .color(ChatColor.DARK_GREEN);

            builder
                .append(msg(sender, "commands.versions.slimefun-label"))
                .color(ChatColor.GREEN)
                .append(Slimefun.getVersion())
                .color(ChatColor.DARK_GREEN);
            if (!Slimefun.getUpdater().isLatestVersion()) {
                builder
                    .append(" (").color(ChatColor.GRAY)
                    .append(msg(sender, "commands.versions.update-available")).color(ChatColor.RED).event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(
                        String.join("\n", msgList(sender, "commands.versions.update-hover"))
                    )))
                    .append(")").color(ChatColor.GRAY);
            }

            builder.append("\n").event((HoverEvent) null);
            // @formatter:on

            if (Slimefun.getMetricsService().getVersion() != null) {
                // @formatter:off
                builder.append(msg(sender, "commands.versions.metrics-module-label"))
                    .color(ChatColor.GREEN)
                    .append("#" + Slimefun.getMetricsService().getVersion() + '\n')
                    .color(ChatColor.DARK_GREEN);
                // @formatter:on
            }

            addJavaVersion(sender, builder);

            builder.append("\n").event((HoverEvent) null);
            addPluginVersions(sender, builder);

            send(sender, builder.create());
        } else {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
        }
    }

    /**
     * Sends the rich component message, guaranteeing output on every server version. The Spigot component
     * API ({@code CommandSender#spigot().sendMessage(BaseComponent[])}) is post-1.8 and, on some newer
     * servers (e.g. 26.2), is removed or throws - previously that failed silently, so {@code /sf versions}
     * printed nothing. If the rich send does not succeed, we fall back to a plain legacy-text send that
     * works everywhere (losing only hover/click, never the whole message).
     */
    private void send(@Nonnull CommandSender sender, @Nonnull BaseComponent[] components) {
        if (!trySpigotSend(sender, components)) {
            sender.sendMessage(TextComponent.toLegacyText(components));
        }
    }

    /**
     * @implNote {@code sendMessage} is resolved on the PUBLIC {@code CommandSender.Spigot} API type: the
     *           concrete Spigot instance is a non-public craftbukkit class, and invoking a Method declared
     *           there throws {@code IllegalAccessException} (the same trap ReflectionCompat documents).
     */
    private boolean trySpigotSend(@Nonnull CommandSender sender, @Nonnull BaseComponent[] components) {
        try {
            Object spigot = sender.getClass().getMethod("spigot").invoke(sender);

            if (spigot == null) {
                return false;
            }

            Class<?> spigotApi = Class.forName("org.bukkit.command.CommandSender$Spigot");
            spigotApi.getMethod("sendMessage", BaseComponent[].class).invoke(spigot, (Object) components);
            return true;
        } catch (Throwable ignored) {
            // spigot() absent (true 1.8) or the component send is gone/non-functional (26.2): fall back.
            return false;
        }
    }

    private void addJavaVersion(@Nonnull CommandSender sender, @Nonnull ComponentBuilder builder) {
        int version = NumberUtils.getJavaVersion();

        if (version < RECOMMENDED_JAVA_VERSION) {
            // @formatter:off
            builder.append(msg(sender, "commands.versions.java-label") + version).color(ChatColor.RED)
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(
                    String.join("\n", msgList(sender, "commands.versions.java-outdated-hover")).replace("%version%", String.valueOf(RECOMMENDED_JAVA_VERSION))
                )))
                .append("\n")
                .event((HoverEvent) null);
            // @formatter:on
        } else {
            builder.append(msg(sender, "commands.versions.java-label")).color(ChatColor.GREEN).append(version + "\n").color(ChatColor.DARK_GREEN);
        }
    }

    private void addPluginVersions(@Nonnull CommandSender sender, @Nonnull ComponentBuilder builder) {
        Collection<Plugin> addons = Slimefun.getInstalledAddons();

        if (addons.isEmpty()) {
            builder.append(msg(sender, "commands.versions.no-addons")).color(ChatColor.GRAY).italic(true);
            return;
        }

        builder.append(msg(sender, "commands.versions.installed-addons")).color(ChatColor.GRAY).append("(" + addons.size() + ")").color(ChatColor.DARK_GRAY);

        for (Plugin plugin : addons) {
            String version = plugin.getDescription().getVersion();

            HoverEvent hoverEvent = null;
            ClickEvent clickEvent = null;
            ChatColor primaryColor;
            ChatColor secondaryColor;

            if (Bukkit.getPluginManager().isPluginEnabled(plugin)) {
                primaryColor = ChatColor.GREEN;
                secondaryColor = ChatColor.DARK_GREEN;
                String authors = String.join(", ", plugin.getDescription().getAuthors());

                if (plugin instanceof SlimefunAddon && ((SlimefunAddon) plugin).getBugTrackerURL() != null) {
                    SlimefunAddon addon = (SlimefunAddon) plugin;                    // @formatter:off
                    hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("")
                        .append(msg(sender, "commands.versions.author-label"))
                        .append(authors)
                        .color(ChatColor.YELLOW)
                        .append("\n" + msg(sender, "commands.versions.click-issues-tracker"))
                        .color(ChatColor.GOLD)
                        .create()
                    );
                    // @formatter:on

                    clickEvent = new ClickEvent(ClickEvent.Action.OPEN_URL, addon.getBugTrackerURL());
                } else {
                    // @formatter:off
                    hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("")
                        .append(msg(sender, "commands.versions.author-label"))
                        .append(authors)
                        .color(ChatColor.YELLOW)
                        .create()
                    );
                    // @formatter:on
                }
            } else {
                primaryColor = ChatColor.RED;
                secondaryColor = ChatColor.DARK_RED;

                if (plugin instanceof SlimefunAddon && ((SlimefunAddon) plugin).getBugTrackerURL() != null) {
                    SlimefunAddon addon = (SlimefunAddon) plugin;                    // @formatter:off
                    hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("")
                        .append(String.join("\n", msgList(sender, "commands.versions.disabled-notice")))
                        .color(ChatColor.RED)
                        .append("\n" + msg(sender, "commands.versions.click-report-tracker"))
                        .color(ChatColor.DARK_RED)
                        .create()
                    );
                    // @formatter:on

                    if (addon.getBugTrackerURL() != null) {
                        clickEvent = new ClickEvent(ClickEvent.Action.OPEN_URL, addon.getBugTrackerURL());
                    }
                } else {
                    hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(msg(sender, "commands.versions.disabled-plain")));
                }
            }

            // @formatter:off
            // We need to reset the hover event or it's added to all components
            builder.append("\n  " + plugin.getName())
                .color(primaryColor)
                .event(hoverEvent)
                .event(clickEvent)
                .append(" v" + version)
                .color(secondaryColor)
                .append("")
                .event((ClickEvent) null)
                .event((HoverEvent) null);
            // @formatter:on
        }
    }

    /**
     * @implNote Mirrors {@link SubCommand#getDescription(CommandSender)}: a {@link Player} gets their own
     *           selected language, anything else (console) falls back to the server default.
     */
    @Nonnull
    private String msg(@Nonnull CommandSender sender, @Nonnull String key) {
        return sender instanceof Player ? Slimefun.getLocalization().getMessage((Player) sender, key) : Slimefun.getLocalization().getMessage(key);
    }

    @Nonnull
    private List<String> msgList(@Nonnull CommandSender sender, @Nonnull String key) {
        return sender instanceof Player ? Slimefun.getLocalization().getMessages((Player) sender, key) : Slimefun.getLocalization().getDefaultMessages(key);
    }
}

