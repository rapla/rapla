package org.rapla.storage.dbrm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Dotfile-backed token store at {@code ~/.rapla/tokens.json} with mode 0600
 * on POSIX systems. Matches the {@code ~/.aws/credentials} convention.
 * Falls back gracefully when the home directory isn't writable.
 *
 * <p>The file is a flat JSON object (see {@link TokenStoreCodec}) holding the
 * refresh token plus the non-secret login preferences (language, sign-in
 * method). No encryption: the contents are RSA-signed JWTs (~700 bytes) with
 * a 30-day TTL, not passwords. App-level encryption with a system-derived key
 * would be defeated by any same-user attacker who can read the file AND
 * derive the same key, so it provides no real protection. OS-layer tools
 * (file permissions, full-disk encryption) handle the realistic threats.
 * See PRD 029 Open Question 10 for the full reasoning.
 */
public final class FileTokenStore implements TokenStore
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FileTokenStore.class);
    private static final String KEY_REFRESH_TOKEN = "refreshToken";

    private final Path tokenFile;

    public FileTokenStore()
    {
        this(Path.of(System.getProperty("user.home", "."), ".rapla", "tokens.json"));
    }

    FileTokenStore(Path tokenFile)
    {
        this.tokenFile = tokenFile;
    }

    @Override
    public Optional<String> read()
    {
        return value(KEY_REFRESH_TOKEN);
    }

    @Override
    public void tryWrite(String token)
    {
        if (token == null || token.isEmpty()) return;
        Map<String, String> doc = load();
        doc.put(KEY_REFRESH_TOKEN, token);
        save(doc);
    }

    @Override
    public void tryClear()
    {
        Map<String, String> doc = load();
        boolean hadToken = doc.remove(KEY_REFRESH_TOKEN) != null;
        if (!hadToken && doc.isEmpty()) return;
        if (doc.isEmpty())
        {
            try
            {
                Files.deleteIfExists(tokenFile);
            }
            catch (Throwable t)
            {
                LOGGER.warn("file token-store clear failed: {}", t.getMessage());
            }
        }
        else
        {
            // Token removed, but language / login-method preferences remain —
            // rewrite without the token so the next launch still defaults well.
            save(doc);
        }
    }

    @Override
    public Optional<String> readPref(String key)
    {
        return value(key);
    }

    @Override
    public void tryWritePref(String key, String value)
    {
        if (key == null || key.isEmpty()) return;
        Map<String, String> doc = load();
        if (value == null || value.isEmpty())
        {
            doc.remove(key);
        }
        else
        {
            doc.put(key, value);
        }
        save(doc);
    }

    private Optional<String> value(String key)
    {
        String v = load().get(key);
        return (v == null || v.isEmpty()) ? Optional.empty() : Optional.of(v);
    }

    private Map<String, String> load()
    {
        try
        {
            if (!Files.exists(tokenFile)) return new LinkedHashMap<>();
            return TokenStoreCodec.parse(Files.readString(tokenFile, StandardCharsets.UTF_8));
        }
        catch (Throwable t)
        {
            LOGGER.debug("file token-store read failed: {}", t.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private void save(Map<String, String> doc)
    {
        try
        {
            Path parent = tokenFile.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(tokenFile, TokenStoreCodec.toJson(doc) + "\n", StandardCharsets.UTF_8);
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
            LOGGER.warn("file token-store write failed (NOT persisted): {}", t.getMessage());
        }
    }
}
