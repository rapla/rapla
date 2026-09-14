package org.rapla.storage.dbrm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * Token store backed by the JNLP {@code PersistenceService} (JSR-56),
 * implemented in IcedTea-Web and inherited by OpenWebStart. Storage is
 * scoped to the rapla deployment's codebase URL — another JNLP app on
 * the same machine can't read rapla's refresh token, even though both
 * share the on-disk JNLP cache directory.
 *
 * <p>Accessed via reflection so rapla-core doesn't take a hard compile
 * dependency on {@code javax.jnlp.*} (which is only on the classpath
 * when launched under JNLP). Use {@link #tryCreate()} to instantiate;
 * it returns {@link Optional#empty()} when not running under a JNLP
 * runtime.
 */
public final class JnlpTokenStore implements TokenStore
{
    private static final Logger LOGGER = LoggerFactory.getLogger(JnlpTokenStore.class);
    private static final String TOKEN_KEY_PATH = "rapla/refresh-token";
    private static final long RESERVED_SIZE = 4096L;

    private final Object persistenceService;
    private final URL key;

    /**
     * @return a JnlpTokenStore if the JNLP {@code PersistenceService} is
     *         available, empty otherwise. Never throws.
     */
    public static Optional<TokenStore> tryCreate()
    {
        try
        {
            Class<?> sm = Class.forName("javax.jnlp.ServiceManager");
            Method lookup = sm.getMethod("lookup", String.class);
            Object basic = lookup.invoke(null, "javax.jnlp.BasicService");
            Object persistence = lookup.invoke(null, "javax.jnlp.PersistenceService");
            URL codebase = (URL) basic.getClass().getMethod("getCodeBase").invoke(basic);
            URL key = new URL(codebase, TOKEN_KEY_PATH);
            LOGGER.debug("JNLP token store available; codebase={}", codebase);
            return Optional.of(new JnlpTokenStore(persistence, key));
        }
        catch (Throwable t)
        {
            // Not running under JNLP, or JNLP API not on classpath. Expected for
            // dev runs (mvn exec:java) and plain java -jar launches.
            LOGGER.debug("JNLP token store unavailable: {}", t.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private JnlpTokenStore(Object persistenceService, URL key)
    {
        this.persistenceService = persistenceService;
        this.key = key;
    }

    private static final String KEY_REFRESH_TOKEN = "refreshToken";

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
                persistenceService.getClass()
                        .getMethod("delete", URL.class).invoke(persistenceService, key);
            }
            catch (Throwable t)
            {
                LOGGER.warn("JNLP token-store clear failed: {}", t.getMessage());
            }
        }
        else
        {
            // Token gone, but language / login-method preferences remain.
            save(doc);
        }
    }

    @Override
    public Optional<String> readPref(String key)
    {
        return value(key);
    }

    @Override
    public void tryWritePref(String prefKey, String prefValue)
    {
        if (prefKey == null || prefKey.isEmpty()) return;
        Map<String, String> doc = load();
        if (prefValue == null || prefValue.isEmpty())
        {
            doc.remove(prefKey);
        }
        else
        {
            doc.put(prefKey, prefValue);
        }
        save(doc);
    }

    private Optional<String> value(String mapKey)
    {
        String v = load().get(mapKey);
        return (v == null || v.isEmpty()) ? Optional.empty() : Optional.of(v);
    }

    /** Reads the persistence entry and parses it as the flat JSON store doc.
     *  Empty map on a missing entry or any failure (never throws). */
    private Map<String, String> load()
    {
        try
        {
            Object fileContents = persistenceService.getClass()
                    .getMethod("get", URL.class).invoke(persistenceService, key);
            try (InputStream in = (InputStream) fileContents.getClass()
                    .getMethod("getInputStream").invoke(fileContents))
            {
                byte[] bytes = in.readAllBytes();
                return TokenStoreCodec.parse(new String(bytes, StandardCharsets.UTF_8));
            }
        }
        catch (Throwable t)
        {
            // get() throws FileNotFoundException-equivalent when the entry doesn't
            // exist yet — that's the empty case, not a real failure.
            LOGGER.debug("JNLP token-store read miss: {}", t.getClass().getSimpleName());
            return new java.util.LinkedHashMap<>();
        }
    }

    private void save(Map<String, String> doc)
    {
        try
        {
            // create() throws if the entry already exists; swallow that, then write.
            try
            {
                persistenceService.getClass()
                        .getMethod("create", URL.class, long.class)
                        .invoke(persistenceService, key, RESERVED_SIZE);
            }
            catch (Throwable alreadyExists)
            {
                // entry already reserved; fall through to write
            }
            Object fileContents = persistenceService.getClass()
                    .getMethod("get", URL.class).invoke(persistenceService, key);
            try (OutputStream out = (OutputStream) fileContents.getClass()
                    .getMethod("getOutputStream", boolean.class).invoke(fileContents, true))
            {
                out.write(TokenStoreCodec.toJson(doc).getBytes(StandardCharsets.UTF_8));
            }
        }
        catch (Throwable t)
        {
            LOGGER.warn("JNLP token-store write failed (NOT persisted): {}", t.getMessage());
        }
    }
}
