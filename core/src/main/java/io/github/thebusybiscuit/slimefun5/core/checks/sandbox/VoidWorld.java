package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.generator.ChunkGenerator;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

import me.mrCookieSlime.Slimefun.api.BlockStorage;

/**
 * A real, empty world for the checks that need real blocks with real tile entities, such as a vanilla
 * container a Slimefun machine is built on. It exists only while a check uses it: closing it unloads it
 * without saving and deletes its folder, and {@link #deleteLeftover} removes a folder a crash left behind.
 * No real player can enter it.
 *
 * @implNote Only an open {@link Sandbox} creates one, so its block data is routed to memory from before the
 *           server loads it. A folder is deleted only when it carries {@link #MARKER}, written before the
 *           server creates the world, so a world of the same name that the checks did not make is never
 *           touched. Up to 1.21 a world lives in {@code <container>/<name>}; from 26.x the server keeps it in
 *           {@code <main world>/dimensions/minecraft/<name>}, so both places are checked, marked and deleted.
 */
public final class VoidWorld implements AutoCloseable {

    public static final String NAME = "slimefun_checks";

    static final String MARKER = "slimefun-checks-world";

    private static final int SPAWN_Y = 64;
    private static final Set<UUID> MADE_BY_CHECKS = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean WARNED_UNTRUSTED_FOLDER = new AtomicBoolean();

    private final World world;
    private final List<File> folders;
    private final Guard guard;
    private final List<String> closeFailures = new ArrayList<>();
    private boolean closed;
    private boolean unloaded;

    private VoidWorld(World world, List<File> folders, Guard guard) {
        this.world = world;
        this.folders = folders;
        this.guard = guard;
    }

    /**
     * Creates the world and loads it. Main thread only, and only from {@link Sandbox#openVoidWorld()}.
     *
     * @throws IllegalStateException when a world or folder of this name already exists, or the server would
     *                               not create it; nothing is left behind then
     */
    @Nonnull
    static VoidWorld create() {
        if (Bukkit.getWorld(NAME) != null) {
            throw new IllegalStateException("A world named " + NAME + " is already loaded, so the checks cannot make their own");
        }
        File container = Bukkit.getWorldContainer();
        for (File candidate : candidateFolders(container)) {
            if (candidate.exists()) {
                throw new IllegalStateException("The folder " + candidate + " already exists, so the checks cannot make their world there");
            }
        }
        List<File> folders = foldersTheServerWillUse(container);
        for (File folder : folders) {
            writeMarker(folder);
        }
        Guard guard = new Guard();
        Bukkit.getPluginManager().registerEvents(guard, Slimefun.instance());
        try {
            World world = newCreator().createWorld();
            if (world == null) {
                throw new IllegalStateException("The server did not create the world " + NAME);
            }
            MADE_BY_CHECKS.add(world.getUID());
            markRealFolder(world, folders);
            settle(world);
            return new VoidWorld(world, folders, guard);
        } catch (RuntimeException | LinkageError failure) {
            if (discardFailedCreation(folders)) {
                HandlerList.unregisterAll(guard);
            }
            throw failure;
        }
    }

    /**
     * Whether the checks created this world, so it stays away from the real block storage even while a
     * refused unload keeps it loaded.
     */
    static boolean isMadeByChecks(@Nonnull World world) {
        return MADE_BY_CHECKS.contains(world.getUID());
    }

    @Nonnull
    public World world() {
        return world;
    }

    /** Whether the world is still loaded, which is when its name must stay routed to memory. */
    boolean isLoaded() {
        return !unloaded;
    }

    /**
     * Unloads the world without saving it, forgets everything Slimefun held for it and deletes its folder,
     * undoing each on its own so one failure does not leave the rest behind. A world the server would not unload
     * keeps its guard, and its folder is left for start-up to delete. Main thread only.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        attempt("players", this::moveRealPlayersOut);
        attempt("ticker", () -> Slimefun.getTickerTask().forgetWorld(world));
        unloaded = attempt("unload", this::unload);
        if (unloaded) {
            HandlerList.unregisterAll(guard);
            attempt("block data", VoidWorld::purgeBlockData);
            attempt("world settings", () -> Slimefun.getWorldSettingsService().unload(world));
            for (File folder : folders) {
                attempt("folder", () -> deleteMarkedFolder(folder));
            }
        }
    }

    /** One line per step that failed while closing, such as {@code unload: java.lang.IllegalStateException}. */
    @Nonnull
    public List<String> closeFailures() {
        return new ArrayList<>(closeFailures);
    }

    /**
     * Deletes the folder of a void world that a crash left behind. Called at start-up, before any check runs;
     * a folder without the checks' marker, or a world of this name the server has loaded, is left alone.
     */
    public static void deleteLeftover(@Nonnull File worldContainer) {
        for (File folder : candidateFolders(worldContainer)) {
            deleteLeftoverFolder(folder);
        }
    }

    /** Every place a world of this name may live: the container, and from 26.x the main world's dimensions. */
    @Nonnull
    static List<File> candidateFolders(@Nonnull File worldContainer) {
        List<File> candidates = new ArrayList<>();
        candidates.add(new File(worldContainer, NAME));
        File dimensions = dimensionsFolder(worldContainer);
        if (dimensions != null) {
            candidates.add(dimensionFolder(dimensions));
        }
        return candidates;
    }

    /** The candidates this server's layout uses, marked before the server creates the world. */
    private static List<File> foldersTheServerWillUse(File worldContainer) {
        List<File> folders = new ArrayList<>();
        folders.add(new File(worldContainer, NAME));
        File dimensions = dimensionsFolder(worldContainer);
        if (dimensions != null && dimensions.isDirectory()) {
            folders.add(dimensionFolder(dimensions));
        }
        return folders;
    }

    @Nullable
    private static File dimensionsFolder(File worldContainer) {
        List<World> worlds = Bukkit.getWorlds();
        return worlds.isEmpty() ? null : new File(new File(worldContainer, worlds.get(0).getName()), "dimensions");
    }

    private static File dimensionFolder(File dimensions) {
        return new File(new File(dimensions, "minecraft"), NAME);
    }

    /**
     * @implNote A world the server keeps somewhere neither candidate names is still marked and deleted; a server
     *           that cannot name its world folder, such as a test server, keeps to the candidates. Our own marker
     *           proves nothing about a folder the server named after creating the world, so only a folder that
     *           can be the checks' world alone is taken.
     */
    private static void markRealFolder(World world, List<File> folders) {
        File real;
        try {
            real = world.getWorldFolder();
        } catch (RuntimeException unknown) {
            Slimefun.logger().log(Level.FINE, "This server cannot name the checks' world folder", unknown);
            return;
        }
        for (File folder : folders) {
            if (canonical(folder).equals(canonical(real))) {
                return;
            }
        }
        if (!isOnlyTheChecksWorld(world, real)) {
            if (WARNED_UNTRUSTED_FOLDER.compareAndSet(false, true)) {
                Slimefun.logger().log(Level.WARNING, "The server keeps the checks'' world in {0}, which may hold other worlds; it is never marked or deleted", real);
            }
            return;
        }
        writeMarker(real);
        folders.add(real);
    }

    /** Whether the folder is named for the checks' world and is not the container, another world's, or above one. */
    private static boolean isOnlyTheChecksWorld(World world, File folder) {
        Path candidate = canonical(folder);
        if (!NAME.equals(folder.getName()) || candidate.equals(canonical(Bukkit.getWorldContainer()))) {
            return false;
        }
        for (World other : Bukkit.getWorlds()) {
            if (other.equals(world)) {
                continue;
            }
            File otherFolder;
            try {
                otherFolder = other.getWorldFolder();
            } catch (RuntimeException unknown) {
                continue;
            }
            if (canonical(otherFolder).startsWith(candidate)) {
                return false;
            }
        }
        return true;
    }

    private static Path canonical(File folder) {
        try {
            return folder.getCanonicalFile().toPath();
        } catch (IOException unresolvable) {
            return folder.toPath().toAbsolutePath().normalize();
        }
    }

    private static boolean isMarked(File folder) {
        return new File(folder, MARKER).isFile();
    }

    private static void deleteMarkedFolder(File folder) {
        if (folder.exists() && !isMarked(folder)) {
            throw new IllegalStateException("the folder " + folder + " lost the checks' marker, so it is left alone");
        }
        deleteFolder(folder);
    }

    private static void deleteLeftoverFolder(File folder) {
        if (!folder.isDirectory()) {
            return;
        }
        if (Bukkit.getWorld(NAME) != null || !isMarked(folder)) {
            Slimefun.logger().log(Level.WARNING, "Leaving the folder {0} alone: the checks did not make it, or it is loaded", folder);
            return;
        }
        try {
            deleteFolder(folder);
            Slimefun.logger().log(Level.INFO, "Deleted the checks'' world folder {0} that an earlier run left behind", folder);
        } catch (RuntimeException failure) {
            Slimefun.logger().log(Level.WARNING, failure, () -> "Could not delete the checks' world " + folder + " that an earlier run left behind");
        }
    }

    private static WorldCreator newCreator() {
        WorldCreator creator = new WorldCreator(NAME).environment(World.Environment.NORMAL).generateStructures(false).generator(emptyGenerator());
        keepNoSpawnLoaded(creator);
        return creator;
    }

    /**
     * @implNote Paper's {@code keepSpawnLoaded} spares generating the spawn area while the world is created;
     *           elsewhere it is generated and then released by {@link #settle}.
     */
    private static void keepNoSpawnLoaded(WorldCreator creator) {
        for (Method method : creator.getClass().getMethods()) {
            if (method.getName().equals("keepSpawnLoaded") && method.getParameterCount() == 1) {
                try {
                    Object no = method.getParameterTypes()[0].getField("FALSE").get(null);
                    method.invoke(creator, no);
                } catch (ReflectiveOperationException | RuntimeException absent) {
                    Slimefun.logger().log(Level.FINE, "This server cannot skip the spawn area of a new world", absent);
                }
                return;
            }
        }
    }

    private static void settle(World world) {
        world.setAutoSave(false);
        try {
            world.setKeepSpawnInMemory(false);
        } catch (UnsupportedOperationException removed) {
            Slimefun.logger().log(Level.FINE, "This server keeps no spawn area to release", removed);
        }
        BlockStorage.getOrCreate(world);
    }

    /**
     * @implNote From 1.17.1 a generator that overrides nothing generates nothing; before that the server asks
     *           {@code generateChunkData}, which throws unless overridden.
     */
    private static ChunkGenerator emptyGenerator() {
        try {
            ChunkGenerator.class.getMethod("shouldGenerateNoise");
            return new EmptyGenerator();
        } catch (NoSuchMethodException legacy) {
            return new LegacyEmptyGenerator();
        }
    }

    private void moveRealPlayersOut() {
        List<Player> inside = world.getPlayers();
        if (inside.isEmpty()) {
            return;
        }
        Location away = Bukkit.getWorlds().get(0).getSpawnLocation();
        for (Player player : inside) {
            player.teleport(away);
        }
    }

    private void unload() {
        if (!Bukkit.unloadWorld(world, false)) {
            throw new IllegalStateException("the server refused to unload " + NAME);
        }
    }

    private static void purgeBlockData() {
        Slimefun.getRegistry().getWorlds().remove(NAME);
        Slimefun.getRegistry().getChunks().keySet().removeIf(key -> key.startsWith(NAME + ';'));
    }

    private boolean attempt(String step, Runnable undo) {
        try {
            undo.run();
            return true;
        } catch (RuntimeException | LinkageError failure) {
            closeFailures.add(step + ": " + failure);
            Slimefun.logger().log(Level.SEVERE, failure, () -> "The checks' world could not undo its " + step);
            return false;
        }
    }

    private static void writeMarker(File folder) {
        try {
            Files.createDirectories(folder.toPath());
            if (!isMarked(folder)) {
                Files.createFile(new File(folder, MARKER).toPath());
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Could not prepare the folder " + folder, failure);
        }
    }

    /**
     * @return whether no part of the world is still loaded; its folder is deleted only then, and is otherwise
     *         left for start-up to delete
     */
    private static boolean discardFailedCreation(List<File> folders) {
        World partial = Bukkit.getWorld(NAME);
        try {
            if (partial != null) {
                MADE_BY_CHECKS.add(partial.getUID());
                if (!Bukkit.unloadWorld(partial, false)) {
                    Slimefun.logger().log(Level.WARNING, "The checks' world failed to load and the server would not unload it; start-up removes it next time");
                    return false;
                }
            }
            for (File folder : folders) {
                deleteMarkedFolder(folder);
            }
        } catch (RuntimeException | LinkageError cleanup) {
            Slimefun.logger().log(Level.WARNING, cleanup, () -> "Could not remove the checks' world after it failed to load; start-up removes it next time");
        }
        return Bukkit.getWorld(NAME) == null;
    }

    static void deleteFolder(@Nonnull File folder) {
        if (!folder.exists()) {
            return;
        }
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(folder.toPath())) {
            paths = walk.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        } catch (IOException failure) {
            throw new IllegalStateException("Could not list " + folder, failure);
        }
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException failure) {
                throw new IllegalStateException("Could not delete " + path, failure);
            }
        }
    }

    /** Keeps every real player out: only sandbox players, who never move, belong in the world. */
    public static final class Guard implements Listener {

        private Guard() {}

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onTeleport(PlayerTeleportEvent event) {
            if (isEntering(event.getPlayer(), event.getTo())) {
                event.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onPortal(PlayerPortalEvent event) {
            if (isEntering(event.getPlayer(), event.getTo())) {
                event.setCancelled(true);
            }
        }

        /** Catches a move no cancellable event announced. */
        @EventHandler(priority = EventPriority.HIGHEST)
        public void onWorldChange(PlayerChangedWorldEvent event) {
            Player player = event.getPlayer();
            if (isEntering(player, player.getLocation())) {
                player.teleport(event.getFrom().getSpawnLocation());
            }
        }

        private static boolean isEntering(Player player, @Nullable Location destination) {
            if (destination == null || Sandbox.isSandboxPlayer(player.getUniqueId())) {
                return false;
            }
            World target = Sandbox.worldOf(destination);
            return target != null && NAME.equals(target.getName());
        }
    }

    /** Generates no blocks, and spawns at a fixed point so the server never searches the void for one. */
    static class EmptyGenerator extends ChunkGenerator {

        @Override
        public boolean canSpawn(World world, int x, int z) {
            return true;
        }

        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, SPAWN_Y, 0.5);
        }
    }

    static final class LegacyEmptyGenerator extends EmptyGenerator {

        @Override
        public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biomes) {
            return createChunkData(world);
        }
    }
}
