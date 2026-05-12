package org.rapla.server.internal;

import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.logger.Logger;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Date;

/**
 * Persists Spring Security's remember-me tokens in Rapla's system preferences,
 * so a remember-me cookie issued before a server restart still validates after
 * the JVM comes back up — matching the persistent-JWK pattern used by
 * {@link RaplaKeyStorageImpl}.
 *
 * <p>Storage shape: a single system-preferences entry
 * {@code org.rapla.auth.rememberMeTokens} holds a JSON object
 * <pre>{ "&lt;series&gt;": {"username": "&lt;userId&gt;", "tokenValue": "...", "lastUsed": &lt;epochMillis&gt;}, ... }</pre>
 * The whole map is rewritten on every token rotation; at expected scale
 * (~hundreds of active cookies for a small org) that's negligible compared to
 * the rest of the facade's XML/JDBC flush cost on a preferences commit.
 *
 * <p>Spring's contract is series-based lookup, so each entry stores its own
 * username field to support {@link #removeUserTokens(String)}.
 */
public class RaplaTokenRepository implements PersistentTokenRepository
{
    static final TypedComponentRole<String> TOKENS =
            new TypedComponentRole<>("org.rapla.auth.rememberMeTokens");

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final RaplaFacade facade;
    private final Logger logger;

    public RaplaTokenRepository(RaplaFacade facade, Logger logger)
    {
        this.facade = facade;
        this.logger = logger;
    }

    @Override
    public synchronized void createNewToken(PersistentRememberMeToken token)
    {
        ObjectNode root = readAll();
        ObjectNode entry = MAPPER.createObjectNode();
        entry.put("username", token.getUsername());
        entry.put("tokenValue", token.getTokenValue());
        entry.put("lastUsed", token.getDate().getTime());
        root.set(token.getSeries(), entry);
        writeAll(root);
    }

    @Override
    public synchronized void updateToken(String series, String tokenValue, Date lastUsed)
    {
        ObjectNode root = readAll();
        JsonNode existing = root.get(series);
        if (existing == null || !existing.isObject()) return;
        ObjectNode entry = (ObjectNode) existing;
        entry.put("tokenValue", tokenValue);
        entry.put("lastUsed", lastUsed.getTime());
        writeAll(root);
    }

    @Override
    public synchronized PersistentRememberMeToken getTokenForSeries(String seriesId)
    {
        ObjectNode root = readAll();
        JsonNode entry = root.get(seriesId);
        if (entry == null || !entry.isObject()) return null;
        String username = entry.path("username").asString(null);
        String tokenValue = entry.path("tokenValue").asString(null);
        long ts = entry.path("lastUsed").asLong(0);
        if (username == null || tokenValue == null) return null;
        return new PersistentRememberMeToken(username, seriesId, tokenValue, new Date(ts));
    }

    @Override
    public synchronized void removeUserTokens(String username)
    {
        ObjectNode root = readAll();
        int sizeBefore = root.size();
        root.removeIf(v -> v.isObject() && username.equals(v.path("username").asString(null)));
        if (root.size() != sizeBefore) writeAll(root);
    }

    private ObjectNode readAll()
    {
        try
        {
            String stored = facade.getSystemPreferences().getEntryAsString(TOKENS, null);
            if (stored == null || stored.isEmpty()) return MAPPER.createObjectNode();
            JsonNode parsed = MAPPER.readTree(stored);
            return parsed.isObject() ? (ObjectNode) parsed : MAPPER.createObjectNode();
        }
        catch (Exception e)
        {
            logger.warn("Failed to read remember-me tokens; treating as empty: " + e.getMessage());
            return MAPPER.createObjectNode();
        }
    }

    private void writeAll(ObjectNode root)
    {
        try
        {
            Preferences prefs = facade.getSystemPreferences();
            Preferences edit = facade.edit(prefs);
            edit.putEntry(TOKENS, root.toString());
            facade.store(edit);
        }
        catch (RaplaException e)
        {
            logger.error("Failed to persist remember-me tokens: " + e.getMessage());
        }
    }
}
