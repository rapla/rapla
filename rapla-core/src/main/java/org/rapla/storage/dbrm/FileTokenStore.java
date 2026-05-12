package org.rapla.storage.dbrm;

import org.rapla.logger.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;

/**
 * Dotfile-backed token store at {@code ~/.rapla/tokens.json} with mode 0600
 * on POSIX systems. Matches the {@code ~/.aws/credentials} convention.
 * Falls back gracefully when the home directory isn't writable.
 *
 * <p>No encryption: the contents are RSA-signed JWTs (~700 bytes) with a
 * 30-day TTL, not passwords. App-level encryption with a system-derived
 * key would be defeated by any same-user attacker who can read the file
 * AND derive the same key, so it provides no real protection. OS-layer
 * tools (file permissions, full-disk encryption) handle the realistic
 * threats. See PRD 029 Open Question 10 for the full reasoning.
 */
public final class FileTokenStore implements TokenStore
{
    private static final String FILE_BODY_PREFIX = "{\"refreshToken\":\"";
    private static final String FILE_BODY_SUFFIX = "\"}\n";

    private final Path tokenFile;
    private final Logger logger;

    public FileTokenStore(Logger logger)
    {
        this(Path.of(System.getProperty("user.home", "."), ".rapla", "tokens.json"), logger);
    }

    FileTokenStore(Path tokenFile, Logger logger)
    {
        this.tokenFile = tokenFile;
        this.logger = logger;
    }

    @Override
    public Optional<String> read()
    {
        try
        {
            if (!Files.exists(tokenFile)) return Optional.empty();
            String content = Files.readString(tokenFile, StandardCharsets.UTF_8);
            return extractRefreshToken(content);
        }
        catch (Throwable t)
        {
            if (logger != null) logger.debug("file token-store read failed: " + t.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void tryWrite(String token)
    {
        if (token == null || token.isEmpty()) return;
        try
        {
            Path parent = tokenFile.getParent();
            if (parent != null) Files.createDirectories(parent);
            String content = FILE_BODY_PREFIX + token + FILE_BODY_SUFFIX;
            Files.writeString(tokenFile, content, StandardCharsets.UTF_8);
            try
            {
                // 0600 — readable + writable by owner only
                Files.setPosixFilePermissions(tokenFile, PosixFilePermissions.fromString("rw-------"));
            }
            catch (UnsupportedOperationException posixUnsupported)
            {
                // Windows / non-POSIX filesystem; ACLs / NTFS-default cover the typical threat model.
            }
        }
        catch (Throwable t)
        {
            if (logger != null) logger.warn("file token-store write failed (token NOT persisted): " + t.getMessage());
        }
    }

    @Override
    public void tryClear()
    {
        try
        {
            Files.deleteIfExists(tokenFile);
        }
        catch (Throwable t)
        {
            if (logger != null) logger.warn("file token-store clear failed: " + t.getMessage());
        }
    }

    /**
     * Minimal JSON extraction — the file shape is fixed and rapla-owned,
     * so we avoid pulling in a JSON dependency on the core module.
     * Returns empty on any parse anomaly.
     */
    private static Optional<String> extractRefreshToken(String content)
    {
        if (content == null) return Optional.empty();
        int idx = content.indexOf("\"refreshToken\"");
        if (idx < 0) return Optional.empty();
        int colon = content.indexOf(':', idx);
        if (colon < 0) return Optional.empty();
        int firstQuote = content.indexOf('"', colon + 1);
        if (firstQuote < 0) return Optional.empty();
        int closingQuote = content.indexOf('"', firstQuote + 1);
        if (closingQuote < 0) return Optional.empty();
        String value = content.substring(firstQuote + 1, closingQuote);
        if (value.isEmpty()) return Optional.empty();
        return Optional.of(value);
    }
}
