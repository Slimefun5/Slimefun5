package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import io.github.bakedlibs.dough.protection.Interaction;
import io.github.bakedlibs.dough.protection.ProtectionLogger;
import io.github.bakedlibs.dough.protection.ProtectionManager;
import io.github.bakedlibs.dough.protection.ProtectionModule;
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
import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;
import io.github.thebusybiscuit.slimefun5.integrations.IntegrationsManager;
import io.github.thebusybiscuit.slimefun5.integrations.Protections;
import io.github.thebusybiscuit.slimefun5.storage.Storage;
import io.github.thebusybiscuit.slimefun5.storage.backend.BlockStorageBackend;
import io.github.thebusybiscuit.slimefun5.storage.data.PlayerData;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.PdcCompat;

import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.Slimefun.api.BlockInfoConfig;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.inventory.UniversalBlockMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;

class SandboxIsolationTest {

    private ServerMock server;
    private Slimefun plugin;
    private RecordingBackend realBackend;

    @TempDir
    File worldContainer;

    private boolean refuseUnload;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock(new ServerMock() {

            @Override
            public File getWorldContainer() {
                return worldContainer;
            }

            @Override
            public boolean unloadWorld(World world, boolean save) {
                return !refuseUnload && super.unloadWorld(world, save);
            }
        });
        plugin = MockBukkit.load(Slimefun.class);
        realBackend = new RecordingBackend();
        Slimefun.setBlockStorageBackendForTesting(new RoutingBlockStorageBackend(realBackend));
    }

    @AfterEach
    void tearDown() {
        Sandbox.failCloseStepForTesting(null);
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

    public static final class LegacyHolder {

        private final UUID uniqueId;

        LegacyHolder(UUID uniqueId) {
            this.uniqueId = uniqueId;
        }

        public UUID getUniqueId() {
            return uniqueId;
        }
    }

    @Test
    void legacyPersistentDataOfSandboxPlayersNeverReachesTheLegacyFile() throws IOException {
        NamespacedKey key = new NamespacedKey(plugin, "language");
        LegacyHolder realHolder = new LegacyHolder(UUID.randomUUID());
        UUID sandboxId;
        UUID sandboxEntityId;
        UUID voidWorldEntityId = UUID.randomUUID();

        try (Sandbox sandbox = Sandbox.open(true)) {
            World voidWorld = sandbox.openVoidWorld().world();
            PdcCompat.setString(legacyEntity(voidWorldEntityId, voidWorld), key, "hologram");
            sandboxId = sandbox.join("legacy_holder", "en_us").player().getUniqueId();
            World world = sandbox.world().world();
            sandboxEntityId = world.spawnEntity(new Location(world, 1, 65, 1), org.bukkit.entity.EntityType.ARMOR_STAND).getUniqueId();
            LegacyHolder sandboxHolder = new LegacyHolder(sandboxId);
            PdcCompat.setString(sandboxHolder, key, "de");
            PdcCompat.setString(new LegacyHolder(sandboxEntityId), key, "hologram");
            PdcCompat.setString(realHolder, key, "fr");

            Assertions.assertEquals("de", PdcCompat.getString(sandboxHolder, key));
        }

        String legacyFile = new String(Files.readAllBytes(new File(plugin.getDataFolder(), "legacy-pdc.yml").toPath()), java.nio.charset.StandardCharsets.UTF_8);
        Assertions.assertTrue(legacyFile.contains(realHolder.getUniqueId().toString()), legacyFile);
        Assertions.assertFalse(legacyFile.contains(sandboxId.toString()), legacyFile);
        Assertions.assertFalse(legacyFile.contains(sandboxEntityId.toString()), legacyFile);
        Assertions.assertFalse(legacyFile.contains(voidWorldEntityId.toString()), legacyFile);
    }

    /** A real entity of a server without persistent data containers, as 1.8 to 1.13 have. */
    private static org.bukkit.entity.Entity legacyEntity(UUID id, World world) {
        return (org.bukkit.entity.Entity) java.lang.reflect.Proxy.newProxyInstance(SandboxIsolationTest.class.getClassLoader(),
            new Class<?>[] { org.bukkit.entity.Entity.class }, (proxy, method, arguments) -> {
                switch (method.getName()) {
                    case "getUniqueId":
                        return id;
                    case "getWorld":
                        return world;
                    default:
                        return null;
                }
            });
    }

    @Test
    void blocksTheChecksPlaceAreNeverTicked() {
        World realWorld = server.addSimpleWorld("ticked_world");
        Location real = new Location(realWorld, 5, 64, 5);

        try (Sandbox sandbox = Sandbox.open(true)) {
            World sandboxWorld = sandbox.world().world();
            World voidWorld = sandbox.openVoidWorld().world();
            Slimefun.getTickerTask().enableTicker(new Location(sandboxWorld, 1, 64, 1));
            Slimefun.getTickerTask().enableTicker(new Location(voidWorld, 2, 64, 0));
            Slimefun.getTickerTask().enableTicker(real);

            Set<String> tickedWorlds = new HashSet<>();
            Slimefun.getTickerTask().getLocations().values().forEach(locations -> locations.forEach(location -> tickedWorlds.add(location.getWorld().getName())));
            Assertions.assertEquals(Collections.singleton("ticked_world"), tickedWorlds);
        } finally {
            Slimefun.getTickerTask().disableTicker(real);
        }
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
        assertRealBackendSawNoSandbox();
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

    @Test
    void closeLeavesRealTickerStateUntouched() {
        World realWorld = server.addSimpleWorld("real_world");
        Location ticking = new Location(realWorld, 1, 64, 1);
        Location queued = new Location(realWorld, 2, 64, 2);
        UnloadableLocation unloaded = new UnloadableLocation(realWorld, 3, 64, 3);
        Slimefun.getTickerTask().enableTicker(ticking);
        Slimefun.getTickerTask().enableTicker(unloaded);
        Slimefun.getTickerTask().queueDelete(queued, false);
        Slimefun.getTickerTask().queueDelete(unloaded, false);
        unloaded.unload();

        Sandbox sandbox = Sandbox.open(true);
        Location sandboxTicking = new Location(sandbox.world().world(), 0, 64, 0);
        Slimefun.getTickerTask().enableTicker(sandboxTicking);
        Slimefun.getTickerTask().queueDelete(sandboxTicking, false);
        sandbox.close();

        Assertions.assertEquals(Collections.emptyList(), sandbox.closeFailures());
        Assertions.assertTrue(isTicking(ticking));
        Assertions.assertTrue(isTicking(unloaded));
        Assertions.assertFalse(isTicking(sandboxTicking));
        Assertions.assertTrue(Slimefun.getTickerTask().isDeletedSoon(queued));
        Assertions.assertFalse(Slimefun.getTickerTask().isDeletedSoon(sandboxTicking));
    }

    @Test
    void failedCloseKeepsSandboxDataAwayFromRealBackend() {
        AtomicReference<World> sandboxWorld = new AtomicReference<>();
        Check writing = check("writing", Stage.MEDIUM, context -> () -> {
            World world = context.sandbox().world().world();
            sandboxWorld.set(world);
            BlockStorage.addBlockInfo(new Location(world, 0, 64, 0), "id", "SANDBOX_TEST_ITEM");
            BlockStorage.setChunkInfo(world, 0, 0, "resource", "1");
            return 0;
        });
        Findings findings = new Findings(line -> {});
        Sandbox.failCloseStepForTesting(Sandbox.BLOCK_DATA_STEP);

        new CheckRunner(Collections.singletonList(writing)).runBlocking(Stage.MEDIUM, new CheckContext(findings, Stage.MEDIUM, true), ticks -> {});
        BlockStorage.getStorage(sandboxWorld.get()).save();
        BlockStorage.saveChunks();
        Slimefun.getRegistry().getWorlds().values().forEach(BlockStorage::saveAndRemove);

        Assertions.assertTrue(findings.lines().stream().anyMatch(line -> line.startsWith("[check:sandbox-close] block data: ")), () -> findings.lines().toString());
        Assertions.assertFalse(Sandbox.isOpen());
        assertRealBackendSawNoSandbox();
        Assertions.assertThrows(IllegalStateException.class, () -> Sandbox.open(true), "a sandbox whose data is still registered refuses to open beside it");
    }

    @Test
    void saveAfterCloseStaysInMemory() {
        BlockStorage leftover;
        try (Sandbox sandbox = Sandbox.open(true)) {
            World world = sandbox.world().world();
            BlockStorage.addBlockInfo(new Location(world, 0, 64, 0), "id", "SANDBOX_TEST_ITEM");
            leftover = BlockStorage.getStorage(world);
        }
        Map<String, BlockInfoConfig> snapshot = new HashMap<>();
        snapshot.put(Sandbox.WORLD_NAME + ";Chunk;0;0", new BlockInfoConfig());

        leftover.save();
        Slimefun.getBlockStorageBackend().flushChunks(snapshot);

        assertRealBackendSawNoSandbox();
    }

    @Test
    void voidWorldKeepsItsDataInMemoryAndLeavesNothingBehind() {
        File folder = new File(Bukkit.getWorldContainer(), VoidWorld.NAME);
        World voidWorld;
        try (Sandbox sandbox = Sandbox.open(true)) {
            voidWorld = sandbox.openVoidWorld().world();
            server.getPluginManager().callEvent(new WorldLoadEvent(voidWorld));
            Location placed = new Location(voidWorld, 1, 64, 1);

            BlockStorage.addBlockInfo(placed, "id", "VOID_TEST_ITEM");
            BlockStorage.setChunkInfo(voidWorld, 0, 0, "resource", "1");
            BlockStorage.getStorage(voidWorld).save();
            BlockStorage.saveChunks();

            Assertions.assertTrue(Sandbox.covers(null, voidWorld));
            Assertions.assertTrue(folder.isDirectory());
            Assertions.assertEquals(Collections.emptyList(), sandbox.closeVoidWorld());
            Assertions.assertNull(Bukkit.getWorld(VoidWorld.NAME));
            Assertions.assertFalse(folder.exists());
        }
        Map<String, BlockInfoConfig> lateSave = new HashMap<>();
        lateSave.put(VoidWorld.NAME + ";Chunk;0;0", new BlockInfoConfig());
        Slimefun.getBlockStorageBackend().flushChunks(lateSave);

        assertRealBackendSawNoSandbox();
        Assertions.assertTrue(Slimefun.getRegistry().getChunks().keySet().stream().noneMatch(key -> key.startsWith(VoidWorld.NAME + ";")));
        Assertions.assertFalse(new File(plugin.getDataFolder(), "world-settings/" + VoidWorld.NAME + ".yml").exists());
    }

    @Test
    void closingTheSandboxRemovesAVoidWorldLeftOpen() {
        Sandbox sandbox = Sandbox.open(true);
        sandbox.openVoidWorld();

        sandbox.close();

        Assertions.assertEquals(Collections.emptyList(), sandbox.closeFailures());
        Assertions.assertNull(Bukkit.getWorld(VoidWorld.NAME));
        Assertions.assertFalse(new File(Bukkit.getWorldContainer(), VoidWorld.NAME).exists());
    }

    @Test
    void aVoidWorldTheServerWouldNotUnloadStaysIsolated() {
        refuseUnload = true;
        PlayerMock player = server.addPlayer();
        World start = player.getWorld();
        World voidWorld;
        try (Sandbox sandbox = Sandbox.open(true)) {
            voidWorld = sandbox.openVoidWorld().world();

            List<String> failures = sandbox.closeVoidWorld();

            Assertions.assertTrue(failures.stream().anyMatch(failure -> failure.startsWith("unload: ")), failures::toString);
        }
        try (Sandbox later = Sandbox.open(true)) {
            BlockStorage.addBlockInfo(new Location(voidWorld, 1, 64, 1), "id", "VOID_TEST_ITEM");
            BlockStorage.setChunkInfo(voidWorld, 0, 0, "resource", "1");
            BlockStorage.getStorage(voidWorld).save();
            BlockStorage.saveChunks();
        }
        BlockStorage.getStorage(voidWorld).save();
        BlockStorage.saveChunks();
        player.teleport(new Location(voidWorld, 0, 64, 0));

        Assertions.assertSame(voidWorld, Bukkit.getWorld(VoidWorld.NAME));
        Assertions.assertTrue(Sandbox.covers(null, voidWorld));
        Assertions.assertSame(start, player.getWorld(), "the guard stays while the world is loaded");
        Assertions.assertTrue(new File(worldContainer, VoidWorld.NAME).isDirectory(), "the folder of a loaded world is left for start-up");
        assertRealBackendSawNoSandbox();
    }

    @Test
    void realPlayersCannotEnterTheVoidWorld() {
        PlayerMock player = server.addPlayer();
        World start = player.getWorld();

        try (Sandbox sandbox = Sandbox.open(true)) {
            World voidWorld = sandbox.openVoidWorld().world();
            player.teleport(new Location(voidWorld, 0, 64, 0));

            Assertions.assertSame(start, player.getWorld());
        }
    }

    @Test
    void voidWorldRefusesToReplaceAWorldOfItsName() {
        server.addSimpleWorld(VoidWorld.NAME);

        try (Sandbox sandbox = Sandbox.open(true)) {
            Assertions.assertThrows(IllegalStateException.class, sandbox::openVoidWorld);
            Assertions.assertFalse(sandbox.isSandboxWorld(Bukkit.getWorld(VoidWorld.NAME)));
        }
    }

    @Test
    void startupDeletesOnlyTheChecksOwnLeftoverWorld(@TempDir File container) throws IOException {
        File leftover = new File(container, VoidWorld.NAME);
        Files.createDirectories(new File(leftover, "region").toPath());
        Files.createFile(new File(leftover, VoidWorld.MARKER).toPath());
        Files.createFile(new File(leftover, "region/r.0.0.mca").toPath());

        VoidWorld.deleteLeftover(container);

        Assertions.assertFalse(leftover.exists());

        Files.createDirectories(new File(leftover, "region").toPath());
        VoidWorld.deleteLeftover(container);

        Assertions.assertTrue(leftover.isDirectory(), "a folder without the marker is not the checks' own");
    }

    @Test
    void aVoidWorldInTheDimensionsLayoutIsMarkedAndDeletedThere() throws IOException {
        server.addSimpleWorld("world");
        Files.createDirectories(new File(worldContainer, "world/dimensions").toPath());
        File dimensionFolder = new File(worldContainer, "world/dimensions/minecraft/" + VoidWorld.NAME);

        try (Sandbox sandbox = Sandbox.open(true)) {
            sandbox.openVoidWorld();
            Assertions.assertTrue(new File(dimensionFolder, VoidWorld.MARKER).isFile());
            Files.createDirectories(new File(dimensionFolder, "data").toPath());
            Files.createFile(new File(dimensionFolder, "data/world_border.dat").toPath());

            Assertions.assertEquals(Collections.emptyList(), sandbox.closeVoidWorld());
        }

        Assertions.assertFalse(dimensionFolder.exists());
        Assertions.assertFalse(new File(worldContainer, VoidWorld.NAME).exists());
    }

    @Test
    void anUnmarkedDimensionFolderOfThatNameRefusesTheVoidWorldAndIsKept() throws IOException {
        server.addSimpleWorld("world");
        File dimensionFolder = new File(worldContainer, "world/dimensions/minecraft/" + VoidWorld.NAME);
        Files.createDirectories(new File(dimensionFolder, "data").toPath());

        try (Sandbox sandbox = Sandbox.open(true)) {
            Assertions.assertThrows(IllegalStateException.class, sandbox::openVoidWorld);
        }
        VoidWorld.deleteLeftover(worldContainer);

        Assertions.assertTrue(new File(dimensionFolder, "data").isDirectory());
    }

    @Test
    void aMarkedDimensionFolderAnEarlierRunLeftIsDeletedAtStartup() throws IOException {
        server.addSimpleWorld("world");
        File dimensionFolder = new File(worldContainer, "world/dimensions/minecraft/" + VoidWorld.NAME);
        Files.createDirectories(new File(dimensionFolder, "data").toPath());
        Files.createFile(new File(dimensionFolder, VoidWorld.MARKER).toPath());

        VoidWorld.deleteLeftover(worldContainer);

        Assertions.assertFalse(dimensionFolder.exists());
    }

    @Test
    void protectionPluginsNeverSeeTheSandbox() throws ReflectiveOperationException {
        ProtectionManager protection = new ProtectionManager(plugin);
        List<String> logged = new ArrayList<>();
        protection.registerLogger(new ProtectionLogger() {
            @Override
            public void load() {}

            @Override
            public String getName() {
                return "RecordingLogger";
            }

            @Override
            public void logAction(OfflinePlayer player, Block block, Interaction action) {
                logged.add(block.getWorld().getName());
            }
        });
        Plugin denyAll = MockBukkit.createMockPlugin("DenyAll");
        protection.registerModule(Bukkit.getPluginManager(), "DenyAll", owner -> new DenyAllModule(denyAll));
        installProtectionManager(protection);
        World realWorld = server.addSimpleWorld("real_world");
        Player realPlayer = server.addPlayer("RealPlayer");
        Block realBlock = realWorld.getBlockAt(1, 64, 1);

        try (Sandbox sandbox = Sandbox.open(true)) {
            Player sandboxPlayer = sandbox.join("Tester", "en_us").player();
            Block sandboxBlock = sandbox.world().blockAt(1, 64, 1);

            Protections.logAction(sandboxPlayer, sandboxBlock, Interaction.PLACE_BLOCK);
            Protections.logAction(realPlayer, sandboxBlock, Interaction.BREAK_BLOCK);
            Protections.logAction(sandboxPlayer, realBlock, Interaction.BREAK_BLOCK);
            Assertions.assertTrue(Protections.hasPermission(sandboxPlayer, sandboxBlock, Interaction.INTERACT_BLOCK));
            Assertions.assertTrue(Protections.hasPermission(sandboxPlayer, sandboxBlock.getLocation(), Interaction.INTERACT_BLOCK));
            Assertions.assertTrue(Protections.hasPermission(sandboxPlayer, realBlock, Interaction.INTERACT_BLOCK));
            Assertions.assertTrue(Protections.hasPermission(realPlayer, sandboxBlock, Interaction.INTERACT_BLOCK));
            Assertions.assertTrue(logged.isEmpty(), () -> "a protection logger saw the sandbox: " + logged);

            Protections.logAction(realPlayer, realBlock, Interaction.PLACE_BLOCK);
            Assertions.assertEquals(Collections.singletonList("real_world"), logged, "real players in real worlds are still logged");
            Assertions.assertFalse(Protections.hasPermission(realPlayer, realBlock, Interaction.INTERACT_BLOCK), "real players in real worlds are still asked");
        }
    }

    @Test
    void universalMenusStayOutOfTheSandbox() {
        String id = "SANDBOX_UNIVERSAL_TEST";
        universalPreset(id);
        World realWorld = server.addSimpleWorld("real_world");
        BlockStorage.getOrCreate(realWorld);

        try (Sandbox sandbox = Sandbox.open(true)) {
            Location placed = new Location(sandbox.world().world(), 0, 64, 0);

            BlockStorage.addBlockInfo(placed, "id", id);

            Assertions.assertNull(BlockStorage.checkID(placed));
            Assertions.assertFalse(BlockStorage.hasUniversalInventory(id));
            Assertions.assertTrue(Sandbox.refusesUniversalMenu(placed, id));
            Assertions.assertTrue(sandbox.gaps().recorded().contains("UniversalBlockMenu(" + id + ")"), () -> sandbox.gaps().recorded().toString());
            Assertions.assertFalse(Sandbox.refusesUniversalMenu(new Location(realWorld, 0, 64, 0), id));
        }

        BlockStorage.addBlockInfo(new Location(realWorld, 0, 64, 0), "id", id);
        Assertions.assertTrue(BlockStorage.hasUniversalInventory(id), "a real world still gets its universal menu");
    }

    private void assertRealBackendSawNoSandbox() {
        Assertions.assertTrue(realBackend.calls().stream().noneMatch(call -> call.contains(Sandbox.WORLD_NAME) || call.contains(VoidWorld.NAME)), () -> "real backend saw the sandbox: " + realBackend.calls());
    }

    private static boolean isTicking(Location location) {
        for (Set<Location> locations : Slimefun.getTickerTask().getLocations().values()) {
            for (Location candidate : locations) {
                if (candidate == location) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void installProtectionManager(ProtectionManager protection) throws ReflectiveOperationException {
        Field field = IntegrationsManager.class.getDeclaredField("protectionManager");
        field.setAccessible(true);
        field.set(Slimefun.getIntegrations(), protection);
    }

    private static void universalPreset(String id) {
        new BlockMenuPreset(id, "Universal", true) {
            @Override
            public void init() {
                addItem(0, new ItemStack(Material.STONE));
            }

            @Override
            public boolean canOpen(Block block, Player player) {
                return true;
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                return new int[0];
            }
        };
    }

    /** A location whose world is unloaded and collected, which from 1.14 makes {@link Location#getWorld()} throw. */
    private static final class UnloadableLocation extends Location {

        private boolean unloaded;

        UnloadableLocation(World world, double x, double y, double z) {
            super(world, x, y, z);
        }

        void unload() {
            unloaded = true;
        }

        @Override
        public World getWorld() {
            if (unloaded) {
                throw new IllegalArgumentException("World unloaded");
            }
            return super.getWorld();
        }
    }

    private static final class DenyAllModule implements ProtectionModule {

        private final Plugin owner;

        DenyAllModule(Plugin owner) {
            this.owner = owner;
        }

        @Override
        public void load() {}

        @Override
        public Plugin getPlugin() {
            return owner;
        }

        @Override
        public boolean hasPermission(OfflinePlayer player, Location location, Interaction action) {
            return false;
        }
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
