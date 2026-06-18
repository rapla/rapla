package org.rapla.server.spring.graphql;

/**
 * PRD 069 — thrown by GraphQL data resolvers when the caller is authenticated
 * but not permitted to perform the requested admin-scoped operation — e.g. the
 * access-by-target filters (`accessibleByUsername` / `accessibleByUserId` /
 * `accessibleByGroup`) name a user/group the caller may not administer.
 *
 * <p>Surfaced to the client as a GraphQL error ({@code ErrorType.FORBIDDEN},
 * extension {@code code: FORBIDDEN}) by {@link MutationExceptionResolver}.
 *
 * <p>§12: the message is deliberately generic and identical whether the named
 * handle does not exist or merely lies outside the caller's admin scope — the
 * response must not let a caller probe for the existence of users/groups they
 * cannot administer.
 */
public class ForbiddenException extends RuntimeException
{
    public ForbiddenException()
    {
        super("Not permitted: you may only query users or groups you administer");
    }
}
