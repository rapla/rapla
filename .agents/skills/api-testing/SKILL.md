ent---
name: api-testing
description: Use when the user wants to probe the running rapla server's REST API directly — login, fetch resources, query reservations/appointments, dispatch updates — bypassing the Swing client. Useful for confirming wire-format payloads (e.g. "the GUI shows empty names — is the data wrong on the wire, or is it a client-side deserialize bug?"), reproducing server bugs without booting the client, and quick smoke-testing endpoints after Spring controller changes. Assumes the dev server is running per AGENTS.md §8.
---

# Probing the rapla REST API

Use this when you need to inspect what the server actually returns over the wire, separate from any Swing-client behavior.

## Prerequisites

Server must be running. Check via AGENTS.md §8 status check
(`jps -l | grep RaplaSpringBoot` + `curl /raplaclient.jnlp` returns 200).

URL layout post PRD 031 (2026-05-12):

| Namespace | Examples |
|---|---|
| `/api/...` | REST API — `/api/storage/resources`, `/api/v3/api-docs`, `/api/auth/api-keys`, etc. |
| `/oauth2/...`, `/.well-known/...` | OAuth2 / OIDC (RFC paths, root) |
| `/rapla/{calendar,ical,internal_calendar,internal_ical,*.csv}` | Six legacy load-bearing URLs (external iCal subscribers, calendar embeds) |
| `/raplaclient.jnlp`, `/webclient/**` | JNLP Swing launcher (root) |
| `/app/` | Angular SPA (root mount) |

The historical `/rapla` context-path was dropped — no global prefix.

## Login (admin / empty password — dev only)

The bundled dev DB ships with one user: **`admin`** with **empty password**. Direct username/password login uses the OAuth 2.0 password grant at `/oauth2/token` (the rapla-custom `/api/auth/login` was removed — PRD 041). It returns a JSON body `{access_token, refresh_token, expires_in, token_type}` (snake_case). The `access_token` is an RSA-signed (RS256) JWT used as a Bearer token for all subsequent requests.

```bash
ACCESS=$(curl -s -X POST "http://localhost:8051/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&username=admin&password=&client_id=rapla-client" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
echo "Got token: ${ACCESS:0:40}..."
```

Tokens expire after `expires_in` seconds (3600 by default). Re-run the login if you get 401 mid-session, or refresh with `grant_type=refresh_token&refresh_token=…&client_id=rapla-client` against the same endpoint.

## Bootstrap payload — `GET /storage/resources`

Returns an `UpdateEvent` containing the entity bootstrap the client needs at login: categories, types (DynamicTypes), users, preferences, resources (Allocatables). Reservations are NOT in this payload — query them separately (next section).

```bash
curl -s -H "Authorization: Bearer $ACCESS" \
  "http://localhost:8051/api/storage/resources" > /tmp/resources.json

python3 - <<'PY'
import json
d = json.load(open('/tmp/resources.json'))
for k,v in d.items():
    if isinstance(v, list):
        print(f"  {k}: {len(v)} items")
    elif v is None:
        print(f"  {k}: null")
    else:
        print(f"  {k}: {type(v).__name__}")
PY
```

Useful drill-downs:
- A specific resource: `data['resources'][0]` — has `classification.{typeId, type, data}` and `links.owner`.
- The DynamicType behind a resource: filter `data['types']` by `id == resource.classification.typeId`. Look at `annotations.nameformat.formatString` to see what controls the displayed name (commonly `"{name}"`).
- A specific category: `data['categories']` — flat list, parent/child via `links.parent` / `links.childs`.

## Reservations — `POST /storage/queryAppointments`

Reservations are not in the bootstrap. Use `/queryAppointments` with a JSON body matching `RemoteStorage.QueryAppointments`. **You must filter by either `resources` or `ownerIds`** — an empty body returns 0 results.

```bash
RESOURCE_ID=$(python3 -c "import json; print(json.load(open('/tmp/resources.json'))['resources'][0]['id'])")
echo "Filtering by resource: $RESOURCE_ID"

curl -s -X POST "http://localhost:8051/api/storage/queryAppointments" \
  -H "Authorization: Bearer $ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"resources\":[\"$RESOURCE_ID\"],\"start\":\"2020-01-01T00:00:00\",\"end\":\"2030-01-01T00:00:00\"}" \
  | python3 -m json.tool | head -50
```

Response shape: `{entityIdToAppointmentIds: {resourceId: [appointmentId, ...]}, reservations: [Reservation, ...]}`.

A reservation's display name comes from its classification (same mechanism as resources): `classification.data.<attr>` resolved through the type's `nameformat` annotation.

## Other endpoints worth knowing

All on `/api/storage/...` from `RemoteStorage.java`:

| Endpoint | Method | Purpose |
|---|---|---|
| `/conflicts` | GET | List of `ConflictImpl` |
| `/refresh?lastValidated=<ts>` | POST | Incremental sync since timestamp |
| `/refreshAllEvents?lastValidated=<ts>` | POST | Full event refresh |
| `/dispatch` | POST | Apply an `UpdateEvent` (store/remove/merge) — write path |
| `/identifier?raplaType=<t>&count=<n>` | POST | Reserve N IDs for new entities of a given type |
| `/user?userId=<id>` | GET | Lookup username |
| `/change/password` | POST | `{username, oldPassword, newPassword}` |

Authentication endpoints are on `/api/auth/`:

| Endpoint | Method | Purpose |
|---|---|---|
| `/login` | POST | `{username, password}` → `{accessToken, expiresIn, refreshToken}` |
| `/refresh` | POST | `{refreshToken}` → fresh access token |

## When the wire format itself looks wrong

If a client-side bug is suspected (e.g. "empty names in the GUI"):

1. **Pull the raw JSON** with the curl above.
2. Confirm the data is actually present: `classification.data.<attr>` populated, `types[?].annotations.nameformat.formatString` non-null.
3. If the data IS in the JSON, the bug is **client-side** — in deserialization, `setResolver` chain, or post-deserialize init. See PRD 010 (field-based wire format) and PRD 011 risk #4 (Jackson 3 transient-field handling).
4. If the data is missing, the bug is **server-side** — in the controller, the storage layer, or the Jackson serialize config (`JacksonObjectMapperFactory` should produce field-based, transient-skipping output).

Round-trip the suspect entity through `JacksonObjectMapperFactory.create()` in a unit test (see `rapla-core/src/test/java/org/rapla/rest/Jackson3TransientInitializerTest.java` for the pattern) to isolate Jackson 3 behavior from any operator/cache wiring.
