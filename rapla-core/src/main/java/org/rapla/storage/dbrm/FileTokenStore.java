package org.rapla.storage.dbrm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Dotfile-backed token store at {@code ~/.rapla/tokens.json}, owner-only
 * ({@link OwnerOnlyFiles}). Matches the {@code ~/.aws/credentials} convention.
 * Falls back gracefully when the home directory isn't writable.
 *
 * <p>The file is a flat JSON object (see {@link TokenStoreCodec}) holding one
 * refresh token per server origin ({@code refreshToken@<origin>}) plus the
 * non-secret login preferences (language, sign-in method). The origin is
 * resolved from the supplier at every call, so a token is only ever returned
 * for the origin it was written for; an unkeyed {@code refreshToken} entry from
 * the earlier single-slot format is ignored and dropped by the next write.
 * No encryption: the contents are RSA-signed JWTs (~700 bytes) with
 * a 21-day TTL, not passwords. App-level encryption with a system-derived key
 * would be defeated by any same-user attacker who can read the file AND
 * derive the same key, so it provides no real protection. OS-layer tools
 * (file permissions, full-disk encryption) handle the realistic threats.
 * See PRD 029 Open Question 10 for the full reasoning.
 *
 * <p>If file or directory belong to someone else or are accessible by other
 * principals, the file is neither read nor written: tokens and preferences stay
 * in memory and {@link #failure()} names the attempted path.
 */
public final class FileTokenStore implements TokenStore
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FileTokenStore.class);
    private static final String KEY_REFRESH_TOKEN = "refreshToken";

    private final Path tokenFile;
    private final Supplier<String> serverUrl;
    private volatile Map<String, String> memory = new LinkedHashMap<>();
    private volatile Failure failure;

    public FileTokenStore(Supplier<String> serverUrl)
    {
        this(Path.of(System.getProperty("user.home", "."), ".rapla", "tokens.json"), serverUrl);
    }

    FileTokenStore(Path tokenFile, Supplier<String> serverUrl)
    {
        this.tokenFile = tokenFile;
        this.serverUrl = serverUrl;
    }

    Path path()
    {
        return tokenFile;
    }

    @Override
    public Optional<Failure> failure()
    {
        return Optional.ofNullable(failure);
    }

    @Override
    public Optional<String> read()
    {
        String key = tokenKey();
        return key == null ? Optional.empty() : value(key);
    }

    @Override
    public void tryWrite(String token)
    {
        if (token == null || token.isEmpty()) return;
        String key = tokenKey();
        if (key == null)
        {
            LOGGER.warn("file token-store: server URL unknown, token NOT persisted");
            return;
        }
        Map<String, String> doc = load();
        doc.put(key, token);
        save(doc);
    }

    @Override
    public void tryClear()
    {
        String key = tokenKey();
        Map<String, String> doc = load();
        boolean hadToken = doc.remove(KEY_REFRESH_TOKEN) != null;
        hadToken |= key != null && doc.remove(key) != null;
        if (!hadToken && doc.isEmpty()) return;
        if (doc.isEmpty())
        {
            delete();
        }
        else
        {
            // Token removed, but language / login-method preferences (or other origins' tokens)
            // remain — rewrite without the token so the next launch still defaults well.
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

    /** Scheme, host and port (default port dropped), lower-cased; null when the URL has none. */
    private static String origin(String url)
    {
        if (url == null) return null;
        try
        {
            URL u = URI.create(url.trim()).toURL();
            String host = u.getHost();
            if (host == null || host.isEmpty()) return null;
            String port = u.getPort() < 0 || u.getPort() == u.getDefaultPort() ? "" : ":" + u.getPort();
            return u.getProtocol().toLowerCase(Locale.ROOT) + "://" + host.toLowerCase(Locale.ROOT) + port;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private String tokenKey()
    {
        String origin = origin(serverUrl.get());
        return origin == null ? null : KEY_REFRESH_TOKEN + "@" + origin;
    }

    private Optional<String> value(String key)
    {
        String v = load().get(key);
        return (v == null || v.isEmpty()) ? Optional.empty() : Optional.of(v);
    }

    private Map<String, String> load()
    {
        String problem = OwnerOnlyFiles.secure(tokenFile);
        if (problem != null)
        {
            fail(problem);
            return new LinkedHashMap<>(memory);
        }
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
        doc.remove(KEY_REFRESH_TOKEN);
        String problem = OwnerOnlyFiles.secure(tokenFile);
        if (problem != null)
        {
            fail(problem);
            memory = doc;
            return;
        }
        try
        {
            OwnerOnlyFiles.create(tokenFile);
            Files.writeString(tokenFile, TokenStoreCodec.toJson(doc) + "\n", StandardCharsets.UTF_8);
            failure = null;
        }
        catch (Throwable t)
        {
            failure = new Failure(tokenFile, String.valueOf(t.getMessage()));
            LOGGER.warn("file token-store write failed (NOT persisted): {}", t.getMessage());
        }
    }

    private void delete()
    {
        if (OwnerOnlyFiles.secure(tokenFile) != null)
        {
            memory = new LinkedHashMap<>();
            return;
        }
        try
        {
            Files.deleteIfExists(tokenFile);
        }
        catch (Throwable t)
        {
            LOGGER.warn("file token-store clear failed: {}", t.getMessage());
        }
    }

    private void fail(String reason)
    {
        Failure previous = failure;
        failure = new Failure(tokenFile, reason);
        if (previous == null || !reason.equals(previous.reason()))
        {
            LOGGER.warn("file token-store {} not used, login data stays in memory: {}", tokenFile, reason);
        }
    }
}
