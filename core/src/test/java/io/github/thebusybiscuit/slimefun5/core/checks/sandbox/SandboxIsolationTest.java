package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import io.github.thebusybiscuit.slimefun5.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun5.core.checks.Check;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckContext;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckRunner;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckStep;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckThread;
import io.github.thebusybiscuit.slimefun5.core.checks.Findings;
import io.github.thebusybiscuit.slimefun5.core.checks.LiveChecks;
import io.github.thebusybiscuit.slimefun5.core.checks.Stage;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.storage.Storage;
import io.github.thebusybiscuit.slimefun5.storage.backend.BlockStorageBackend;
import io.github.thebusybiscuit.slimefun5.storage.data.PlayerData;

import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.Slimefun.api.BlockInfoConfig;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.inventory.UniversalBlockMenu;

class SandboxIsolationTest {

    private ServerMock server;
    private Slimefun plugin;
    private RecordingBackend realBackend;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Slimefun.class);
        realBackend = new RecordingBackend();
        Slimefun.setBlockStorageBackendForTesting(new RoutingBlockStorageBackend(realBackend));
    }

    @AfterEach
    void tearDown() {
        LiveChecks.stop();
        Sandbox.closeOpen();
        MockBukkit.unmock();
    }

    @Test
    void enableWrapsTheRealBackendInTheRouter() {
        MockBukkit.unmock();
        server = MockBukkit.mock();
        MockBukkit.load(Slimefun.class);

        Assertions.assertInstanceOf(RoutingBlockStorageBackend.class, Slimefun.getBlockStorageBackend());
    }

    @Test
    void placingInSandboxWritesNothingToRealBackend() {
        World realWorld = server.addSimpleWorld("real_world");
        BlockStorage.getOrCreate(realWorld);
        Location realLocation = new Location(realWorld, 3, 64, 3);
        World sandboxWorld;

        try (Sandbox sandbox = Sandbox.open(true)) {
            sandboxWorld = sandbox.world().world();
            Location placed = new Location(sandboxWorld, 1, 64, 1);

            BlockStorage.addBlockInfo(placed, "id", "SANDBOX_TEST_ITEM");
            BlockStorage.addBlockInfo(realLocation, "id", "REAL_TEST_ITEM");
            BlockStorage.setChunkInfo(sandboxWorld, 0, 0, "resource", "1");
            BlockStorage.setChunkInfo(realWorld, 0, 0, "resource", "2");
            BlockStorage.getStorage(sandboxWorld).save();
            BlockStorage.getStorage(realWorld).save();
            BlockStorage.saveChunks();
            BlockStorage.clearBlockInfo(placed);
            Slimefun.getTickerTask().run();
            BlockStorage.getStorage(sandboxWorld).save();
            Slimefun.getBlockStorageBackend().deleteInventory(placed);
            Slimefun.getBlockStorageBackend().loadInventoryIfPresent(placed, null);
            Map<Location, BlockMenu> inventories = new HashMap<>();
            Slimefun.getBlockStorageBackend().flushInventories(inventories);

            Assertions.assertFalse(router().memory().isEmpty(), "the sandbox's writes went to memory");
        }

        Assertions.assertTrue(realBackend.calls().stream().noneMatch(call -> call.contains(Sandbox.WORLD_NAME)), () -> "real backend saw the sandbox: " + realBackend.calls());
        Assertions.assertTrue(realBackend.calls().stream().anyMatch(call -> call.contains("real_world")), () -> "real backend lost the real world: " + realBackend.calls());
        Assertions.assertTrue(router().memory().isEmpty());
        Assertions.assertNull(BlockStorage.getStorage(sandboxWorld));
        Assertions.assertTrue(Slimefun.getRegistry().getChunks().keySet().stream().noneMatch(key -> key.startsWith(Sandbox.WORLD_NAME + ";")));
    }

    @Test
    void sandboxProfilesAreNeverSaved() throws ReflectiveOperationException {
        RecordingStorage storage = new RecordingStorage();
        installPlayerStorage(storage);
        UUID sandboxId;

        try (Sandbox sandbox = Sandbox.open(true)) {
            SandboxPlayer player = sandbox.join("Tester", "en_us");
            sandboxId = player.player().getUniqueId();
            Assertions.assertTrue(Sandbox.isSandboxPlayer(sandboxId));

            PlayerProfile profile = profileOf(player.player());
            Slimefun.getRegistry().getPlayerProfiles().put(sandboxId, profile);
            profile.markDirty();
            profile.save();
            PlayerProfile.iterator().forEachRemaining(PlayerProfile::save);
        }

        Assertions.assertFalse(storage.savedIds().contains(sandboxId));
        Assertions.assertFalse(Slimefun.getRegistry().getPlayerProfiles().containsKey(sandboxId), "a sandbox profile outlived the sandbox");
        Assertions.assertTrue(Sandbox.isSandboxPlayer(sandboxId), "a closed sandbox's players stay unsaveable");

        PlayerProfile realProfile = profileOf(server.addPlayer("RealPlayer"));
        realProfile.markDirty();
        realProfile.save();
        Assertions.assertTrue(storage.savedIds().contains(realProfile.getUUID()), "a real player's profile is still saved");
        Assertions.assertFalse(Sandbox.isSandboxPlayer(realProfile.getUUID()));
    }

    @Test
    void liveEventsSkipUnrelatedPlugins() throws Exception {
        Plugin unrelated = MockBukkit.createMockPlugin("UnrelatedProtection");
        Plugin dependent = MockBukkit.loadWith(DependentAddon.class, description(DependentAddon.class, "depend: [Slimefun]"));
        Plugin softDependent = MockBukkit.loadWith(SoftDependentAddon.class, description(SoftDependentAddon.class, "softdepend: [Slimefun]"));
        List<String> received = new ArrayList<>();
        listen(unrelated, received);
        listen(dependent, received);
        listen(softDependent, received);
        listen(plugin, received);

        try (Sandbox sandbox = Sandbox.open(true)) {
            sandbox.callEvent(new ProbeEvent());
        }

        Assertions.assertEquals(Arrays.asList("DependentAddon", "Slimefun", "SoftDependentAddon"), sorted(received));

        received.clear();
        Sandbox.deliver(new ProbeEvent());
        Assertions.assertEquals(Arrays.asList("DependentAddon", "Slimefun", "SoftDependentAddon", "UnrelatedProtection"), sorted(received));
    }

    @Test
    void sandboxPlayersAreNotOnline() {
        server.addPlayer("RealPlayer");
        Collection<?> before = new ArrayList<>(Bukkit.getOnlinePlayers());

        try (Sandbox sandbox = Sandbox.open(true)) {
            SandboxPlayer player = sandbox.join("Tester", "de_de");

            Assertions.assertEquals(before, new ArrayList<>(Bukkit.getOnlinePlayers()));
            Assertions.assertNull(Bukkit.getPlayer(player.player().getUniqueId()));
            Assertions.assertNull(Bukkit.getWorld(Sandbox.WORLD_NAME), "the live sandbox world is not registered with the server");
        }
    }

    @Test
    void sandboxClosesAfterCrash() {
        AtomicReference<Sandbox> seen = new AtomicReference<>();
        Check crashing = check("crashing", Stage.MEDIUM, context -> () -> {
            Sandbox sandbox = context.sandbox();
            seen.set(sandbox);
            sandbox.join("Crasher", "en_us");
            BlockStorage.addBlockInfo(new Location(sandbox.world().world(), 0, 64, 0), "id", "SANDBOX_TEST_ITEM");
            BlockStorage.getStorage(sandbox.world().world()).save();
            throw new IllegalStateException("boom");
        });
        Findings findings = new Findings(line -> {});

        new CheckRunner(Collections.singletonList(crashing)).runBlocking(Stage.MEDIUM, new CheckContext(findings, Stage.MEDIUM, true), ticks -> {});

        Assertions.assertNotNull(seen.get(), "a MEDIUM check gets a sandbox");
        Assertions.assertTrue(seen.get().world().players().isEmpty());
        Assertions.assertTrue(router().memory().isEmpty());
        Assertions.assertFalse(Sandbox.isOpen());
        Assertions.assertTrue(findings.lines().stream().anyMatch(line -> line.contains("boom")));
    }

    @Test
    void lowRunHasNoSandbox() {
        AtomicReference<Object> seen = new AtomicReference<>("unset");
        Check low = check("low", Stage.LOW, context -> () -> {
            seen.set(context.sandbox());
            return 0;
        });

        new CheckRunner(Collections.singletonList(low)).runBlocking(Stage.LOW, new CheckContext(new Findings(line -> {}), Stage.LOW, true), ticks -> {});

        Assertions.assertNull(seen.get());
        Assertions.assertFalse(Sandbox.isOpen());
    }

    @Test
    void recordedGapsBecomeFindings() {
        Check gapping = check("gapping", Stage.MEDIUM, context -> () -> {
            context.sandbox().world().gaps().record("Player.getHealthScale()");
            return 0;
        });
        Findings findings = new Findings(line -> {});

        new CheckRunner(Collections.singletonList(gapping)).runBlocking(Stage.MEDIUM, new CheckContext(findings, Stage.MEDIUM, true), ticks -> {});

        Assertions.assertTrue(findings.lines().contains("[check:sandbox-gap] Player.getHealthScale(): the sandbox could not answer this call"), () -> findings.lines().toString());
        Assertions.assertEquals(1, findings.count());
    }

    @Test
    void disableClosesOpenSandbox() throws InterruptedException {
        AtomicReference<Sandbox> seen = new AtomicReference<>();
        Slimefun.getChecks().register(check("waiting", Stage.MEDIUM, context -> () -> {
            seen.set(context.sandbox());
            return 10_000;
        }));

        LiveChecks.start(Stage.MEDIUM, Bukkit.getConsoleSender());
        for (int tick = 0; tick < 400 && seen.get() == null; tick++) {
            server.getScheduler().performOneTick();
            Thread.sleep(5);
        }
        Assertions.assertNotNull(seen.get(), "the check ran inside a sandbox");
        Assertions.assertTrue(Sandbox.isOpen());

        server.getPluginManager().disablePlugin(plugin);

        Assertions.assertFalse(Sandbox.isOpen());
        Assertions.assertTrue(router().memory().isEmpty());
    }

    private interface StepsFactory {

        CheckStep firstStep(CheckContext context);
    }

    private static Check check(String name, Stage stage, StepsFactory factory) {
        return new Check() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Stage stage() {
                return stage;
            }

            @Override
            public CheckThread thread() {
                return CheckThread.MAIN;
            }

            @Override
            public Iterator<CheckStep> steps(CheckContext context) {
                return Collections.singletonList(factory.firstStep(context)).iterator();
            }
        };
    }

    private static RoutingBlockStorageBackend router() {
        return (RoutingBlockStorageBackend) Slimefun.getBlockStorageBackend();
    }

    private static PlayerProfile profileOf(OfflinePlayer player) {
        return new PlayerProfile(player, new PlayerData(new HashSet<>(), new HashMap<>(), new HashSet<>())) {};
    }

    private static void installPlayerStorage(Storage storage) throws ReflectiveOperationException {
        Field field = Slimefun.class.getDeclaredField("playerStorage");
        field.setAccessible(true);
        field.set(Slimefun.instance(), storage);
    }

    private static PluginDescriptionFile description(Class<? extends JavaPlugin> main, String dependency) throws Exception {
        return new PluginDescriptionFile(new StringReader("name: " + main.getSimpleName() + "\nversion: 1\nmain: " + main.getName() + "\n" + dependency + "\n"));
    }

    private static void listen(Plugin owner, List<String> received) {
        Bukkit.getPluginManager().registerEvent(ProbeEvent.class, new Listener() {}, EventPriority.NORMAL, (listener, event) -> received.add(owner.getName()), owner);
    }

    private static List<String> sorted(List<String> names) {
        List<String> copy = new ArrayList<>(names);
        Collections.sort(copy);
        return copy;
    }

    public static class DependentAddon extends JavaPlugin {}

    public static class SoftDependentAddon extends JavaPlugin {}

    public static final class ProbeEvent extends Event {

        private static final HandlerList HANDLERS = new HandlerList();

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }
    }

    private static final class RecordingStorage implements Storage {

        private final List<UUID> saved = new ArrayList<>();

        @Override
        public PlayerData loadPlayerData(UUID uuid) {
            return new PlayerData(new HashSet<>(), new HashMap<>(), new HashSet<>());
        }

        @Override
        public synchronized void savePlayerData(UUID uuid, PlayerData data) {
            saved.add(uuid);
        }

        synchronized List<UUID> savedIds() {
            return new ArrayList<>(saved);
        }
    }

    private static final class RecordingBackend implements BlockStorageBackend {

        private final List<String> calls = new ArrayList<>();

        synchronized List<String> calls() {
            return new ArrayList<>(calls);
        }

        private synchronized void record(String call) {
            calls.add(call);
        }

        private static String worldOf(Location location) {
            return location.getWorld() == null ? "null" : location.getWorld().getName();
        }

        @Override
        public Map<String, Map<Location, Config>> loadWorldBlocks(World world) {
            record("loadWorldBlocks " + world.getName());
            return new HashMap<>();
        }

        @Override
        public Map<String, BlockInfoConfig> loadChunksForWorld(World world) {
            record("loadChunksForWorld " + world.getName());
            return new HashMap<>();
        }

        @Override
        public Map<Location, BlockMenu> loadWorldInventories(World world) {
            record("loadWorldInventories " + world.getName());
            return new HashMap<>();
        }

        @Override
        public Map<String, UniversalBlockMenu> loadUniversalInventories() {
            record("loadUniversalInventories");
            return new HashMap<>();
        }

        @Override
        public BlockMenu loadInventoryIfPresent(Location l, BlockMenuPreset preset) {
            record("loadInventoryIfPresent " + worldOf(l));
            return null;
        }

        @Override
        public void flushBlocks(World world, Map<String, Config> blocksCache) {
            record("flushBlocks " + world.getName() + " " + blocksCache.keySet());
        }

        @Override
        public void deleteBlocks(World world, Collection<Location> locations) {
            record("deleteBlocks " + world.getName());
        }

        @Override
        public void flushInventories(Map<Location, BlockMenu> dirtyInventories) {
            for (Location location : dirtyInventories.keySet()) {
                record("flushInventories " + worldOf(location));
            }
        }

        @Override
        public void flushUniversalInventories(Map<String, UniversalBlockMenu> universalInventories) {
            record("flushUniversalInventories");
        }

        @Override
        public void flushChunks(Map<String, BlockInfoConfig> chunks) {
            record("flushChunks " + chunks.keySet());
        }

        @Override
        public void deleteInventory(Location l) {
            record("deleteInventory " + worldOf(l));
        }
    }
}
