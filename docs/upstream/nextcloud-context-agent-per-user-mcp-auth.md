# Draft upstream issue — Nextcloud Context Agent per-user MCP auth

**Target repo:** [`nextcloud/context_agent`](https://github.com/nextcloud/context_agent)
**Status:** Draft. **Not yet filed.** Verify empirically against a running Nextcloud + Context Agent before posting upstream.
**Date drafted:** 2026-05-15
**Filed as:** _(fill in URL after `gh issue create`)_

## Why we held off filing

Filing an upstream issue with concrete code claims requires the claims to be airtight. The current behaviour of `ex_app/lib/all_tools/mcp.py` was inferred from reading the source on `main` (commit unspecified at draft time). Before posting:

- Confirm with a running Nextcloud + Context Agent + a test MCP server that user identity is in fact NOT propagated outbound (intercept the HTTP request from Context Agent → MCP server with mitmproxy / a logging endpoint, check what `Authorization` and other headers arrive).
- If the empirical check shows Context Agent already does *some* form of user-token forwarding that the source review missed, rewrite the issue with the corrected baseline before posting.
- Pin the commit / version of `nextcloud/context_agent` referenced in the issue body so maintainers can match against the same tree.

## Source code observation (basis for the issue)

`ex_app/lib/all_tools/mcp.py`, the entire outbound MCP loader (~30 lines):

```python
async def get_tools(nc: AsyncNextcloudApp):
    mcp_json = await nc.appconfig_ex.get_value("mcp_config", "{}")
    mcp_config = json.loads(mcp_json)
    server = MultiServerMCPClient(mcp_config)
    tools = await asyncio.wait_for(server.get_tools(), timeout=120)
    ...
```

- `appconfig_ex` is admin-tenant config; no `preferences_ex` (per-user) path.
- `nc` carries the calling user's identity but is never threaded into the `MultiServerMCPClient` construction.
- Auth is whatever static `headers` the admin put in `mcp_config` — same token for every user.

## Draft issue text (paste-ready)

**Title:** Support per-user authentication when calling outbound MCP servers

**Body:**

---

## Problem

When Context Agent calls an external MCP server (admin-registered via `mcp_config`),
all requests from all Nextcloud users go through with the **same static tenant
credential** configured by the admin. There is no propagation of the calling user's
identity to the downstream MCP server.

This makes Context Agent unsuitable for any external MCP server whose tools must
respect per-user permissions — e.g.:

- A scheduling system where each user has their own calendar / resources / read scope
- A document store with per-user access control
- Any line-of-business app where "user A booking on behalf of user B" is incorrect
  behaviour

In all such cases, the entire Nextcloud tenant currently acts as a single identity
to the downstream MCP server, defeating the downstream's permission model.

## Code reference

The entire outbound MCP loader is `ex_app/lib/all_tools/mcp.py`:

```python
async def get_tools(nc: AsyncNextcloudApp):
    mcp_json = await nc.appconfig_ex.get_value("mcp_config", "{}")
    mcp_config = json.loads(mcp_json)
    server = MultiServerMCPClient(mcp_config)
    tools = await asyncio.wait_for(server.get_tools(), timeout=120)
    ...
```

Three issues:

1. `appconfig_ex` is admin-tenant config; there's no per-user config path.
2. The calling user's identity (available via `nc`) is not threaded into the
   `MultiServerMCPClient` construction.
3. Whatever `Authorization` header is in `mcp_config` is static — no token forwarding,
   no OBO, no header injection per request.

## Proposed solution

Extend the per-server entry in `mcp_config` with an `auth` discriminator. Three
backwards-compatible modes (default = current behaviour):

```json
{
  "rapla": {
    "url": "https://rapla.example.com/mcp",
    "transport": "streamable_http",
    "auth": {
      "mode": "per_user_bearer",
      "preference_key": "rapla_mcp_token"
    }
  }
}
```

| `auth.mode` | Behaviour | Implementation sketch |
|---|---|---|
| `static` (default, == today) | Use the `headers` block as-is for every request | unchanged |
| `per_user_bearer` | Look up `preferences_ex.get_value(auth.preference_key)` for the calling user; inject as `Authorization: Bearer <value>`. Add a personal-settings UI in the Context Agent app where each user pastes their own token | new |
| `forward_user_token` | Copy the user's incoming Nextcloud session bearer token onto the outbound request | new |
| `obo` (OAuth 2.0 token exchange, RFC 8693) | Use Context Agent's service credentials + the user's session to exchange for a downstream-scoped token at the configured `token_endpoint`. Inject the resulting access token. | new (requires shared IdP like Keycloak) |

The first two are ~30 lines of code each; the OBO option is larger but standards-based.

## Why this matters beyond one app

Per-user permission propagation is the difference between Context Agent being a
demo-quality MCP host and a production-quality one. Any external MCP server with a
real permission model needs this.

The "forward user token" mode in particular is essentially free — and it makes
**all federated SSO setups work correctly** (Keycloak / Shibboleth / Entra), because
the downstream MCP server can validate the same token its IdP issued.

## Use case I'm working on

[rapla](https://github.com/rapla/rapla) — open-source resource scheduling. Each
user can read/book a different subset of resources based on rapla's own permission
model. With the current Context Agent behaviour, exposing rapla as a Nextcloud MCP
tool means every Nextcloud user shares one rapla identity, which silently grants
them access to resources they shouldn't see.

(rapla already supports per-user API keys + IdP federation via Spring Authorization
Server, so it's ready to consume any of the three proposed `auth.mode` options
immediately.)

## Happy to PR this

Comfortable doing the `per_user_bearer` and `forward_user_token` implementations
if there's interest — both are small, additive, backwards-compatible. OBO can
follow as a separate PR.

---

## Filing checklist

When ready to post (after empirical verification):

- [ ] Verify the source-code claim against the latest `main` of `nextcloud/context_agent`.
- [ ] Pin the commit hash / file URL in the body so the issue ages well.
- [ ] Empirical sanity-check: Context Agent → test MCP server → confirm headers received are the static admin token, no user identity forwarded.
- [ ] (Optional) Soften / remove the "Use case I'm working on" rapla mention if you want a generic framing.
- [ ] (Optional) Adjust the "Happy to PR this" closing if you don't want to commit to authoring.
- [ ] `gh issue create -R nextcloud/context_agent --title "Support per-user authentication when calling outbound MCP servers" --body-file <this-section>`
- [ ] Record the resulting issue URL in the **Filed as:** field at the top of this doc.
- [ ] Cross-link from [PRD 035](../prd/done/035-graphql-foundations.md)'s Nextcloud track (Track 5 once added).

## Cross-references

- [PRD 035](../prd/done/035-graphql-foundations.md) — rapla MCP server. The use case driving this issue.
- AGENTS.md §12 — the permission-leak invariant this gap would force rapla to violate.
- `ex_app/lib/all_tools/mcp.py` — the file that needs the change upstream.
- [App: Context Agent — Nextcloud Administration Manual](https://docs.nextcloud.com/server/stable/admin_manual/ai/app_context_agent.html)
