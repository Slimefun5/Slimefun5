package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.thebusybiscuit.slimefun5.api.events.PlayerLanguageChangeEvent;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.core.checks.LiveChecks;
import io.github.thebusybiscuit.slimefun5.core.checks.packets.PacketWatch;
import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.Sandbox;
import io.github.thebusybiscuit.slimefun5.core.guide.options.ItemDescriptionsOption;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.PdcCompat;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.packet.PacketItemDescriptor;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.packet.PacketReflect;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.packet.PacketWindowTitleDescriptor;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

/**
 * Rewrites outbound inventory packets so each player sees Slimefun items in their own language, without
 * mutating the stored item. A no-op if translation.packets is disabled or no packet descriptor resolves
 * on this server version (both leave items at their raw-id canonical = the fallback).
 */
public class PacketTranslationService implements Listener {

    private static final String HANDLER_NAME = "slimefun-translate";

    /** Sentinel stored in {@link #languageCache} for "no explicit language / no known locale" (ConcurrentHashMap forbids null values). */
    private static final String NO_LANGUAGE = "";

    private final List<PacketItemDescriptor> descriptors;

    /** Packets carrying stacks a client sends back, whose translation must be undone before the server reads them. */
    private final List<PacketItemDescriptor> inboundDescriptors;

    /**
     * @implNote Snapshotted once at construction (like descriptors above) rather than re-read per packet:
     *           reading {@code TranslationConfig.fallback()} on the Netty thread would race an admin config
     *           reload ({@code Slimefun.getCfg()} is not synchronized for concurrent reads) and re-parse the
     *           enum on every single packet.
     */
    private final TranslationConfig.FallbackMode fallback;

    /**
     * @implNote Snapshotted once at construction, same rationale as {@link #fallback}. {@code refreshLanguage()}
     *           runs on the main thread so reading this field there is safe; it must never be re-read from
     *           {@code TranslationConfig} off the main thread.
     */
    private final TranslationConfig.LanguageSource languageSource;

    /**
     * Effective language id per player, refreshed on the main thread (join / language change) and read
     * verbatim by the Netty write handler. This is the ONLY thing the Netty thread may touch - it must
     * never call into {@link LocalizationService#getLanguage(Player)} or entity PDC directly, both of
     * which are main-thread-only (and on 1.8-1.13 the legacy-pdc.yml fallback path is a shared,
     * synchronized-guarded map that a Netty thread would read outside that guard, racing main-thread writes).
     */
    private final Map<UUID, String> languageCache = new ConcurrentHashMap<>();

    /**
     * Per-player description-toggle preference, refreshed on the main thread (join / toggle click) and
     * read verbatim by the Netty write handler. Absent means "default true" - the Netty thread must never
     * call {@link ItemDescriptionsOption#isEnabledFor(Player)} directly (it reads player PDC).
     */
    private final Map<UUID, Boolean> descriptionsCache = new ConcurrentHashMap<>();

    /**
     * Resolved once, like {@link #descriptors}; {@code null} means this server's open-window packet
     * cannot be retitled per-viewer, so the vanilla-container-title listener must keep using its
     * one-tick-later {@code setTitle} fallback instead.
     */
    @Nullable
    private final PacketWindowTitleDescriptor windowTitleDescriptor;

    /**
     * The title each player's very next open-window packet is to carry, set on the main thread by
     * {@link #notifyVanillaContainerOpen} or {@link #notifyWindowTitle} and taken by the Netty write handler
     * that intercepts that packet. Never read/written directly from anywhere else.
     */
    private final PendingWindowTitles pendingWindowTitles = new PendingWindowTitles(System::nanoTime);

    /** Players whose channel carries the translation handler; apart from sandbox players, only their windows can be retitled by packet. */
    private final Set<UUID> injectedPlayers = ConcurrentHashMap.newKeySet();

    private PacketTranslationService(@Nonnull List<PacketItemDescriptor> descriptors, @Nonnull List<PacketItemDescriptor> inboundDescriptors,
            @Nullable PacketWindowTitleDescriptor windowTitleDescriptor) {
        this.descriptors = descriptors;
        this.inboundDescriptors = inboundDescriptors;
        this.fallback = TranslationConfig.fallback();
        this.languageSource = TranslationConfig.languageSource();
        this.windowTitleDescriptor = windowTitleDescriptor;
    }

    public PacketTranslationService(@Nonnull Slimefun plugin) {
        this(PacketItemDescriptor.resolveAll(), PacketItemDescriptor.resolveInbound(),
            TranslationConfig.packetsEnabled() ? PacketWindowTitleDescriptor.resolve() : null);

        if (!TranslationConfig.packetsEnabled() || (descriptors.isEmpty() && windowTitleDescriptor == null)) {
            Slimefun.logger().info("Packet item translation disabled or unsupported on this version; "
                + "items use their configured fallback.");
            return;
        }

        Slimefun.logger().info("Packet item translation active (" + descriptors.size() + " item packets, "
            + inboundDescriptors.size() + " inbound, window titles " + (windowTitleDescriptor != null ? "on" : "off") + ").");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        injectAll();
    }

    /**
     * A service that translates only the packets handed to {@link #translateOutbound}: it listens to no event and
     * injects no channel.
     */
    @Nonnull
    public static PacketTranslationService withoutChannels(@Nonnull PacketWindowTitleDescriptor windowTitleDescriptor) {
        return new PacketTranslationService(Collections.emptyList(), Collections.emptyList(), windowTitleDescriptor);
    }

    /**
     * Whether this server can retitle a vanilla container's open-window packet before it reaches the
     * client. When {@code false}, the caller (the vanilla-container-title listener) falls back to its
     * slower, one-tick-later {@code setTitle} correction instead.
     */
    public boolean canRetitleWindows() {
        return windowTitleDescriptor != null;
    }

    /**
     * Whether this player's next open-window packet can be retitled: the server supports it and this
     * player's channel carries the handler. When {@code false}, a caller falls back to {@code setTitle}.
     *
     * @implNote A sandbox player has no channel; the checks feed its packets through {@link #translateOutbound}
     *           instead, so it takes the packet path exactly as a real player with the handler does.
     */
    public boolean canRetitleWindowFor(@Nonnull Player player) {
        UUID playerId = player.getUniqueId();
        return windowTitleDescriptor != null && (injectedPlayers.contains(playerId) || Sandbox.isSandboxPlayer(playerId));
    }

    /** Whether a title is recorded for the open-window packet this player receives next. */
    public boolean hasPendingWindowTitle(@Nonnull Player player) {
        return pendingWindowTitles.isPending(player.getUniqueId());
    }

    /**
     * Translates an outbound packet for this player exactly as the handler on their channel would, and returns
     * the packet to send in its place. The translation never throws; for a sandbox player the refresh before it
     * may, so the check feeding the packet reports it instead of reading a stale language.
     *
     * @implNote A sandbox player never joins through an event and is given its language after it is online, so
     *           its cached language and description preference are refreshed here; for a sandbox player this must
     *           run on the main thread.
     */
    @Nonnull
    public Object translateOutbound(@Nonnull Player player, @Nonnull Object packet) {
        if (Sandbox.isSandboxPlayer(player.getUniqueId())) {
            refreshLanguage(player);
            refreshDescriptions(player);
        }
        return translate(player, packet);
    }

    /**
     * Records an already resolved title for the open-window packet {@code player} is about to receive. Must
     * be called on the main thread, before the window is opened. A no-op when {@link #canRetitleWindowFor}
     * is {@code false}.
     */
    public void notifyWindowTitle(@Nonnull Player player, @Nonnull String title) {
        if (canRetitleWindowFor(player)) {
            pendingWindowTitles.expectTitle(player.getUniqueId(), title);
        }
    }

    /** Drops a title recorded for a window that, in the end, did not open. */
    public void cancelWindowTitle(@Nonnull Player player) {
        pendingWindowTitles.cancel(player.getUniqueId());
    }

    /**
     * Records that {@code player} is about to receive the open-window packet for a vanilla-container
     * {@link SlimefunItem} block with the given id, so the Netty write handler can retitle that exact
     * packet before it reaches the client. Must be called from {@code InventoryOpenEvent} (main thread),
     * which always fires strictly before Bukkit sends the corresponding packet. A no-op when
     * {@link #canRetitleWindows()} is {@code false}.
     */
    public void notifyVanillaContainerOpen(@Nonnull Player player, @Nonnull String itemId) {
        if (windowTitleDescriptor != null) {
            pendingWindowTitles.expectItem(player.getUniqueId(), itemId);
        }
    }

    public void injectAll() {
        for (Player p : Slimefun.instance().getServer().getOnlinePlayers()) {
            inject(p);
            refreshLanguage(p);
            refreshDescriptions(p);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        inject(e.getPlayer());
        refreshLanguage(e.getPlayer());
        refreshDescriptions(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // Channel is torn down by the server; nothing to clean up explicitly.
        languageCache.remove(e.getPlayer().getUniqueId());
        descriptionsCache.remove(e.getPlayer().getUniqueId());
        pendingWindowTitles.cancel(e.getPlayer().getUniqueId());
        injectedPlayers.remove(e.getPlayer().getUniqueId());
    }

    /**
     * Refreshes the description-toggle preference cache for a player. Must run on the main thread: it
     * reads the player's PDC ({@link ItemDescriptionsOption#isEnabledFor(Player)}). Called on join and
     * whenever the player flips the toggle so the packet layer reflects it immediately.
     */
    public void refreshDescriptions(@Nonnull Player p) {
        descriptionsCache.put(p.getUniqueId(), ItemDescriptionsOption.isEnabledFor(p));
    }

    @EventHandler
    public void onLanguageChange(PlayerLanguageChangeEvent e) {
        // The new language is only applied to the player after this event resolves, so re-resolve one
        // tick later - still on the main thread.
        Player p = e.getPlayer();
        Slimefun.runSync(() -> refreshLanguage(p), 1L);
    }

    /**
     * Computes the effective packet-translation language for a player and stores it in the thread-safe
     * cache. Must run on the main thread: it reads the player's explicitly-chosen Slimefun language (PDC)
     * and, failing that, their MC client locale.
     */
    private void refreshLanguage(@Nonnull Player p) {
        String explicit = PdcCompat.getString(p, Slimefun.getLocalization().getKey());
        String clientLocale = null;

        try {
            // Reflective: Player.getLocale() was added after the 1.8.8 Bukkit API this module compiles against.
            String locale = (String) p.getClass().getMethod("getLocale").invoke(p);
            if (locale != null && locale.length() >= 2) {
                clientLocale = locale.substring(0, 2).toLowerCase(java.util.Locale.ROOT);
            }
        } catch (Throwable ignored) {
            // fall through, clientLocale stays null
        }

        Language defaultLanguage = Slimefun.getLocalization().getDefaultLanguage();
        String serverDefault = defaultLanguage != null ? defaultLanguage.getId() : null;

        String resolved = LanguageResolver.resolveLanguageId(explicit, clientLocale, languageSource,
            id -> Slimefun.getLocalization().isLanguageLoaded(id), serverDefault);

        languageCache.put(p.getUniqueId(), resolved != null ? resolved : NO_LANGUAGE);
    }

    private void inject(Player player) {
        Object channelObj = PacketReflect.channelOf(player);
        if (!(channelObj instanceof io.netty.channel.Channel)) {
            return;
        }
        io.netty.channel.Channel channel = (io.netty.channel.Channel) channelObj;

        try {
            if (channel.pipeline().get(HANDLER_NAME) != null) {
                injectedPlayers.add(player.getUniqueId());
                return;
            }
            channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new ChannelDuplexHandler() {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                    Object out = translate(player, msg);
                    super.write(ctx, out, promise);
                }

                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    super.channelRead(ctx, restoreShownOriginals(msg));
                }
            });
            injectedPlayers.add(player.getUniqueId());
        } catch (Throwable t) {
            // "packet_handler" absent or pipeline locked → skip; player sees canonical id (fallback).
            // Logged (once per join, not spammy) so an operator on a fork with a differently-named
            // pipeline stage has a signal instead of silently losing packet translation.
            Slimefun.logger().info("Could not inject packet translation handler for " + player.getName()
                + " (pipeline stage \"packet_handler\" missing or locked); items will use their configured fallback.");
        }
    }

    /** Returns msg (possibly a rewritten packet). Never throws - on any failure returns msg unchanged. */
    private Object translate(Player player, Object msg) {
        try {
            if (windowTitleDescriptor != null && windowTitleDescriptor.matches(msg)) {
                String applied = applyPendingWindowTitle(player, msg);
                if (applied != null) {
                    watchTitle(player.getUniqueId(), applied);
                }
                return msg;
            }

            for (PacketItemDescriptor descriptor : descriptors) {
                if (descriptor.matches(msg)) {
                    // Pure cache reads - no getLanguage()/entity PDC/ItemDescriptionsOption access on the
                    // Netty thread.
                    UUID playerId = player.getUniqueId();
                    String cached = languageCache.get(playerId);
                    final String language = NO_LANGUAGE.equals(cached) ? null : cached;
                    return descriptor.rewrite(msg, nmsItem -> rewriteItem(nmsItem, playerId, language, fallback));
                }
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return msg;
    }

    private void watchTitle(UUID playerId, String appliedTitle) {
        try {
            PacketWatch watch = LiveChecks.activeWatch();
            if (watch != null && !Sandbox.isSandboxPlayer(playerId)) {
                watch.observe("title", ChatColor.stripColor(appliedTitle), appliedTitle);
            }
        } catch (Throwable ignored) {
            // the watch must never affect the packet
        }
    }

    private void watchItem(UUID playerId, TranslatedStack translated) {
        try {
            PacketWatch watch = LiveChecks.activeWatch();
            if (watch != null && translated.shownName != null && !Sandbox.isSandboxPlayer(playerId)) {
                watch.observe("item", translated.subject, translated.shownName);
            }
        } catch (Throwable ignored) {
            // the watch must never affect the packet
        }
    }

    /**
     * Retitles an outbound open-window packet to the title recorded for this player, if one was recorded
     * recently enough: a resolved title as it is, a vanilla-container item by its per-viewer name. One-shot,
     * so a later, unrelated open-window packet for the same player never reuses it.
     *
     * @return the title the packet now carries, or {@code null} when it was left as it was
     */
    @Nullable
    private String applyPendingWindowTitle(@Nonnull Player player, @Nonnull Object packet) {
        String cached = languageCache.get(player.getUniqueId());
        String language = NO_LANGUAGE.equals(cached) ? null : cached;
        String title = pendingWindowTitles.take(player.getUniqueId(), itemId -> resolveWindowTitle(itemId, language));

        return title != null && windowTitleDescriptor.retitle(packet, title) ? title : null;
    }

    /**
     * @implNote Unlike the vanilla-container-title listener's own {@code resolveTitle} (which runs on the
     *           main thread and may read {@code TranslationConfig.fallback()} fresh), this runs on the
     *           Netty thread, where only the snapshotted {@link #fallback} field is safe to read.
     */
    @Nonnull
    private String resolveWindowTitle(@Nonnull String itemId, @Nullable String languageId) {
        ItemTranslationService.RenderedDisplay display =
            Slimefun.getItemTranslationService().renderForPacket(itemId, languageId, fallback, false);
        return display != null ? display.name : itemId;
    }

    /** Returns msg, or a rebuilt copy whose shown stacks carry the server's own name and lore again. Never throws. */
    private Object restoreShownOriginals(Object msg) {
        try {
            for (PacketItemDescriptor descriptor : inboundDescriptors) {
                if (descriptor.matches(msg)) {
                    return descriptor.rewrite(msg, PacketTranslationService::restoreItem);
                }
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return msg;
    }

    private static Object restoreItem(Object nmsItem) {
        ItemStack bukkit = PacketReflect.asBukkit(nmsItem);
        if (bukkit == null || !bukkit.hasItemMeta() || !ShownOriginal.restore(bukkit)) {
            return nmsItem;
        }
        Object restored = PacketReflect.asNms(bukkit);
        return restored != null ? restored : nmsItem;
    }

    private Object rewriteItem(Object nmsItem, UUID playerId, String language, TranslationConfig.FallbackMode fallback) {
        ItemStack bukkit = PacketReflect.asBukkit(nmsItem);
        if (bukkit == null || !bukkit.hasItemMeta()) {
            return nmsItem;
        }
        ShownOriginal original = ShownOriginal.of(bukkit);
        TranslatedStack translated = translatedCopy(bukkit, playerId, language, fallback);
        if (translated == null) {
            return nmsItem;
        }
        watchItem(playerId, translated);
        ItemStack shown = translated.stack;
        original.stashOnto(shown);
        Object rewritten = PacketReflect.asNms(shown);
        return rewritten != null ? rewritten : nmsItem;
    }

    /**
     * The stack as this viewer should see it, or {@code null} when nothing about it is translated.
     *
     * @implNote The id and the applied name come from the rewrite itself, so the watch reads nothing again.
     */
    @Nullable
    private TranslatedStack translatedCopy(ItemStack bukkit, UUID playerId, String language, TranslationConfig.FallbackMode fallback) {
        String id = Slimefun.getItemDataService().getItemData(bukkit).orElse(null);
        if (id == null) {
            ItemStack menuItem = Slimefun.getMenuTranslationService().rewriteStampedItem(bukkit, language);
            if (menuItem != null) {
                return menuStack(menuItem);
            }
            ItemStack guideBook = rewriteGuideBook(bukkit, language);
            return guideBook != null ? new TranslatedStack(guideBook, null, null) : null;
        }
        boolean includeDescription = !Boolean.FALSE.equals(descriptionsCache.get(playerId)); // default true
        String shownName = PacketItemRewriter.applyPacketTranslation(bukkit, id, language, fallback, includeDescription);
        return new TranslatedStack(bukkit, id, shownName);
    }

    /**
     * Reads the stamp and name back only while the watch is on, since a menu rewrite does not hand them out.
     *
     * @implNote A read that throws costs the watch this stack, never the packet its translation.
     */
    static TranslatedStack menuStack(ItemStack menuItem) {
        if (LiveChecks.activeWatch() == null) {
            return new TranslatedStack(menuItem, null, null);
        }
        try {
            return watchedMenuStack(menuItem);
        } catch (Throwable ignored) {
            return new TranslatedStack(menuItem, null, null);
        }
    }

    private static TranslatedStack watchedMenuStack(ItemStack menuItem) {
        ItemMeta meta = menuItem.getItemMeta();
        MenuSlotKey key = meta == null ? null : MenuSlotKey.parse(PdcCompat.getString(meta, Slimefun.getRegistry().getMenuSlotKey()));
        boolean nameable = meta != null && key != null && meta.hasDisplayName();
        String subject = key == null ? menuItem.getType().name() : MenuSlotKey.format(key.getPresetId(), key.getSlot());
        return new TranslatedStack(menuItem, subject, nameable ? meta.getDisplayName() : null);
    }

    /** A stack core rewrote, with what the watch needs: who it is and the name it now shows ({@code null} when core set none). */
    static final class TranslatedStack {

        final ItemStack stack;
        final String subject;
        final String shownName;

        private TranslatedStack(ItemStack stack, String subject, String shownName) {
            this.stack = stack;
            this.subject = subject;
            this.shownName = shownName;
        }
    }

    @Nullable
    private ItemStack rewriteGuideBook(ItemStack bukkit, String language) {
        ItemMeta meta = bukkit.getItemMeta();
        if (meta == null) {
            return null;
        }
        String mode = PdcCompat.getString(meta, Slimefun.getRegistry().getGuideDataKey());
        if (mode == null) {
            return null; // not the guide book
        }
        ItemTranslationService.RenderedDisplay d =
            Slimefun.getGuideBookDisplay().rendered(language, "CHEAT_MODE".equals(mode));
        if (d == null) {
            return null;
        }
        meta.setDisplayName(d.name);
        meta.setLore(d.lore.isEmpty() ? null : d.lore);
        bukkit.setItemMeta(meta);
        return bukkit;
    }

}
