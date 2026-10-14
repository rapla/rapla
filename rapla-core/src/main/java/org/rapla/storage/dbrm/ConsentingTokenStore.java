package org.rapla.storage.dbrm;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The refresh token is written to the backend only with consent: the server's stay-signed-in decision of the
 * login (PRD 131 R4, was the PRD 126 dialog). Until a decision is set, the latest token is held in memory;
 * {@link #setConsent(boolean)} then writes it (true) or keeps this login's tokens in memory (false); a stored entry
 * is never touched by a login without stay-signed-in (PRD 131 OQ10), only a logout clears it. A token already in
 * the backend counts as standing consent, so rotations keep flowing after a silent restart. Never throws (same
 * contract as {@link TokenStore}).
 */
public final class ConsentingTokenStore implements TokenStore
{
    private final TokenStore delegate;
    private final String backend;
    private volatile Boolean consent;
    private boolean standingResolved;
    private volatile String pending;

    public ConsentingTokenStore(TokenStore delegate, String backend)
    {
        this.delegate = delegate;
        this.backend = backend;
    }

    /** The standing answer from the backend, resolved at first use (the startup {@link #read()}, before
     *  any clear): the backend knows the server, hence its token key, only after construction. */
    private synchronized Boolean consent()
    {
        if (!standingResolved)
        {
            standingResolved = true;
            if (consent == null && delegate.read().isPresent())
            {
                consent = Boolean.TRUE;
            }
        }
        return consent;
    }

    /** Name of the backend for the user-facing result ("file", "none"). */
    public String backend() { return backend; }

    /** Whether the user chose to stay signed in (or a stored token stands for it): the backend is expected to hold the token. */
    public boolean remembers() { return Boolean.TRUE.equals(consent()); }

    /** Applies the decision for this session; returns whether the token is now readable from the backend. */
    public boolean setConsent(boolean yes)
    {
        consent = yes;
        if (!yes)
        {
            pending = null;
            return false;
        }
        String token = pending;
        if (token == null) return delegate.read().isPresent();
        delegate.tryWrite(token);
        pending = null;
        return delegate.failure().isEmpty() && delegate.read().map(token::equals).orElse(false);
    }

    @Override public Optional<String> read() { consent(); return delegate.read(); }

    @Override public void tryWrite(String token)
    {
        Boolean c = consent();
        if (c == null) pending = token;
        else if (c) delegate.tryWrite(token);
    }

    @Override public void tryClear() { consent(); pending = null; delegate.tryClear(); }
    @Override public Optional<String> readPref(String key) { return delegate.readPref(key); }
    @Override public void tryWritePref(String key, String value) { delegate.tryWritePref(key, value); }
    @Override public Optional<Failure> failure() { return delegate.failure(); }

    private static final Pattern SUB = Pattern.compile("\"sub\"\\s*:\\s*\"([^\"]*)\"");

    /** The {@code sub} claim of a JWT, or empty when absent or unreadable. */
    public static Optional<String> subjectOf(String jwt)
    {
        try
        {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return Optional.empty();
            Matcher m = SUB.matcher(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            return m.find() ? Optional.of(m.group(1)) : Optional.empty();
        }
        catch (RuntimeException e)
        {
            return Optional.empty();
        }
    }
}
