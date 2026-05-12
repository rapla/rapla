# Rapla Authentication

Rapla supports two authentication paths against the bundled Spring
Authorization Server:

| Path | Used by | Credentials |
|---|---|---|
| `POST /auth/login` (legacy) | Swing client default, REST clients | username + password → HMAC JWT |
| `GET /oauth2/authorize` + `POST /oauth2/token` (OAuth 2.0 + PKCE) | Swing "Sign in with browser…" button, future Angular SPA | redirected through system browser → RSA JWT |

Both paths return RSA-signed JWTs (RS256) issued with the same key —
Spring Authorization Server's `JWKSource`. The resource server validates
them through a single decoder in `JwtConfig.java`. Tokens issued via
`/auth/login` and via `/oauth2/token` are structurally interchangeable
once issued: same `sub` claim (user UUID), same signature algorithm,
same key.

This document covers configuring authentication for a rapla
deployment. For OAuth-specific architecture see [PRD 029](prd/029-swing-oauth-login.md).

## Quick start (default deployment)

Defaults are correct for a single rapla server at `https://rapla.yourdomain.com`:

```bash
java -jar rapla-2.1-SNAPSHOT.jar
```

The Swing client's "Sign in with browser…" button works out of the
box. End-users log in via the form at `/rapla/login`, the auth server
redirects to a loopback URL on their machine, and the Swing client
completes the token exchange. No per-deployment OAuth config required.

Once an Angular SPA is added at the same origin, its
`/auth/callback` redirect is auto-accepted by the same-origin
validator — also zero-config.

The only authentication concern for a typical deployment is replacing
the default empty admin password (see "Replacing the default admin"
below).

## Configuration reference

All properties live under `rapla.oauth.*` in `application.yml`. Each can
be overridden with the matching env var.

| Property | Env var | Default | Meaning |
|---|---|---|---|
| `rapla.oauth.enabled` | `RAPLA_OAUTH_ENABLED` | `true` | Master toggle for the OAuth flow. When `false`, the Swing "Sign in with browser…" button is hidden and the discovery endpoint reports `enabled: false`. Legacy `/auth/login` still works. |
| `rapla.oauth.client-id` | `RAPLA_OAUTH_CLIENT_ID` | `rapla-client` | OAuth client id. Both Swing and Angular use this single id; their redirect URIs differ. |
| `rapla.oauth.scopes` | `RAPLA_OAUTH_SCOPES` | `openid,profile` | Scopes granted to issued tokens. Comma-separated. |
| `rapla.oauth.show-paste-fallback` | `RAPLA_OAUTH_SHOW_PASTE_FALLBACK` | `false` | Show a "paste callback URL here" dialog alongside the browser launch. Enable only for environments where the automatic loopback redirect can't reach the client. |
| `rapla.oauth.allow-wsl-bridge-redirects` | `RAPLA_OAUTH_ALLOW_WSL_BRIDGE_REDIRECTS` | `true` | **Dev-only**: accept any port for redirect URIs in `172.16.0.0/12` (Hyper-V WSL2 bridge). Lets developers run the Swing client in WSL2 without enabling mirrored networking. **Set to `false` in production.** |
| `rapla.oauth.allow-same-origin-redirects` | `RAPLA_OAUTH_ALLOW_SAME_ORIGIN_REDIRECTS` | `true` | Accept any redirect URI whose scheme/host/port match the auth-server request's public origin (honoring `X-Forwarded-*`), provided the path matches a registered URI. Lets Angular at any deployment hostname auto-register. Safe with PKCE — recommend keeping on. |

### Spring redirect URIs

The "registered" redirect URIs live under
`spring.security.oauth2.authorizationserver.client.rapla-client.registration.redirect-uris`:

```yaml
redirect-uris:
  - http://127.0.0.1/login/oauth2/code/rapla   # Swing loopback (end users)
  - http://localhost/auth/callback             # Angular dev (any port)
  - http://127.0.0.1/auth/callback             # Angular dev alt
```

Spring matches **any port** on loopback hosts (`127.0.0.1`, `localhost`,
`::1`). For non-loopback hosts, exact match required. To register
additional URIs in production:

```bash
export SPRING_SECURITY_OAUTH2_AUTHORIZATIONSERVER_CLIENT_RAPLA-CLIENT_REGISTRATION_REDIRECT-URIS_3=https://rapla.yourdomain.com/auth/callback
```

(Use `_0`, `_1`, `_2`, `_3` etc. to append to the array.)

In practice you usually don't need this because
`rapla.oauth.allow-same-origin-redirects=true` auto-accepts URIs at
the deployment's own origin.

## Deployment recipes

### Single rapla server at a public URL

```bash
SERVER_PORT=8051
RAPLA_FILE_DATASOURCES_RAPLAFILE=/var/lib/rapla/data.xml

# OAuth: hardening on
RAPLA_OAUTH_ALLOW_WSL_BRIDGE_REDIRECTS=false

# Logging
LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY=INFO
```

Reverse proxy in front of port 8051 with TLS termination. Make sure the
proxy sets `X-Forwarded-Proto` and `X-Forwarded-Host` so the
auth-server's discovery endpoint emits the public URL and the
same-origin validator sees the correct public origin. Nginx/traefik/caddy
all do this by default.

### Behind a reverse proxy at a path other than `/rapla`

```bash
SERVER_SERVLET_CONTEXT_PATH=/scheduling
```

The discovery endpoint emits `authorizeUrl`/`tokenUrl` based on the
public URL it sees on incoming requests, so this picks up
automatically.

### Multiple deployment surfaces (Swing + Angular)

Both use the same `rapla-client` OAuth client. No additional
configuration beyond the defaults — the same-origin validator handles
the Angular redirect URI, and the loopback rule handles the Swing one.

### Restricted-network environments (corporate firewall, locked-down Defender)

Some users may hit "This site can't be reached" after signing in
because the system browser can't open a connection back to the Swing
client's local listener. For these deployments:

```bash
RAPLA_OAUTH_SHOW_PASTE_FALLBACK=true
```

This makes the paste-the-callback-URL dialog appear ~12 s after the
browser launch. If the automatic redirect succeeded the dialog never
opens; if it didn't, the user can paste the URL from their browser
and the flow completes via in-process HTTP.

## Replacing the default admin

The bundled `data/data.xml` ships with one admin account: **username
`admin`, empty password**. This is intentional for first-time dev use
but should not survive into production. Two options:

1. **Edit `data.xml`** before first launch: set a password element on
   the admin user.
2. **Through the running Swing client**: log in as admin, open the
   user editor, change the password, log out.

There is no production-mode lockout enforcing a non-empty admin
password today — that's planned hardening (see issue/PRD on
production-mode hardening when it lands).

## Verifying the setup

After launching the server, hit the discovery endpoint:

```bash
curl -s http://localhost:8051/rapla/auth/oauth/config | python3 -m json.tool
```

Expected response (defaults):

```json
{
  "enabled": true,
  "clientId": "rapla-client",
  "authorizeUrl": "http://localhost:8051/rapla/oauth2/authorize",
  "tokenUrl": "http://localhost:8051/rapla/oauth2/token",
  "scopes": ["openid", "profile"],
  "showPasteFallback": false
}
```

For a deployment at `https://rapla.yourdomain.com` behind a proxy,
the URLs in `authorizeUrl`/`tokenUrl` should be the public HTTPS URLs,
not the internal localhost ones. If you see `localhost` in production,
your proxy isn't passing `X-Forwarded-*` headers — fix that.

## Common issues

### "Whitelabel error page" after clicking Sign in with browser

The auth server rejected the redirect URI. Cause: deployment-specific
redirect URI isn't on the registered list AND doesn't match any
loopback / WSL bridge / same-origin allowance. Fix: register the URI
explicitly with `SPRING_SECURITY_OAUTH2_…_REDIRECT-URIS_n=…`.

### "This site can't be reached" after sign-in

The system browser can't open the loopback URL on the user's machine.
Causes vary — WSL2 NAT, corporate firewall blocking inbound localhost,
some antivirus configurations. Workaround:
`RAPLA_OAUTH_SHOW_PASTE_FALLBACK=true`. Long-term: investigate the
specific network restriction.

### Token issued by `/oauth2/token` rejected as Bearer

The composite JWT decoder (see `JwtConfig`) accepts both HMAC tokens
from `/auth/login` and RSA tokens from `/oauth2/token`. If RSA tokens
fail validation, check that the auth server is wired in (it is by
default — see `AuthorizationServerConfig`).

### After server restart, all users are logged out

Expected (today). The auth server's RSA keypair is regenerated on
every startup, invalidating all previously-issued tokens — both
`/auth/login` and `/oauth2/token` use this same in-memory keypair
as of the 2026-05-12 token unification. Persisting the keypair across
restarts is planned hardening (see PRD 026 §5).

## External IdP (Keycloak, Azure AD, Google Workspace)

Not supported today. The bundled Spring Authorization Server is the
only IdP. Planned for Phase 2 of [PRD 029](prd/029-swing-oauth-login.md);
the discovery endpoint already exposes the right shape to be repointed
at an external IdP via additional env vars (`RAPLA_OAUTH_AUTHORIZE_URL`,
`RAPLA_OAUTH_TOKEN_URL`, `RAPLA_OAUTH_JWK_SET_URI`).
