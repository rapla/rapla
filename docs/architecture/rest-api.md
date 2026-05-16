# REST API reference

A catalog of every HTTP endpoint a browser client can call against
the Rapla Spring Boot server. Grouped by audience (SPA-likely,
admin panels, import/export, deprioritised) rather than by
controller, so an SPA team can read top-to-bottom and stop when
they hit "you probably don't need this."

Snapshot: 2026-05-14. Endpoints are stable but DTO field names
drift; check the source class when something doesn't deserialize.

> **The wire model for reservation save** (UpdateEvent envelope,
> Reservation / Appointment / Repeating JSON shapes) lives in
> [reservation-edit.md](reservation-edit.md). This page lists
> endpoints; that page describes payloads.

## Conventions

### URL layout (post-PRD-031, 2026-05-12)

No servlet context path. Every `@RestController` carries the
literal `/api/...` path in its class-level `@RequestMapping`. All
REST paths in this document include the `/api` prefix explicitly —
what you see is what hits the wire.

Six controllers are excluded from the prefix:

| Controller | Lives at | Why excluded |
|---|---|---|
| `IndexPageController` | `/`, `/index` | Landing chooser page |
| `LoginPageController` | `/login` | Spring form-login convention |
| `StatusPageController` | `/server` | Status HTML page |
| `RaplaJNLPController` | `/raplaclient.jnlp`, `/webclient/**` | JNLP launcher manifest |
| `CalendarPageController` | `/rapla/calendar(.csv)?`, `/rapla/internal_calendar(.csv)?` | 🔒 external iCal subscribers depend on these literal URLs |
| `Export2iCalController` | `/rapla/ical`, `/rapla/internal_ical` | 🔒 same — Outlook/Google/Apple subscribe to these |

Root-by-convention endpoints (not @RestController, also unprefixed):
`/oauth2/**`, `/.well-known/**`, `/connect/logout` (Spring
Authorization Server), `/swagger-ui/**`, `/error`.

### OpenAPI spec groups (post-PRD-031 Phase 5, 2026-05-15)

The OpenAPI spec is split into four groups via
`GroupedOpenApi` beans in
`rapla-app/.../spring/SpringDocGroupsConfig.java`:

| Group | URL | Audience | Includes |
|---|---|---|---|
| `auth` | `/api/v3/api-docs/auth` | External integrators wiring SSO | `/api/auth/**` |
| `client` | `/api/v3/api-docs/client` | The rapla SPA / Swing client (codegen target) | `/api/auth/**` + every SPA-internal + admin-UI controller |
| `rest` | `/api/v3/api-docs/rest` | Scripts / third-party integrators | `/api/events/**`, `/api/resources/**` (PRD 009 bulk REST) |
| `exports` | `/api/v3/api-docs/exports` | Anyone doing data in/out | `/api/export/**`, `/api/ical/import**`, external-event import, legacy `/rapla/{calendar,ical}` feeds |

**The default `/api/v3/api-docs` URL continues to serve a merged
spec** (SpringDoc 2.x preserves it as a union of all paths even
when groups are defined — verified 2026-05-15). The Angular
codegen target was nonetheless moved to
`/api/v3/api-docs/client` to give the SPA a tighter spec, but
external tooling can still hit the default URL for a full view.

The Swagger UI at `/swagger-ui/index.html` shows the groups in a
dropdown selector and switches between specs automatically.

**For SPA developers:** add new SPA-facing controllers to the
`client` group's `pathsToMatch`. **For everyone else:** put new
REST API endpoints in `rest`, new file in/out endpoints in
`exports`, new auth endpoints in `auth`. The drift-prevention test
in `ApiPrefixArchitectureTest` will tell you if you forgot.

### Auth header

Every endpoint requires `Authorization: Bearer <accessToken>`
unless it appears in the public list in
`rapla-server/.../spring/SecurityConfig.java:37-47`. The public
list:

```
/api/auth/**         /                  /index              /server
/static/**           /*.html            /*.css              /images/**
/webclient/**        /app/**            /api/logger/**      /api/ical/timezones/**
/rapla/calendar      /rapla/calendar.csv
/rapla/internal_calendar     /rapla/internal_calendar.csv
/rapla/ical          /rapla/internal_ical
/raplaclient        /raplaclient.jnlp
/api/v3/api-docs/**  /swagger-ui/**     /swagger-ui.html
/oauth2/**           /.well-known/**    /login              /error
/dhbw/status
```

Everything else is JWT-gated (`oauth2ResourceServer.jwt`).

### JSON wire format

Jackson 3 with field-based introspection
(`rapla-core/.../rest/JacksonObjectMapperFactory.java`). The fields
on `*Impl` classes are what travels — no `@JsonProperty` rewriting.
`LocalDateTime` is ISO-8601 string, interpreted in the server's
local zone. See [reservation-edit.md §Wire model](reservation-edit.md)
for the timezone-naive caveat.

The factory's full configuration:

```
FIELD            = ANY      ← include private fields
GETTER           = NONE     ← ignore all getX() / isX()
IS_GETTER        = NONE
SETTER           = NONE     ← ignore all setX()
CREATOR          = ANY
+ PROPAGATE_TRANSIENT_MARKER = true   (Java `transient` excludes a field)
+ ALLOW_FINAL_FIELDS_AS_MUTATORS = true
```

Why field-based: rapla entities have getters that resolve
cross-references through a `transient` resolver and cycle back to
the source. Getter-based discovery → infinite recursion →
`StackOverflowError`. The `transient`-marker contract is exactly
the rapla wire contract.

### OpenAPI / Swagger spec caveat (resolved 2026-05-16 by PRD 041)

> **Historical record.** PRD 041 moved OpenAPI spec generation out of
> runtime entirely. The captured specs in
> `rapla-app/src/main/resources/openapi/{auth,client,rest,exports}.json`
> are produced by `OpenApiSpecCaptureTest` (build-time, in the test
> @SpringBootTest context where SpringDoc + the test-scope
> `SwaggerJacksonConfig` are available). The captured JSON correctly
> uses Jackson 3's field-based introspection — the bridge `ModelResolver`
> in `SwaggerJacksonConfig` mirrors the Jackson 3 visibility rules onto
> a Jackson 2 ObjectMapper for the capture run. At runtime,
> `StaticOpenApiController` serves the captured JSON directly; no
> SpringDoc, no swagger-core, no Jackson 2 on the production classpath.
> The CI byte-equality check (`OpenApiSpecCaptureTest` default mode)
> catches spec drift in PRs. See PRD 041 for the design.
>
> The historical caveat below is retained for context; everything in it
> is now solved.

**The OpenAPI spec at `/api/v3/api-docs` does NOT use Jackson 3's
field-based introspection.** SpringDoc 3.0 builds the schema via
`swagger-core`'s `io.swagger.v3.core.jackson.ModelResolver`, which
uses **Jackson 2.x**. The package rename in Jackson 3 (`com.fasterxml.jackson.*`
→ `tools.jackson.*`) is so deep in swagger-core that bridging
versions inside one artifact isn't practical — upstream's
position (swagger-core [#4991](https://github.com/swagger-api/swagger-core/issues/4991))
is to wait for a swagger-core 3.x major release line, with
SpringDoc 4.x downstream of that. Realistic timeline: 12-24
months. Until then we're stuck with the two-mapper picture.
Jackson 2's default visibility is getter/setter introspection, not
field-based. So:

- **Spec properties** = whatever Jackson 2 finds via `getX`/`setX`
  pairs on the class.
- **Wire properties** = whatever Jackson 3 serializes from the
  fields (`Visibility.ANY` + `PROPAGATE_TRANSIENT_MARKER`).

These two sets **don't have to match**, and in practice they
don't:

- **Phantom properties.** A class with `private transient
  Resolver resolver; public String getName() { return
  resolver.resolveBy(this).name; }` shows `name` in the spec but
  doesn't serialize a `name` field at runtime — and at
  *deserialization* time, the JSON's `name` has nowhere to land
  (no field, setter ignored).
- **Missing properties.** A `public final` field with no getter
  (a pattern that rapla uses since PRD 011) shows up on the wire
  but doesn't appear in the spec.
- **False conflicts.** `DefaultConfiguration` has three overloaded
  `setValue(String|int|boolean)` methods. The runtime ignores all
  three (SETTER = NONE) and uses the `value` field directly.
  swagger-core's `ModelResolver` collects the three setters and
  logs `IllegalArgumentException: Conflicting setter definitions
  for property "value"` at startup — a misleading warning about a
  conflict that doesn't exist at the wire layer.

**Consequence for openapi-generator-cli output.** TypeScript /
Angular clients generated from `/api/v3/api-docs` describe what
the *Jackson 2 ModelResolver* sees, not what *Jackson 3 actually
serializes*. Generated DTOs may include phantom fields or omit
real ones. Cross-check the generated client against actual wire
data (the `api-testing` skill or a `curl` of the live endpoint)
before depending on a DTO field shape.

### Recommended fix (open follow-up, 2026-05-13)

Register a custom `ModelResolver` bean in
`rapla-app/.../spring/RaplaJacksonConfig` that wires a
**Jackson-2 ObjectMapper configured the same as the Jackson-3
runtime**:

```java
@Bean
public ModelResolver swaggerModelResolver() {
    com.fasterxml.jackson.databind.ObjectMapper mapper =
            io.swagger.v3.core.util.Json.mapper().copy();
    mapper.setVisibility(mapper.getVisibilityChecker()
            .withFieldVisibility(Visibility.ANY)
            .withGetterVisibility(Visibility.NONE)
            .withIsGetterVisibility(Visibility.NONE)
            .withSetterVisibility(Visibility.NONE)
            .withCreatorVisibility(Visibility.ANY));
    mapper.enable(MapperFeature.PROPAGATE_TRANSIENT_MARKER);
    ModelResolver resolver = new ModelResolver(mapper);
    // The @Bean alone isn't enough — swagger-core's ModelConverters
    // singleton may already have wired its default resolver before
    // Spring's bean lifecycle runs. Explicit addConverter() makes
    // the custom one stick (springdoc issue #2574).
    io.swagger.v3.core.converter.ModelConverters.getInstance()
            .addConverter(resolver);
    return resolver;
}
```

After this, `/api/v3/api-docs` schemas correspond byte-for-byte to
the JSON the SPA actually receives. Two mapper instances stay in
play (one Jackson-2 for spec, one Jackson-3 for runtime) but they
honour identical visibility rules, so the two views align.

**Smoke test after enabling the resolver.** SpringDoc's bean
chain has known fragility (springdoc-openapi #2574), and
swagger-core sometimes still confuses identically-named
getter/field pairs of different types (swagger-core #1611) —
rapla has this pattern (e.g. `getId()` returning
`ReferenceInfo<X>` for a String `id` field). To verify:

1. Hit `/api/v3/api-docs` and copy one entity's schema (e.g.
   `Reservation`).
2. `curl` an endpoint returning that entity (e.g.
   `POST /api/storage/queryAppointments` with the right
   `resources` filter) and compare the JSON property set.
3. If a property appears in one but not the other → either
   `@Schema(hidden=true)` on the offending getter, or
   `@JsonIgnore` on the conflicting field/setter, then re-test.

Until the resolver lands: any field added to a `*Impl` class
needs the generated TS client regenerated AND eyeballed against
a real sample response. The bulk of rapla entities round-trip
fine because their fields and `getX` pairs happen to align —
but edge cases (resolver-traversing getters, overloaded setters,
final fields with no getter) misrepresent the wire.

### Sources

- [SpringDoc FAQ — POJOs require getters](https://springdoc.org/faq.html)
- [swagger-core #4776 — make ObjectMapper for property discovery configurable](https://github.com/swagger-api/swagger-core/issues/4776)
- [springdoc-openapi #2381 — `spring.jackson.property-naming-strategy` not honoured](https://github.com/springdoc/springdoc-openapi/issues/2381)
- [springdoc-openapi #2574 — custom ModelResolver wiring caveats](https://github.com/springdoc/springdoc-openapi/issues/2574)
- [springdoc-openapi #1611 — getter/field name conflicts](https://github.com/springdoc/springdoc-openapi/issues/1611)
- [DZone — Extending Swagger and SpringDoc OpenAPI](https://dzone.com/articles/extending-swagger-and-spring-doc-open-api)

### Error envelope

`RaplaExceptionHandler` (`@RestControllerAdvice`) maps exceptions
to JSON:

```jsonc
{ "status": 401,
  "error":  "Unauthorized",
  "message": "Access only for admin users" }
```

Mappings:

| Exception | HTTP | When |
|---|---|---|
| `RaplaSecurityException` | 401 | Not logged in, expired JWT, or auth check rejected |
| `EntityNotFoundException` | 404 | Reference to an entity that doesn't exist |
| `IllegalArgumentException`, `AssertionError`, `MissingServletRequestParameterException` | 400 | Malformed request |
| `RaplaNewVersionException` | 500 (today; subject to change) | Optimistic-lock conflict — surface as "another user changed this; refresh and try again" |
| `RaplaException` (base) | 500 | Catch-all server error |

**Today** there's no dedicated 409 for version conflict —
`RaplaNewVersionException` falls through to 500. If the SPA needs
to distinguish, pattern-match on `message` (brittle) or push for
a status-code change in the advice.

### Conditional registration

Many controllers are `@ConditionalOnBean(RemoteSession.class)`,
meaning they're only registered when the server is configured for
remote access. In practice every dev environment qualifies; flag
this only if you build a stripped-down deployment.

---

## 1. Auth — OAuth 2.0 / OIDC only (PRD 041, 2026-05-16)

All token issuance, refresh, and revocation goes through Spring
Authorization Server's `/oauth2/*` endpoints. The legacy rapla-custom
`AuthController` (`/api/auth/login`, `/api/auth/refresh`,
`/api/auth/logout`) is **deleted** — see PRD 041's adjacent-work
section for the migration story. `/api/auth/oauth/config` (discovery),
`/api/auth/oauth/exchange/{providerId}` (BFF for external IdPs),
and `/api/auth/api-keys/*` (personal access tokens, PRD 043) remain
under `/api/auth/`.

### Token endpoints (`/oauth2/*`)

Spring AS-served, NOT a `@RestController` — they live in the filter
chain so SpringDoc doesn't list them. Wire-shape is OAuth 2.0 standard
(RFC 6749 + 7009).

| Method | Path | Body (form-encoded) | Returns | Auth |
|---|---|---|---|---|
| POST | `/oauth2/token` `grant_type=authorization_code` | `code`, `redirect_uri`, `client_id`, `code_verifier` | `{access_token, refresh_token, id_token, expires_in, token_type, scope}` | PKCE |
| POST | `/oauth2/token` `grant_type=refresh_token` | `refresh_token`, `client_id` | `{access_token, refresh_token, expires_in, token_type}` | refresh token = credential |
| POST | `/oauth2/token` `grant_type=password` | `username`, `password`, `client_id` | same | username + password |
| POST | `/oauth2/revoke` | `token`, `token_type_hint=refresh_token`, `client_id` | 200 (RFC 7009 — opaque) | the token itself |
| GET | `/oauth2/authorize` | (browser flow) | 302 to redirect_uri with `code` | form-login |
| GET | `/oauth2/jwks` | — | JWKS for signature verification | public |

All tokens are **RSA-signed JWTs** (RS256). The signing key is
persistent (`RaplaKeyStorage`-backed) — tokens survive server restart.
The `sub` claim is the user UUID; `typ` is `access`, `refresh`, or
`api_key` (PRD 043).

### Refresh + revocation model

- **One refresh token per user.** Stored as the full JWT in user
  preferences (`org.rapla.auth.session`, single slot). Each login
  returns the existing valid token instead of minting a new one —
  multi-tab / multi-device share trivially.
- **Never rotate on refresh.** The refresh grant returns the same
  refresh token + a fresh access token until the refresh token expires
  (30 d). All sessions re-Authorize together at expiry.
- **Revocation is explicit.** `POST /oauth2/revoke` clears the prefs
  entry → every refresh token in circulation for the user becomes
  invalid. In-flight access tokens keep working until their 1 h TTL.

Validation is **stateless**: signature + `typ=refresh` claim + exact
match against the full token stored in user prefs. No in-memory
authorization state — survives JVM restart.

Implementation: `RefreshSessionService` (`rapla-server`) is the single
source of truth. Wired into Spring AS via custom providers in
`AuthorizationServerConfig` (`JwtRefreshTokenGenerator`,
`RaplaRefreshTokenAuthenticationProvider`, `PasswordGrantAuthenticationProvider`,
`RaplaTokenRevocationAuthenticationProvider`, plus
`PublicClientRefreshTokenAuthenticationConverter`/`Provider` for
public-client client-auth).

See [`docs/authentication.md`](../authentication.md) for the user-facing
guide (curl examples, migration table from `/api/auth/*`).

### SPA pattern for 401

Outgoing API request returns 401 → the SPA's HTTP interceptor calls
`POST /oauth2/token grant_type=refresh_token` with the stored refresh
JWT → on success, retry the original request with the new access
token; on failure, drop tokens and redirect to the login flow. The
Swing client's `MyCustomConnector.refreshUsingToken` does the same.

### Personal-access-token API keys (`/api/auth/api-keys/*`)

GitHub-style PAT flow. Server generates an RSA keypair, signs **one**
JWT with the private key, stores only the public key in user prefs,
discards the private key. The single JWT is the API key — the user
copies it like a GitHub PAT and uses it as `Authorization: Bearer
<jwt>` on every API call. Server verifies incoming JWTs against the
stored public key (lookup by `kid` thumbprint) and confirms the key
hasn't been revoked. Backup leak yields useless public keys.

See [PRD 043](../prd/043-api-keys-jwt-pat.md) for the full design
+ [`docs/authentication.md`](../authentication.md#api-keys-personal-access-tokens)
for the user-facing curl flow.

### OIDC / SSO endpoints (Spring Authorization Server)

rapla's embedded Spring Authorization Server (SAS) exposes the
standard OIDC + OAuth 2.0 endpoints at the **root namespace** —
NOT under `/api/`, and NOT auto-discovered by SpringDoc (they're
served by Spring Security filters, not `@RestController`s, so the
OpenAPI spec doesn't include them).

Two URLs are all a client needs to bootstrap:

| Endpoint | RFC / spec | Role |
|---|---|---|
| `GET /.well-known/openid-configuration` | [OIDC Discovery 1.0](https://openid.net/specs/openid-connect-discovery-1_0.html) | Discovery document — lists every other endpoint URL (`/oauth2/authorize`, `/oauth2/token`, `/oauth2/revoke`, `/oauth2/introspect`, `/connect/logout`, `/userinfo`, …) plus supported scopes, grant types, signing algorithms, and PKCE methods |
| `GET /oauth2/jwks` | [RFC 7517](https://www.rfc-editor.org/rfc/rfc7517) | JSON Web Key Set — public keys for verifying access-token signatures. Resource servers cache this |

OIDC client libraries fetch the discovery document and auto-configure from there; documenting the downstream endpoints individually here would just duplicate the discovery JSON and risk drift.

**How to use them — don't roll your own.** Every mature OIDC
client library auto-configures from the discovery URL:

- Angular SPA → [`angular-oauth2-oidc`](https://github.com/manfredsteyer/angular-oauth2-oidc) (already wired in `rapla-angular`).
- Browser-side vanilla → [`oidc-client-ts`](https://github.com/authts/oidc-client-ts).
- Server-side Java client → Spring Security's [`spring-security-oauth2-client`](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/index.html).
- Microsoft stacks → [MSAL](https://learn.microsoft.com/entra/identity-platform/msal-overview).
- CLI / scripts → [`oauth2c`](https://github.com/cloudentity/oauth2c) for Authorization Code + PKCE; plain `curl` works for Client Credentials.

Point the library at rapla's discovery URL
(`http://<host>/.well-known/openid-configuration`) and it will
fill in every URL above + the supported scopes, response types,
and PKCE methods.

**rapla's client-id / scope conventions for the embedded SAS:**

- Default public client id: `rapla-client` (configurable via
  `RAPLA_OAUTH_CLIENT_ID`).
- Required flow: Authorization Code + PKCE (no implicit, no
  client-credentials for end-user logins).
- Scopes: `openid` + `profile`. No custom scopes today.

**External IdP (PRD 036).** When `rapla.oauth.external.providers[]`
is configured (Google, Microsoft Entra, etc.), the SPA's login
picker also lists those — and Google/Microsoft confidential-client
secrets are handled by the BFF
[`POST /api/auth/oauth/exchange/{providerId}`](#1-auth--authcontroller),
documented in the `auth` SpringDoc group. The IdPs' own `/oauth2/*`
endpoints (e.g. `https://login.microsoftonline.com/.../oauth2/v2.0/token`)
are NOT proxied by rapla; only the token exchange step is.

**Why not in OpenAPI?** Spring Authorization Server's filters
don't register `HandlerMapping` entries, so SpringDoc skips them.
We could hand-write `Paths` entries via an `OpenApiCustomizer`,
but that risks drift from the RFC contract and from SAS upgrades.
The discovery document is the canonical source. If you need a
machine-readable spec for these endpoints, fetch
`/.well-known/openid-configuration`.

---

## 2. Storage core — `RemoteStorageController`

Base path `/api/storage`. This is the SPA's main API surface. The
controller deliberately bundles every read and write into a small
number of high-leverage endpoints — `dispatch` for all writes,
`refresh` for incremental sync.

Source: `rapla-server/.../web/RemoteStorageController.java`.

### Reads

| Method | Path | Query / Body | Returns | Purpose |
|---|---|---|---|---|
| GET | `/api/storage/resources` | — | `UpdateEvent` | **Initial hydrate.** DynamicTypes, Allocatables, Categories, Periods, the calling user. Call once at app boot |
| POST | `/api/storage/queryAppointments` | `QueryAppointments { start, end, resourceIds, ownerIds }` | `AppointmentMap` (id → Appointment + parent Reservation refs) | Calendar viewport query |
| GET | `/api/storage/conflicts` | — | `List<ConflictImpl>` | Current allocation conflicts (advisory list) |
| POST | `/api/storage/allocatable/bindings/first` | `AllocatableBindingsRequest { allocatableIds, appointmentIds, ignoreReservationId? }` | `BindingMap` (allocatable → first conflicting binding) | Cheap "is anything blocking?" check |
| POST | `/api/storage/allocatable/bindings/all` | same | `List<ReservationImpl>` | Full conflict overlay payload |
| POST | `/api/storage/allocatable/date/next` | `NextAllocatableDateRequest { allocatableId, after, duration, … }` | `LocalDateTime` | "Find next free slot for resource X" |
| GET | `/api/storage/user` | `?userId=<uuid>` | `String` (username) | Display name lookup |
| POST | `/api/storage/entity/recursiveSync` | `SerializableReferenceInfo[]` | `UpdateEvent` | Fetch an entity + all its dependencies |
| POST | `/api/storage/entity/dependent` | `SerializableReferenceInfo[]` | `UpdateEvent` | Fetch entities that depend on the given ids (for safe delete) |

### Long-poll refresh

| Method | Path | Query | Returns | Purpose |
|---|---|---|---|---|
| POST | `/api/storage/refresh` | `?lastValidated=<opaque>` | `UpdateEvent` (delta since `lastValidated`) | Heartbeat: pick up changes from other clients |
| POST | `/api/storage/refreshAllEvents` | `?lastValidated=<opaque>` | `UpdateEvent` | Same but events only — used by calendar-only views |

`lastValidated` is opaque server state — echo whatever the previous
response gave you. The Swing client polls every ~30 s; pick a
cadence that matches your UX (5–30 s is reasonable).

### Writes

| Method | Path | Body | Returns | Purpose |
|---|---|---|---|---|
| POST | `/api/storage/dispatch` | `UpdateEvent` | `UpdateEvent` (refreshed + new conflicts) | **The only write path** for Reservation / Allocatable / DynamicType. Bundled transaction. See [reservation-edit.md](reservation-edit.md) for the envelope. |
| POST | `/api/storage/identifier` | `?raplaType=<type>&count=<n>` | `List<String>` | Allocate UUIDs before building a draft |
| POST | `/api/storage/merge` | `MergeRequest` + `?targetId=` | `UpdateEvent` | Merge two entities (typically allocatables) — admin only |
| POST | `/api/storage/restart` | — | void | Admin: hot-restart the server |

### User self-service

| Method | Path | Body / Query | Returns | Purpose |
|---|---|---|---|---|
| GET | `/api/storage/change/canchangepassword` | — | `boolean` | Check if the user can self-update (false e.g. for LDAP-backed users) |
| POST | `/api/storage/change/password` | `PasswordPost { oldPassword, newPassword }` | void | Self-service password change |
| POST | `/api/storage/change/name` | String + params | void | Update display name |
| POST | `/api/storage/change/email` | String | void | Initiate email change (sends confirmation) |
| POST | `/api/storage/confirm/email` | String (token) | void | Complete email change |

---

## 3. Bulk REST (PRD 009) — `RaplaEventsController`, `RaplaResourcesController`

Resource-style REST sugar on top of `/storage/dispatch`. Useful
for scripting and as a friendlier API for SPAs that prefer
fine-grained endpoints over the bundled `UpdateEvent`. Internally
these still drive the same operator dispatch — they're a wrapper,
not a separate code path.

### `/api/events`

| Method | Path | Body | Returns | Purpose |
|---|---|---|---|---|
| GET | `/api/events` | `?start=&end=&resources=&owners=&eventTypes=&attributeFilter=` | `List<ReservationImpl>` | Filtered query |
| GET | `/api/events/{id}` | — | `ReservationImpl` | Single fetch |
| POST | `/api/events` | `ReservationImpl` | `ReservationImpl` | Create |
| PUT | `/api/events` | `ReservationImpl` | `ReservationImpl` | Update (full body) |
| PATCH | `/api/events/{id}` | `ReservationImpl` (partial) | `ReservationImpl` | Update (sparse) |
| DELETE | `/api/events/{id}` | — | `boolean` | Delete |

### `/api/resources`

| Method | Path | Body | Returns | Purpose |
|---|---|---|---|---|
| GET | `/api/resources` | `?resourceTypes=&attributeFilter=` | `List<AllocatableImpl>` | Filtered query |
| GET | `/api/resources/{id}` | — | `AllocatableImpl` | Single fetch |
| POST | `/api/resources` | `AllocatableImpl` | `AllocatableImpl` | Create |
| PUT | `/api/resources` | `AllocatableImpl` | `AllocatableImpl` | Update |
| DELETE | `/api/resources/{id}` | — | void | Delete |

**Pick one style and stick with it.** Mixing `/api/storage/dispatch`
and `/api/events` in the same SPA leads to surprises around
optimistic locking (each endpoint marshals `lastChanged`
differently). The bundled-transaction model of `dispatch` is what
the Swing client uses and what's most thoroughly tested.

---

## 4. Schema & i18n

### Dynamic types — `/api/dynamictypes`

| Method | Path | Query | Returns |
|---|---|---|---|
| GET | `/api/dynamictypes` | `?classificationType=<event|resource|person>` | `List<DynamicTypeImpl>` |

The "type schema for forms" endpoint. Returns the attribute
definitions, constraints, default values, and permission templates
for each DynamicType. Cache the result; it changes only when an
admin edits a type.

See [dynamic-types.md](dynamic-types.md) for the attribute model.

### Locale — `/api/locale`

| Method | Path | Body / Path | Returns |
|---|---|---|---|
| GET | `/api/locale/{id}` | path: locale tag, `?locale=` | `LocalePackage` (message keys → translated strings) |
| POST | `/api/locale` | `Set<String>` (language codes) | `Map<String, Set<String>>` (lang → country list) |

The SPA fetches its message catalog from here. Server holds the
i18n properties bundles; client renders. For per-entity localized
fields (DynamicType labels, Category names), the value comes back
in the entity payload directly — `/api/locale` is just the static
UI strings.

### iCal timezones — `/api/ical/timezones` (public)

| Method | Path | Returns |
|---|---|---|
| GET | `/api/ical/timezones` | `List<String>` |
| GET | `/api/ical/timezones/default` | `String` |

Public — used by the Exchange Connector admin panel and any
iCal-aware editor.

---

## 5. Edit-time pre-checks — `ReservationEditController`

| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/api/edit/validate-recurrence` | `RecurrenceRule` | `RecurrenceValidation` |
| POST | `/api/edit/check-conflicts` | `ConflictCheckRequest` | `ConflictReport` |

The SPA's `EventCheck` chain equivalent. Call these before
dispatch to surface a confirm dialog rather than letting the
server reject. Both are **advisory** — `dispatch` will succeed
with conflicts; this just lets you ask first.

See PRD 024 for the server-side edit-services rationale.

---

## 6. Calendar layout view — `CalendarViewController`

| Method | Path | Query | Returns |
|---|---|---|---|
| GET | `/api/calendar/view` | `?from=&to=&strategy=&groupBy=&allocatables=` | `CalendarPage` |

Server-side layout: takes a date range + strategy and returns
pre-grouped, pre-sorted blocks ready to render. Optional — the SPA
can also expand client-side using `Appointment.createBlocks`
semantics (see [reservation-edit.md §Edge cases](reservation-edit.md))
— but for complex group-by views the server-side path is much
less code on the client.

Query params:

- `from`, `to` — `LocalDate` ISO strings.
- `strategy` — layout strategy id (matches `org.rapla.components.calendarview.*Strategy`).
- `groupBy` — `GROUP_NONE`, `BY_RESOURCE`, `BY_OWNER`, `BY_EVENT_TYPE`.
- `allocatables` — comma-separated UUIDs (optional filter).

---

## 7. Client → server logging — `/api/logger` (public)

| Method | Path | Body | Returns | Auth |
|---|---|---|---|---|
| PUT | `/api/logger/{level}` | message string | void | public |

`{level}` = `error` | `warn` | `info` | `debug`. The SPA can ship
its uncaught-exception logs to the server for ops visibility.
Public so an unauthenticated client (e.g. one with an expired
session) can still report the failure.

---

## 8. Admin / config panels

These back the in-app admin UI. Most are gated on the user being
an admin (server-side check inside the handler; not enforced by
`SecurityConfig`). Treat 401 from them as "not admin" rather than
"not logged in."

### Server-driven panels (PRD 020) — `/api/admin/panels`

| Method | Path | Body / Query | Returns |
|---|---|---|---|
| GET | `/api/admin/panels` | `?scope=SYSTEM|USER` | `List<PanelSummary>` |
| GET | `/api/admin/panels/{id}` | — | `PanelDefinition` |
| POST | `/api/admin/panels/{id}/save` | `Map<String, Object>` | `PanelDefinition` |
| POST | `/api/admin/panels/{id}/action/{actionId}` | `Map<String, Object>` | `ActionResult` |

The server declares its admin panels declaratively; the SPA renders
the panel from the returned `PanelDefinition`. See PRD 020 for the
schema and the "field types" enum.

### Plugin config endpoints

Pattern: each plugin exposes a `/x/config` endpoint that returns
its current configuration as a `DefaultConfiguration` /
`RaplaConfiguration` blob. Admin-gated inside the handler.

| Plugin | Endpoint | Returns |
|---|---|---|
| iCal | `GET /api/ical/config` (admin), `GET /api/ical/config/default` (user) | `DefaultConfiguration` |
| Exchange | `GET /api/exchange/config/default`, `GET /api/exchange/config/timezones` | `RaplaConfiguration`, `List<String>` |
| Mail | `GET /api/mail/config/external`, `GET /api/mail/config`, `POST /api/mail/config?test=...` | `boolean`, `DefaultConfiguration`, void |
| JNDI | `GET /api/jndi`, `POST /api/jndi` (test) | `DefaultConfiguration`, `boolean` |

### Other admin

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/mail/send` | Send an arbitrary email to a user (admin) |
| POST/GET | `/api/archiver`, `/api/archiver/backup`, `/api/archiver/restore` | Data retention + backup. Gated on `ArchiverService` bean. |
| POST | `/api/urlencryption` | Obfuscate a URL token for external sharing |

---

## 9. Import / export

### iCal export — public subscription URLs

| Method | Path | Query | Returns | Auth |
|---|---|---|---|---|
| GET, HEAD | `/rapla/ical` | `?file=&user=` | iCal file | public |
| GET, HEAD | `/rapla/internal_ical` | `?file=&user=` | iCal file | public |

🔒 These are the subscription URLs an external calendar (Google,
Outlook, Apple) hits. They stay at `/rapla/*` (excluded from the
`/api` prefix in `ApiPathPrefixConfig`) because changing them
would break live subscribers. Public; the `file` parameter is a
server-stored named export.

### iCal import

| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/api/ical/import` | `ICalImport.Import { content, dynamicTypeKey, ... }` | `Integer[]` (created event ids) |

### External-event import (DHBW-style CSV ingest)

`@ConditionalOnProperty(rapla.externalevents.enabled)`. Not
enabled by default; relevant only if your deployment uses the
external-event integration.

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/api/externaleventimport/metadata` | — | `ExternalEventImportMetadata` |
| POST | `/api/externaleventimport/loadEvents` | `ImportCriteria` | `ExternalEventImportResult` |
| POST | `/api/externaleventimport/uploadCsv` | `multipart/form-data` | `ExternalEventImportResult` |
| POST | `/api/externaleventimport/createReservations` | `CreateReservationsRequest` | `List<String>` |

---

## 10. Not needed by an SPA

Page-rendering controllers that serve HTML / JNLP / CSV directly.
An SPA will replace the HTML pages with client-side routes and
won't need the JNLP at all.

| Controller | Paths | Note |
|---|---|---|
| `IndexPageController` | `/`, `/index` | Landing page; SPA replaces |
| `CalendarPageController` | `/rapla/calendar`, `/rapla/calendar.csv`, `/rapla/internal_calendar`, `/rapla/internal_calendar.csv` | 🔒 Legacy export widget URLs — kept under `/rapla/` for backward compat |
| `StatusPageController` | `/server` | Server status HTML page |
| `RaplaJNLPController` | `/raplaclient`, `/raplaclient.jnlp`, `/webclient/*.jar` | Java Web Start manifests + signed jars — Swing-only |

---

## Recipes — common SPA flows in one place

### App boot

```
1. POST /oauth2/token grant_type=password            → tokens
2. GET  /api/storage/resources                       → cache: types, allocatables, periods, categories, self
3. GET  /api/dynamictypes                            → form schemas
4. GET  /api/locale/<userLocale>                     → message catalog
5. setInterval(20s) → POST /api/storage/refresh      → incremental updates
```

### Open the calendar viewport

```
6. POST /api/storage/queryAppointments { start, end, resourceIds }
                                                     → AppointmentMap
   (or GET /api/calendar/view if using server-side layout)
```

### Create a reservation

```
7. POST /api/storage/identifier { raplaType: "reservation", count: 1 }
                                                     → [uuid]
8. POST /api/storage/identifier { raplaType: "appointment", count: N }
                                                     → [uuid, ...]
9. (optional) POST /api/storage/allocatable/bindings/all { ... }
                                                     → advisory conflict list
10. POST /api/storage/dispatch { reservations: [draft] }
                                                     → refreshed entity + conflicts
```

### Edit an existing reservation

```
11. POST /api/storage/refresh                        → fresh copy in cache
12. clone client-side                                → mutable draft
13. POST /api/storage/dispatch { reservations: [edited] }
                                                     → 200, or RaplaNewVersionException → refresh-and-retry dialog
```

### Logout

```
14. POST /api/auth/logout (Bearer <access>)          → clears server-side refresh-token session
15. drop tokens locally
```

The access token stays nominally valid until its TTL expires (JWT
is stateless), but the server-side refresh session is gone, so a
later `/api/auth/refresh` will fail. Calling logout is optional
but recommended — otherwise the refresh token lives on until its
own TTL.

---

## Known gaps (May 2026)

- **No 409 for version conflict.** `RaplaNewVersionException`
  currently maps to 500. Distinguishing it requires inspecting the
  error message until the advice is updated.
- **No server push.** The refresh model is poll-only. Server-Sent
  Events / WebSocket would reduce latency but isn't on any active
  PRD.
- **No per-endpoint OpenAPI / Swagger.** This doc is the closest
  thing. If the SPA wants a generated TS client, it has to read
  the controller signatures directly.
- **DTO drift.** Some bulk-REST DTOs (e.g.
  `ExternalEventImportMetadata`) live in plugin packages and aren't
  exported via `rapla-core`. Verify the actual JSON shape with a
  one-off curl before assuming.

---

## See also

- [reservation-edit.md](reservation-edit.md) — wire model details
  (UpdateEvent / Reservation / Appointment JSON) and the edge-case
  reference
- [permissions.md](permissions.md) — what 401 vs 403 means here
- [flows.md](flows.md) — store / dispatch / refresh internals
- [dynamic-types.md](dynamic-types.md) — DynamicType / Attribute /
  Classification model
- [`../prd/026-angular-frontend.md`](../prd/026-angular-frontend.md)
  — SPA-migration scoping and open questions
- [`../prd/009-server-bulk-storage-rest-api.md`](../prd/009-server-bulk-storage-rest-api.md)
  — rationale for the `/events` and `/resources` resource-style API
- [`../prd/020-server-driven-admin-panels.md`](../prd/020-server-driven-admin-panels.md)
  — rationale for `/admin/panels`
