package org.rapla.storage.dbrm;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PRD 126 Phase 2: the refresh token is written to the backend only with the
 * user's consent. Until the user decides, the latest token is held in memory;
 * {@link #setConsent(boolean)} then writes it (true) or clears the backend
 * (false). A stored {@link #KEY_REMEMBER} preference ("yes"/"no") answers
 * without asking; a token already on disk from before this feature counts
 * as consent. Never throws (same contract as {@link TokenStore}).
 */
public final class ConsentingTokenStore implements TokenStore
{
    private final TokenStore delegate;
    private final String backend;
    private volatile Boolean consent;
    private volatile String pending;

    public ConsentingTokenStore(TokenStore delegate, String backend)
    {
        this.delegate = delegate;
        this.backend = backend;
        String stored = delegate.readPref(KEY_REMEMBER).orElse("");
        if ("yes".equals(stored)) consent = Boolean.TRUE;
        else if ("no".equals(stored)) consent = Boolean.FALSE;
        else if (delegate.read().isPresent())
        {
            // Installed before this feature: the token was stored without asking — keep that standing.
            consent = Boolean.TRUE;
            delegate.tryWritePref(KEY_REMEMBER, "yes");
        }
    }

    /** Name of the backend for the user-facing result ("jnlp", "file", "none"). */
    public String backend() { return backend; }

    /** True when the user has not decided yet for this machine. */
    public boolean needsDecision() { return consent == null; }

    /** Applies the decision for this session; returns whether the token is now readable from the backend. */
    public boolean setConsent(boolean yes)
    {
        consent = yes;
        if (!yes)
        {
            pending = null;
            delegate.tryClear();
            return false;
        }
        String token = pending;
        if (token == null) return delegate.read().isPresent();
        delegate.tryWrite(token);
        pending = null;
        return delegate.read().map(token::equals).orElse(false);
    }

    /** Persists the decision as the answer for future starts ("don't ask again"). */
    public void rememberDecision(boolean yes) { delegate.tryWritePref(KEY_REMEMBER, yes ? "yes" : "no"); }

    /** Logout: ask again at the next interactive login. */
    public void forgetDecision()
    {
        consent = null;
        pending = null;
        delegate.tryWritePref(KEY_REMEMBER, "");
    }

    @Override public Optional<String> read() { return delegate.read(); }

    @Override public void tryWrite(String token)
    {
        Boolean c = consent;
        if (c == null) pending = token;
        else if (c) delegate.tryWrite(token);
    }

    @Override public void tryClear() { pending = null; delegate.tryClear(); }
    @Override public Optional<String> readPref(String key) { return delegate.readPref(key); }
    @Override public void tryWritePref(String key, String value) { delegate.tryWritePref(key, value); }

    private static final Pattern EXP = Pattern.compile("\"exp\"\\s*:\\s*(\\d+)");

    /** The {@code exp} claim of a JWT as an instant, or empty when absent or unreadable. */
    public static Optional<Instant> expiryOf(String jwt)
    {
        try
        {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return Optional.empty();
            String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Matcher m = EXP.matcher(json);
            return m.find() ? Optional.of(Instant.ofEpochSecond(Long.parseLong(m.group(1)))) : Optional.empty();
        }
        catch (RuntimeException e)
        {
            return Optional.empty();
        }
    }
}
