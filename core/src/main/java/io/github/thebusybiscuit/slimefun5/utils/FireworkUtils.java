package io.github.thebusybiscuit.slimefun5.utils;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nonnull;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.meta.FireworkMeta;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.PdcCompat;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.VersionedEntityType;

/**
 * This is a simple utility class for spawning random and colorful {@link Firework} rockets.
 *
 * @author TheBusyBiscuit
 *
 */
public final class FireworkUtils {

    // @formatter:off
    private static final Color[] COLORS = {
        Color.AQUA, Color.BLACK, Color.BLUE, Color.FUCHSIA,
        Color.GRAY, Color.GREEN, Color.LIME, Color.MAROON,
        Color.NAVY, Color.OLIVE, Color.ORANGE, Color.PURPLE,
        Color.RED, Color.SILVER, Color.TEAL, Color.WHITE, Color.YELLOW
    };
    // @formatter:on

    private static final EntityType firework = VersionedEntityType.FIREWORK;

    /**
     * Marks a {@link Firework} spawned by this class so {@code FireworksListener} can recognize it without
     * relying on a display name (which is never actually shown to players and would otherwise need to be
     * baked in whichever language the launching player happens to have).
     */
    private static NamespacedKey slimefunFireworkKey;

    private FireworkUtils() {}

    @Nonnull
    private static NamespacedKey slimefunFireworkKey() {
        if (slimefunFireworkKey == null) {
            slimefunFireworkKey = new NamespacedKey(Slimefun.instance(), "slimefun_firework");
        }

        return slimefunFireworkKey;
    }

    /**
     * @return whether {@code firework} was spawned by this class (used to exempt it from damaging players).
     */
    public static boolean isSlimefunFirework(@Nonnull Firework firework) {
        return PdcCompat.hasByte(firework, slimefunFireworkKey());
    }

    public static void launchFirework(@Nonnull Location l, @Nonnull Color color) {
        createFirework(l, color);
    }

    public static @Nonnull Firework createFirework(@Nonnull Location l, @Nonnull Color color) {
        Firework fw = (Firework) l.getWorld().spawnEntity(l, firework);
        FireworkMeta meta = fw.getFireworkMeta();

        FireworkEffect effect = getRandomEffect(ThreadLocalRandom.current(), color);
        meta.addEffect(effect);
        meta.setPower(ThreadLocalRandom.current().nextInt(2) + 1);
        fw.setFireworkMeta(meta);
        PdcCompat.setByte(fw, slimefunFireworkKey(), (byte) 1);

        return fw;
    }

    public static void launchRandom(@Nonnull Entity n, int amount) {
        Random random = ThreadLocalRandom.current();

        for (int i = 0; i < amount; i++) {
            Location l = n.getLocation().clone();
            l.setX(l.getX() + random.nextInt(amount * 2) - amount);
            l.setZ(l.getZ() + random.nextInt(amount * 2) - amount);

            launchFirework(l, getRandomColor());
        }
    }

    /**
     * This returns a randomized {@link FireworkEffect} using the given {@link Color}.
     * 
     * @param random
     *            The {@link Random} instance to use
     * @param color
     *            The {@link Color} of this {@link FireworkEffect}
     * 
     * @return A randomized {@link FireworkEffect}
     */
    public static @Nonnull FireworkEffect getRandomEffect(@Nonnull Random random, @Nonnull Color color) {
        // @formatter:off
        return FireworkEffect.builder()
                .flicker(random.nextBoolean())
                .withColor(color)
                .with(random.nextBoolean() ? Type.BALL : Type.BALL_LARGE)
                .trail(random.nextBoolean()
        ).build();
        // @formatter:on
    }

    /**
     * This returns a random {@link Color} for our {@link FireworkEffect}.
     * 
     * @return A random {@link Color}
     */
    private static @Nonnull Color getRandomColor() {
        return COLORS[ThreadLocalRandom.current().nextInt(COLORS.length)];
    }
}

