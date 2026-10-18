package io.github.thebusybiscuit.slimefun5.integrations;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;

import io.github.bakedlibs.dough.protection.Interaction;
import io.github.bakedlibs.dough.protection.ProtectionManager;
import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.Sandbox;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * Core's only way into the protection plugins behind {@link ProtectionManager}. For the checks' sandbox world
 * or a sandbox player nothing is logged and everything is allowed, so a check never writes a row into a
 * block logger or asks a claim plugin about a world it does not know.
 */
public final class Protections {

    private Protections() {}

    public static boolean hasPermission(@Nullable OfflinePlayer player, @Nonnull Block block, @Nonnull Interaction interaction) {
        if (Sandbox.covers(player, block.getWorld())) {
            return true;
        }
        return Slimefun.getProtectionManager().hasPermission(player, block, interaction);
    }

    public static boolean hasPermission(@Nullable OfflinePlayer player, @Nonnull Location location, @Nonnull Interaction interaction) {
        if (Sandbox.covers(player, location)) {
            return true;
        }
        return Slimefun.getProtectionManager().hasPermission(player, location, interaction);
    }

    public static void logAction(@Nullable OfflinePlayer player, @Nonnull Block block, @Nonnull Interaction interaction) {
        if (Sandbox.covers(player, block.getWorld())) {
            return;
        }
        Slimefun.getProtectionManager().logAction(player, block, interaction);
    }
}
