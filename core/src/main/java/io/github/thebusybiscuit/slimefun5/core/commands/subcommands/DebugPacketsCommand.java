package io.github.thebusybiscuit.slimefun5.core.commands.subcommands;

import java.util.List;
import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun5.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun5.core.services.localization.ItemTranslationService;
import io.github.thebusybiscuit.slimefun5.core.services.localization.Language;
import io.github.thebusybiscuit.slimefun5.core.services.localization.TranslationConfig;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.HandCompat;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.packet.PacketItemDescriptor;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.packet.PacketReflect;
import io.netty.channel.Channel;

import me.mrCookieSlime.Slimefun.api.BlockStorage;

/**
 * {@code /sf debugpackets} reports whether the packet-based item translation layer is wired up on this
 * server: whether it is enabled, how many {@link PacketItemDescriptor}s resolved for this Minecraft
 * version, and (for a player sender) whether their Netty pipeline carries the {@code slimefun-translate}
 * handler. Purely diagnostic - it changes nothing.
 * Admin-only ({@code slimefun.command.debugpackets}, default op).
 */
class DebugPacketsCommand extends SubCommand {

    // How far the player's line of sight is traced to find the targeted machine - same convention as
    // OwnerCommand's REACH, not the tighter vanilla interaction range.
    private static final int REACH = 12;

    @ParametersAreNonnullByDefault
    DebugPacketsCommand(Slimefun plugin, SlimefunCommand cmd) {
        super(plugin, cmd, "debugpackets", true);
    }

    @Override
    @ParametersAreNonnullByDefault
    public void onExecute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("slimefun.command.debugpackets")) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        boolean enabled = TranslationConfig.packetsEnabled();
        int descriptors = PacketItemDescriptor.resolveAll().size();
        sender.sendMessage("Slimefun packet translation: enabled=" + enabled + ", descriptors=" + descriptors);

        if (sender instanceof Player) {
            Player player = (Player) sender;
            Object ch = PacketReflect.channelOf(player);
            boolean injected = ch instanceof Channel && ((Channel) ch).pipeline().get("slimefun-translate") != null;
            sender.sendMessage("  channel resolved=" + (ch != null) + ", handler injected=" + injected);

            selfTest(sender, player);
            reportTargetBlock(sender, player);
        }
    }

    /**
     * Best-effort self-test of the NMS conversion chain on the item in the sender's main hand, so a single
     * in-game run reconfirms {@code asNMSCopy}/{@code asBukkitCopy} resolved the correct overload. Also
     * prints the item's ORIGINAL (pre-translation) name/lore next to what the packet layer would render for
     * it, so a translation bug can be diagnosed by comparing source against rendered output. Never throws
     * out of the command.
     */
    private void selfTest(CommandSender sender, Player player) {
        try {
            Class<?> paramType = PacketReflect.asNmsParamType();
            sender.sendMessage("  asNMSCopy param type=" + (paramType != null ? paramType.getName() : "null"));

            ItemStack hand = HandCompat.getMainHand(player.getInventory());
            if (hand == null || hand.getType() == Material.AIR) {
                sender.sendMessage("  (no item in main hand to self-test)");
                return;
            }

            Object nms = PacketReflect.asNms(hand);
            sender.sendMessage("  asNms(hand) != null: " + (nms != null));

            String id = Slimefun.getItemDataService().getItemData(hand).orElse(null);
            if (id == null) {
                sender.sendMessage("  (hand item is not a registered Slimefun item)");
                return;
            }
            sender.sendMessage("  hand item id=" + id);

            ItemMeta meta = hand.getItemMeta();
            sender.sendMessage("  ORIGINAL name=" + (meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "(none)"));
            printLore(sender, "ORIGINAL lore", meta != null ? meta.getLore() : null);

            Language language = Slimefun.getLocalization().getLanguage(player);
            String languageId = language != null ? language.getId() : null;
            ItemTranslationService.RenderedDisplay display = Slimefun.getItemTranslationService()
                .renderForPacketWithItem(hand, id, languageId, TranslationConfig.fallback(), true);
            sender.sendMessage("  RENDERED (packet) name=" + (display != null ? display.name : "null"));
            printLore(sender, "RENDERED (packet) lore", display != null ? display.lore : null);
        } catch (Throwable t) {
            sender.sendMessage("  self-test failed: " + t);
        }
    }

    private void printLore(CommandSender sender, String label, List<String> lore) {
        if (lore == null || lore.isEmpty()) {
            sender.sendMessage("  " + label + ": (none)");
            return;
        }

        sender.sendMessage("  " + label + ":");

        for (String line : lore) {
            sender.sendMessage("    " + line);
        }
    }

    /**
     * Reports what Slimefun knows about the block the player is looking at: its stored id and the
     * ORIGINAL (untranslated) name behind it.
     *
     * @implNote Deliberately reports ANY Slimefun block, not just machines, and distinguishes the three
     *           ways a lookup can come back empty - no stored data at all, stored data but no id, and an
     *           id that no registered item claims. The old version collapsed all three into "not a
     *           recognised Slimefun machine", which is both wrong (a fruit or a decorative block is not a
     *           machine) and useless for diagnosing a block that should have identity and does not.
     */
    private void reportTargetBlock(CommandSender sender, Player player) {
        try {
            Block target = player.getTargetBlock((Set<Material>) null, REACH);

            if (target == null || target.getType() == Material.AIR) {
                sender.sendMessage("  (not looking at any block)");
                return;
            }

            String material = String.valueOf(target.getType());
            boolean hasData = BlockStorage.hasBlockInfo(target);
            String storedId = BlockStorage.checkID(target);

            if (storedId == null) {
                sender.sendMessage("  looking at " + material + ": no Slimefun id"
                    + (hasData ? " (the location HAS other stored block data)" : " (no stored block data at all)"));
                return;
            }

            SlimefunItem item = SlimefunItem.getById(storedId);

            if (item == null) {
                sender.sendMessage("  looking at " + material + ": stored id=" + storedId
                    + " but NO registered item claims that id (addon not loaded, or the id changed)");
                return;
            }

            sender.sendMessage("  looking at " + material + ": id=" + storedId
                + ", ORIGINAL name=" + item.getItemName()
                + ", addon=" + (item.getAddon() != null ? item.getAddon().getName() : "Slimefun"));
        } catch (Throwable t) {
            sender.sendMessage("  target-block report failed: " + t);
        }
    }
}
