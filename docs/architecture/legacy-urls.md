# Legacy URLs — the `/rapla/` prefix after the Spring Boot migration

The Jetty-era deployment served the whole webapp under the **context root `/rapla`**.
The Spring Boot migration (PRD 031) moved the context root to **`/`** — and that split
one URL space into two responsibilities that are easy to get wrong:

## Server side — routes kept their literal paths

The externally-subscribed endpoints deliberately kept `/rapla/` as a **literal path
prefix in their mappings** (`CalendarPageController` → `/rapla/calendar(.csv)?`,
`Export2iCalController` → `/rapla/ical`, …). They are 🔒-protected by the AGENTS.md §15
allow-list and `ApiPrefixArchitectureTest`: external iCal subscribers (Outlook, Google,
Apple) hold these exact URLs, so they must never move.

## Client side — URL generators must emit the prefix explicitly

Under Jetty, client code built subscription/export URLs as `codeBase + "calendar"` and
the *container* supplied `/rapla/`. Since the context-root move, the same code silently
produces `/calendar` — a 404 — while the server routes still answer at
`/rapla/calendar`. **Any code that generates a user-facing calendar/export URL (Swing
dialogs, HTML pages, iCal export, autoexport links) must emit the `/rapla/` prefix
itself.** When an old bookmarked URL works but a freshly generated one doesn't, check
the generator, not the controller (regression found 2026-06-18 after a multi-hour
debug; the fix touched URL generation only — existing published URLs never changed).

## `UrlEncryptor` — the salt rides inside the returned string

`UrlEncryptor.encrypt(plain)` does not return just the ciphertext: it returns
**`<base64-blob>&salt=<salt>`** — the salt parameter is embedded in the return value.
The decrypt side (`EncryptedHttpServletRequest`) expects to find `salt` as a request
parameter and reconstructs the key from it.

Consequences:
- The return value is only safe **appended raw** into a query string
  (`?page=calendar&user=x&key=` + encrypted). URL-encoding it, splitting on `&`, or
  storing it as a single opaque token breaks decryption with a key mismatch.
- `EncryptedHttpServletRequest.getRequestURI()` historically returns a **full URL**,
  not a path — callers that expect servlet-spec semantics must not parse it as a path.

Both behaviors are load-bearing for every published calendar URL in the wild; treat
the wire format as frozen.

## Rapla 2.0 context paths — the `rapla.legacy-context-path` filter (PRD 109)

A Rapla 2.0 deployment ran the whole webapp under a **servlet context path** — a single
installation used one path, a shared hosting one Jetty context per tenant. Rapla 3 has no
context path (PRD 031 Phase 1), so every URL in circulation at such an installation would
break on upgrade.

Setting `rapla.legacy-context-path: /<the old context>` registers `LegacyPathFilter`
(`org.rapla.server.spring.web`) ahead of the Spring Security chain:

| Legacy path (`${p}` = the configured prefix) | Treatment | Target |
|---|---|---|
| `${p}/rapla/calendar`, `/calendar.csv`, `/internal_calendar`, `/internal_calendar.csv` | rewritten in-place, **no redirect** | same path without the prefix |
| `${p}/`, `${p}/index`, `${p}/rapla/index` | rewritten in-place | `/`, `/index` |
| `${p}/rapla/raplaclient.jnlp` | 301 | `/raplaclient.jnlp` (Rapla 3 drops the `/rapla/` segment) |
| everything else under `${p}/**` | 301 | same path without the prefix |

Unset (the default) = the filter is not registered at all and none of these paths exist.

Three properties of the implementation are load-bearing:

- **It wraps the request, it does not `forward`.** The same filter chain continues with a
  path-rewritten request, so Spring Security's matchers see the canonical path. A `forward`
  would skip the security chain (registered for the REQUEST/ERROR dispatch), which would turn
  the prefix into a bypass the moment a gated path enters the map.
- **The query string is passed through raw** — see the `UrlEncryptor` section above: the salt
  rides inside the value, and re-encoding breaks decryption of every published `key=` link.
- **The mapping is a code constant**, not configuration. A configurable list is one `/api/**`
  entry away from duplicating the entire live URL space under a second prefix, and with it the
  OAuth redirect-URI checks, the rate-limit path comparisons, the cookie path and the CSP
  assumptions.

Generated links (index page, calendar navigation, CSS) deliberately do **not** carry the
prefix: the first click leaves the `${p}/` space and lands on the canonical URL. The prefix
keeps *published* URLs alive; it is not a second navigable URL space.

## The Rapla 2.0 REST API is gone

Rapla 2.0 shipped a JAX-RS API under `/<context>/rapla/…` — `events`, `resources`,
`dynamictypes`, `auth` (package `org.rapla.enpoints.server`) plus the plugin services
`ical/export`, `ical/config`, `ical/timezones`, `exchange/config`, `exchange/connect`,
`urlencryption`, `templateimport`, `archiver`. **None of it exists in Rapla 3**, and no URL
alias brings it back: the paths moved (PRD 031/049), the DTO shapes changed, and the
authentication changed (Rapla 3 has no query-parameter credentials and no `httpBasic()` —
`/api/**` takes a Bearer token only). Legacy REST calls therefore fall through the filter's
301 and end at a canonical 404.

Replacements for the two endpoints that were still in production use:

| Rapla 2.0 | Rapla 3 |
|---|---|
| `GET /rapla/events?start&end&resources&eventTypes` | `/api/graphql` → `reservations(filter: { from, to, allocatableIdsIn, typeIn })` |
| `GET /rapla/resources?resourceTypes` | `/api/graphql` → `allocatables(filter: { typeIn })` |
| `POST /rapla/events` | `/api/graphql` → `createReservation(input: CreateReservationInput!)` |
| `?username=…&password=…` | `Authorization: Bearer <api-key>` (PRD 076/043) |
