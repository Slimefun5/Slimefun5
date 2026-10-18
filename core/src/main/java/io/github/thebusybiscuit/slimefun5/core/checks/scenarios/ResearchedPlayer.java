package io.github.thebusybiscuit.slimefun5.core.checks.scenarios;

import java.util.Optional;

import javax.annotation.Nonnull;

import io.github.thebusybiscuit.slimefun5.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun5.api.researches.Research;
import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.Sandbox;
import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.SandboxPlayer;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/** A sandbox player whose Slimefun profile is loaded and has every research, so no item refuses them. */
final class ResearchedPlayer {

    /** Profiles load on Slimefun's own thread, so the wait is real time, not ticks. */
    private static final long PROFILE_TIMEOUT_MILLIS = 10_000;

    private final SandboxPlayer player;
    private final long deadline;
    private boolean researched;

    private ResearchedPlayer(SandboxPlayer player) {
        this.player = player;
        this.deadline = System.currentTimeMillis() + PROFILE_TIMEOUT_MILLIS;
    }

    /** Joins the player and starts loading their profile; they are usable once {@link #isReady()} says so. */
    @Nonnull
    static ResearchedPlayer join(@Nonnull Sandbox sandbox, @Nonnull String name, @Nonnull String locale) {
        ResearchedPlayer joined = new ResearchedPlayer(sandbox.join(name, locale));
        PlayerProfile.request(joined.player.player());
        return joined;
    }

    @Nonnull
    SandboxPlayer player() {
        return player;
    }

    /**
     * @return whether the profile has loaded, granting every research the first time it has
     * @throws IllegalStateException when the profile did not load in time
     */
    boolean isReady() {
        if (researched) {
            return true;
        }
        Optional<PlayerProfile> profile = PlayerProfile.find(player.player());
        if (!profile.isPresent()) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("The profile of " + player.player().getName() + " did not load in " + PROFILE_TIMEOUT_MILLIS + " ms");
            }
            return false;
        }
        for (Research research : Slimefun.getRegistry().getResearches()) {
            profile.get().setResearched(research, true);
        }
        researched = true;
        return true;
    }
}
