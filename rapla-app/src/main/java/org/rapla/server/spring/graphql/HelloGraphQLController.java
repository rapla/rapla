package org.rapla.server.spring.graphql;

import java.time.Instant;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

/**
 * PRD 035 testbed — minimal GraphQL controller backing
 * {@code rapla-app/src/main/resources/graphql/schema.graphqls}. Four trivial
 * resolvers ({@code hello}, {@code serverTime}, {@code version}, {@code me})
 * to validate end-to-end wiring (Spring for GraphQL autoconfig → schema
 * discovery → resolver binding → JSON serialization → GraphiQL UI round-trip)
 * including the auth path (paste a Bearer JWT in GraphiQL Headers → re-run
 * {@code me} → see {@code authenticated=true}).
 *
 * <p>The real PRD 035 surface (reservations, allocatables, types, mutations,
 * §12 filtering, etc.) lands in dedicated controllers per the design in
 * {@code docs/prd/035-rapla-mcp-server.md §"2026-05-24 design refinement"}.
 *
 * <p><b>{@code @Argument("name")} must be explicit</b> — Spring for GraphQL
 * looks up the schema-field-argument by reflective parameter name, but
 * compiler parameter-name retention is not guaranteed (same rule as AGENTS.md
 * §15's {@code @RequestParam("name")} convention). Without the explicit name
 * the bean factory throws {@code "Name for argument of type [String] not
 * specified, and parameter name information not found in class file either"}
 * at startup.
 */
@Controller
public class HelloGraphQLController
{
    @QueryMapping
    public String hello(@Argument("name") String name)
    {
        return "Hello, " + name + "!";
    }

    @QueryMapping
    public String serverTime()
    {
        return Instant.now().toString();
    }

    @QueryMapping
    public String version()
    {
        return "2.1-SNAPSHOT";
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Me me()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = auth != null
                && auth.isAuthenticated()
                && !"anonymousUser".equals(String.valueOf(auth.getPrincipal()));
        String principal = authenticated ? auth.getName() : null;
        List<String> authorities = auth == null
                ? List.of()
                : auth.getAuthorities().stream()
                      .map(GrantedAuthority::getAuthority)
                      .toList();
        return new Me(authenticated, principal, authorities);
    }

    public record Me(boolean authenticated, String principal, List<String> authorities) {}
}
