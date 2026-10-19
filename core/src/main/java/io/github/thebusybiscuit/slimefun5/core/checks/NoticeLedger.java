package io.github.thebusybiscuit.slimefun5.core.checks;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;

import javax.annotation.Nonnull;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Remembers which set of findings each player was last told about, so a join notice is sent only when that
 * set changes.
 *
 * @implNote A player is forgotten when the findings clear, so the same findings coming back are a change again.
 */
final class NoticeLedger {

    private final File file;

    NoticeLedger(@Nonnull File file) {
        this.file = file;
    }

    /**
     * @return whether {@code problems} differ from what this player was last told about; the new set is recorded
     */
    synchronized boolean recordIfChanged(@Nonnull UUID player, @Nonnull Collection<String> problems) {
        YamlConfiguration ledger = YamlConfiguration.loadConfiguration(file);
        String key = player.toString();
        String fingerprint = problems.isEmpty() ? null : fingerprintOf(problems);
        if (Objects.equals(ledger.getString(key), fingerprint)) {
            return false;
        }

        ledger.set(key, fingerprint);
        save(ledger);
        return fingerprint != null;
    }

    private void save(YamlConfiguration ledger) {
        try {
            ledger.save(file);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String fingerprintOf(Collection<String> problems) {
        byte[] digest = sha256().digest(String.join("\n", new TreeSet<>(problems)).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte value : digest) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("Every Java runtime ships SHA-256", unavailable);
        }
    }
}
