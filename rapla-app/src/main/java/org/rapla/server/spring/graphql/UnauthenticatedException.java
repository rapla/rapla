package org.rapla.server.spring.graphql;

import org.rapla.entities.User;

/**
 * Thrown by GraphQL <em>data</em> resolvers when there is no authenticated
 * caller. Surfaced to the client as a GraphQL error
 * ({@code ErrorType.UNAUTHORIZED}, extension {@code code: UNAUTHENTICATED})
 * by {@link MutationExceptionResolver}.
 *
 * <p>Replaces the old silent-empty behaviour where an anonymous data query
 * returned {@code []} / {@code null} with HTTP 200 — indistinguishable from a
 * legitimately-empty result, which made "is my bearer token actually
 * attached?" undebuggable (the request looked successful). A missing/expired
 * token now fails loudly.
 *
 * <p>Public probes stay anonymously accessible and must NOT call
 * {@link #require}: schema introspection, {@code hello}/{@code version}/
 * {@code serverTime}, the metadata catalogs ({@code types}/{@code type}/
 * {@code periods}/{@code category}/{@code categories}), and {@code me}
 * (which intentionally returns {@code null} as the "am I logged in?" idiom).
 */
public class UnauthenticatedException extends RuntimeException
{
    public UnauthenticatedException()
    {
        super("Authentication required: this query needs a valid bearer token");
    }

    /** Returns {@code caller} unchanged if non-null; otherwise throws. */
    public static User require(User caller)
    {
        if (caller == null)
        {
            throw new UnauthenticatedException();
        }
        return caller;
    }
}
