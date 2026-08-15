package io.github.thebusybiscuit.slimefun5.implementation.listeners;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Nameable;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.core.services.localization.ItemTranslationService;
import io.github.thebusybiscuit.slimefun5.core.services.localization.Language;
import io.github.thebusybiscuit.slimefun5.core.services.localization.PacketTranslationService;
import io.github.thebusybiscuit.slimefun5.core.services.localization.TranslationConfig;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.ReflectionCompat;

import me.mrCookieSlime.Slimefun.api.BlockStorage;

/**
 * {@link BlockListener#clearInheritedCustomName} clears a vanilla-container-backed {@link SlimefunItem}
 * block's inherited {@code CustomName} on placement, so the GUI title is not frozen in the placer's
 * language for every future viewer - but that leaves the block falling back to vanilla's own title (e.g.
 * a placed Enhanced Furnace just reading "Furnace"). This retitles the just-opened vanilla {@link Inventory}
 * for the opening viewer, to the block's Slimefun name (translated per viewer, the item id as the ultimate
 * fallback), via one of two paths:
 * <ul>
 * <li>If {@link PacketTranslationService#canRetitleWindows()} - the packet layer can rewrite the
 * open-window packet's title field before it ever reaches the client, so this hands the item id off to
 * {@link PacketTranslationService#notifyVanillaContainerOpen} and stops, with no visible flash at all.</li>
 * <li>Otherwise (older/newer server versions where that packet's title field could not be resolved
 * reflectively), the one-tick-later {@code InventoryView#setTitle} correction below runs instead - the same
 * mechanism {@link me.mrCookieSlime.Slimefun.api.inventory.BlockMenu#open} already applies to custom
 * Slimefun GUIs, which still lets the wrong title flash for a frame.</li>
 * </ul>
 */
public class VanillaContainerTitleListener implements Listener {

    @ParametersAreNonnullByDefault
    public VanillaContainerTitleListener(Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        HumanEntity human = e.getPlayer();

        if (!(human instanceof Player)) {
            return;
        }

        InventoryHolder holder = e.getInventory().getHolder();

        if (!(holder instanceof Nameable) || !(holder instanceof BlockState)) {
            return;
        }

        Block block = ((BlockState) holder).getBlock();
        SlimefunItem item = BlockStorage.check(block);

        if (item == null) {
            return;
        }

        Player player = (Player) human;
        PacketTranslationService packetService = Slimefun.getPacketTranslationService();

        if (packetService != null && packetService.canRetitleWindows()) {
            // The packet layer corrects the title before the client ever sees it - the tick-later
            // fallback below would be redundant (and would resend an already-correct title).
            packetService.notifyVanillaContainerOpen(player, item.getId());
            return;
        }

        Inventory inventory = e.getInventory();

        // Deferred one tick: the vanilla OpenWindow packet (still carrying the block's own title) is sent
        // right after this event returns, so retitling here would just be overwritten by it - the same
        // post-open timing BlockMenu#open relies on for custom Slimefun GUIs.
        Slimefun.runSync(() -> retitle(player, inventory, item), 1L);
    }

    private void retitle(@Nonnull Player player, @Nonnull Inventory inventory, @Nonnull SlimefunItem item) {
        if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) {
            return;
        }

        try {
            ReflectionCompat.invoke(player.getOpenInventory(), "setTitle", resolveTitle(item.getId(), languageOf(player)));
        } catch (Exception | LinkageError ignored) {
            // setTitle is 1.13+ and purely cosmetic - never break the open inventory over it.
        }
    }

    @Nonnull
    static String resolveTitle(@Nonnull String itemId, @Nullable String languageId) {
        ItemTranslationService.RenderedDisplay display = Slimefun.getItemTranslationService()
            .renderForPacket(itemId, languageId, TranslationConfig.fallback(), false);
        return display != null ? display.name : itemId;
    }

    @Nullable
    private static String languageOf(@Nonnull Player p) {
        Language language = Slimefun.getLocalization().getLanguage(p);
        return language != null ? language.getId() : null;
    }
}
