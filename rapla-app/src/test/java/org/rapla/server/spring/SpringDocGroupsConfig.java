package org.rapla.server.spring;

import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PRD 031 Phase 5 — splits the unified OpenAPI spec into four audience-focused groups.
 *
 * <p>Each group's spec is served at {@code /api/v3/api-docs/<group>} and shows up
 * in the Swagger UI dropdown. The default {@code /api/v3/api-docs} URL no longer
 * serves a merged spec — once any {@code GroupedOpenApi} bean is registered,
 * SpringDoc replaces that URL with a swagger-config group listing.
 *
 * <p>The Angular codegen ({@code rapla-angular/package.json} {@code gen:api})
 * targets {@code /api/v3/api-docs/client}, so the SPA's generated client ships
 * only services for endpoints the SPA actually uses.
 *
 * <p>Group membership is enforced by {@code ApiPrefixArchitectureTest} — every
 * non-allow-listed {@code @RestController} must appear in exactly one group's
 * {@code pathsToMatch} (the {@code auth ⊆ client} overlap is the only allowed
 * exception). See AGENTS.md §15.
 *
 * <p><b>When you add a new controller:</b> add its path to the appropriate group
 * below. New SPA-facing endpoint → {@link #clientApi()}. New file in/out
 * endpoint → {@link #exportsApi()}. New auth endpoint → {@link #authApi()}.
 */
@Configuration
public class SpringDocGroupsConfig
{
    /**
     * OAuth + JWT mechanics. Stable contract for external integrators wiring SSO.
     * Also included in {@link #clientApi()} so SPA codegen has login covered.
     *
     * <p>The {@code OpenApiCustomizer} adds a long Markdown description that
     * Swagger UI renders above the endpoint list. It cross-references the
     * Spring Authorization Server OIDC endpoints (which are NOT in this spec
     * because they're served by Security filters, not {@code @RestController}s).
     */
    @Bean
    public GroupedOpenApi authApi()
    {
        return GroupedOpenApi.builder()
                .group("auth")
                .displayName("Authentication & OIDC")
                .pathsToMatch("/api/auth/**")
                .addOpenApiCustomizer(api -> api.info(new Info()
                        .title("rapla — Authentication & OIDC")
                        .version("2.1-SNAPSHOT")
                        .description(AUTH_GROUP_DESCRIPTION)))
                .build();
    }

    /**
     * Markdown description shown above the {@code auth} group endpoint list in
     * Swagger UI. CommonMark with inline links.
     */
    private static final String AUTH_GROUP_DESCRIPTION = """
            **rapla-specific authentication endpoints.** Three things this group covers:

            1. **Password login + JWT lifecycle** — `POST /api/auth/login`, `POST /api/auth/refresh`,
               `POST /api/auth/logout`. Username + password in, access + refresh JWT out (HS256).
            2. **OIDC discovery for the SPA picker** — `GET /api/auth/oauth/config` returns the
               provider list the Angular login screen shows (rapla's embedded SAS + any external
               IdPs configured via `rapla.oauth.external.providers[]`).
            3. **BFF token-exchange for external IdPs** — `POST /api/auth/oauth/exchange/{providerId}`
               proxies the OAuth token call to Google / Microsoft Entra etc. so the SPA never holds
               the confidential `client_secret` (PRD 036).

            ## Not in this spec — Spring Authorization Server endpoints

            rapla's embedded SAS exposes the standard OIDC + OAuth 2.0 endpoints at the **root namespace**.
            They're served by Spring Security filters (not `@RestController`s), so SpringDoc skips them.

            Two URLs are all a client needs to bootstrap:

            - [`/.well-known/openid-configuration`](/.well-known/openid-configuration) — the OIDC
              discovery document. Lists every other endpoint URL (`/oauth2/authorize`, `/oauth2/token`,
              `/oauth2/revoke`, `/oauth2/introspect`, `/connect/logout`, `/userinfo`, ...) plus
              supported scopes, grant types, signing algorithms, and PKCE methods. Any OIDC client
              library will auto-configure from this.
            - [`/oauth2/jwks`](/oauth2/jwks) — the JSON Web Key Set. Public keys for verifying
              access-token signatures. Resource servers cache this.

            ## Specs & references

            | What | Where |
            |---|---|
            | OAuth 2.0 Authorization Framework | [RFC 6749](https://www.rfc-editor.org/rfc/rfc6749) |
            | PKCE (mandatory for public clients) | [RFC 7636](https://www.rfc-editor.org/rfc/rfc7636) |
            | JWT format | [RFC 7519](https://www.rfc-editor.org/rfc/rfc7519) |
            | JWKS (public keys) | [RFC 7517](https://www.rfc-editor.org/rfc/rfc7517) |
            | Token Revocation | [RFC 7009](https://www.rfc-editor.org/rfc/rfc7009) |
            | Token Introspection | [RFC 7662](https://www.rfc-editor.org/rfc/rfc7662) |
            | OpenID Connect Core | [OIDC Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html) |
            | OIDC Discovery | [OIDC Discovery 1.0](https://openid.net/specs/openid-connect-discovery-1_0.html) |
            | OIDC RP-Initiated Logout | [OIDC RP-Initiated Logout 1.0](https://openid.net/specs/openid-connect-rpinitiated-1_0.html) |

            ## Recommended client libraries

            | Stack | Library |
            |---|---|
            | Angular | [`angular-oauth2-oidc`](https://github.com/manfredsteyer/angular-oauth2-oidc) (already wired in `rapla-angular`) |
            | Browser-side vanilla JS / TS | [`oidc-client-ts`](https://github.com/authts/oidc-client-ts) |
            | Server-side Java | [`spring-security-oauth2-client`](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/index.html) |
            | Microsoft stacks | [MSAL](https://learn.microsoft.com/entra/identity-platform/msal-overview) |
            | CLI / shell scripts | [`oauth2c`](https://github.com/cloudentity/oauth2c) |

            ## rapla conventions

            - Default public client id: `rapla-client` (override via `RAPLA_OAUTH_CLIENT_ID` env var).
            - Mandatory flow: Authorization Code + PKCE. No implicit grant, no client-credentials for
              end-user logins.
            - Scopes: `openid` + `profile`. No custom scopes today.
            - Access-token TTL: 1 h; refresh-token TTL: 21 d (`RefreshSessionService` constants).
            - No refresh-token rotation: the same refresh token is returned until it expires
              (single-slot model); at expiry the user re-authorizes.

            See `docs/architecture/rest-api.md` §1 for the full OIDC endpoint reference.
            """;

    /**
     * The rapla SPA / Swing client surface: data, layout, schemas, i18n, admin UI,
     * plugin configs. Changes ship in lockstep with the UI. Not a stable
     * third-party contract.
     *
     * <p>Includes the {@code /api/auth/**} paths from {@link #authApi()} so the
     * generated TypeScript client has {@code AuthService} bundled with the rest
     * of the SPA's API surface.
     */
    @Bean
    public GroupedOpenApi clientApi()
    {
        return GroupedOpenApi.builder()
                .group("client")
                .displayName("rapla SPA / Swing client (internal)")
                .pathsToMatch(
                        // Auth (overlap with authApi by design — SPA needs login in its spec)
                        "/api/auth/**",
                        // Core SPA data + layout
                        "/api/storage/**",
                        "/api/edit/**",
                        "/api/table/**",
                        "/api/dynamictypes",
                        "/api/dynamictypes/**",
                        "/api/locale",
                        "/api/locale/**",
                        "/api/logger/**",
                        // Admin UI
                        "/api/users",
                        "/api/users/**",
                        "/api/plugins",
                        "/api/plugins/**",
                        "/api/settings",
                        "/api/settings/**",
                        "/api/admin/panels",
                        "/api/admin/panels/**",
                        // Plugin config screens
                        "/api/mail/config",
                        "/api/mail/config/**",
                        "/api/ical/config",
                        "/api/ical/config/**",
                        "/api/exchange/config",
                        "/api/exchange/config/**",
                        "/api/exchange/connect",
                        "/api/exchange/connect/**",
                        "/api/jndi",
                        "/api/jndi/**",
                        "/api/eventtimecalculator",
                        "/api/eventtimecalculator/**",
                        // Admin tools
                        "/api/archiver",
                        "/api/archiver/**",
                        "/api/urlencryption",
                        "/api/urlencryption/**",
                        // Timezone catalog — SPA admin panels (Exchange/iCal config) need it
                        "/api/ical/timezones",
                        "/api/ical/timezones/**")
                .build();
    }

    /**
     * Imports, exports, and the legacy {@code /rapla/*} iCal feed URLs that
     * external calendar subscribers (Outlook / Google / Apple) depend on.
     */
    @Bean
    public GroupedOpenApi exportsApi()
    {
        return GroupedOpenApi.builder()
                .group("exports")
                .displayName("Imports / Exports / Legacy feeds")
                .pathsToMatch(
                        // /api/ current
                        "/api/ical/import",
                        "/api/ical/import/**",
                        "/api/externaleventimport",
                        "/api/externaleventimport/**",
                        // /rapla/ legacy — external subscribers depend on these literal URLs (HARD)
                        "/rapla/calendar",
                        "/rapla/calendar.csv",
                        "/rapla/internal_calendar",
                        "/rapla/internal_calendar.csv",
                        "/rapla/ical",
                        "/rapla/internal_ical")
                .build();
    }
}
