---
name: storage-inspection
description: Use when debugging "is the data wrong on the wire or in storage?" — inspecting rapla's backing store directly without going through the REST API or Swing client. Covers the three storage backends rapla supports out of the box (XML file via FileOperator, embedded HSQLDB, MariaDB/PostgreSQL via DBOperator) and which inspection tool fits each. Skip when the question is "what does the server return?" — that's the `api-testing` skill; or "what does the client display?" — that's `angular-frontend` / Swing testing.
---

# Inspecting rapla's storage directly

Rapla supports three storage backends. Configuration lives in `rapla-app/src/main/resources/application.yml` — check there first to know which one is active.

| Backend | Configured by | Default in dev? |
|---|---|---|
| XML file (`FileOperator`) | `rapla.file-datasources.raplafile: data/data.xml` | **Yes** |
| Embedded HSQLDB (`DBOperator`) | `rapla.db-datasources.raplafile: {url: jdbc:hsqldb:..., …}` | No (opt-in via env override) |
| MariaDB / PostgreSQL / MySQL (`DBOperator`) | Same key, different JDBC URL | No (production override) |

## Which one is active?

```bash
grep -E 'file-datasources|db-datasources' /home/chris/git/rapla/rapla-app/src/main/resources/application.yml
# … or check env overrides if the deployment uses them:
env | grep -i 'RAPLA_(DB|FILE)_DATASOURCES'
```

If you see `file-datasources` → XML mode. If `db-datasources` → check the JDBC URL prefix (`jdbc:hsqldb:`, `jdbc:mariadb:`, `jdbc:postgresql:`).

## Backend 1 — XML (`data/data.xml`)

This is the dev default. The file is small (~8 KB on a fresh checkout, < 1 MB even with realistic test data). Just read it.

```bash
# Whole file (read it as text — small enough)
cat /home/chris/git/rapla/data/data.xml
```

For structured queries install `libxml2-utils` (one-time):

```bash
sudo apt install -y libxml2-utils         # provides xmllint
```

Then:

```bash
# Find a user by username
xmllint --xpath '//*[local-name()="user"][@username="admin"]' data/data.xml

# Find resource IDs of a specific type
xmllint --xpath '//*[local-name()="dynamictype"][@key="room"]//*[local-name()="resource"]/@id' data/data.xml

# Count reservations
xmllint --xpath 'count(//*[local-name()="reservation"])' data/data.xml
```

The `local-name()` predicate avoids namespace gymnastics — rapla's XML is namespaced and direct `//user` doesn't match without it.

**Don't edit `data.xml` by hand to "fix" a test failure** — the file is the operator's authoritative store; round-trip through `FileOperator.dispatch()` to mutate. Edits there bypass the entity-permission and conflict-detection layers and corrupt the working set.

## Backend 2 — Embedded HSQLDB

When `application.yml` is overridden with a `jdbc:hsqldb:…` URL, rapla creates the DB on first run. To probe it from the agent:

### Option A — `sqltool` (HSQL's bundled CLI)

HSQL ships a JDBC-capable CLI inside `hsqldb.jar` which is already on rapla's runtime classpath. Find the jar in the Maven local repo and invoke it:

```bash
HSQL_JAR=$(find ~/.m2/repository/org/hsqldb -name 'hsqldb-*.jar' | sort -V | tail -1)
java -cp "$HSQL_JAR" org.hsqldb.cmdline.SqlTool \
  --inlineRc="url=jdbc:hsqldb:hsql://localhost/rapla,user=sa,password=" \
  --sql 'SELECT COUNT(*) FROM USERS;'
```

(URL/user/password come from `application.yml`'s `db-datasources` block.)

### Option B — dbhub MCP

dbhub doesn't *officially* support HSQL but in practice HSQL speaks enough of standard SQL that the JDBC driver works. Not battle-tested for this; prefer `sqltool` for HSQL.

## Backend 3 — MariaDB / PostgreSQL / MySQL via dbhub MCP

For production-style deployments where rapla is backed by MariaDB or Postgres, register **dbhub** as an MCP server. dbhub is the right shape: zero-dependency, multi-driver, read-only mode optional.

### One-time install (when a real DB is up)

```bash
# Read-only example for Postgres — adjust DSN to your deployment:
claude mcp add rapla-db npx '@bytebase/dbhub@latest' -- \
  --transport stdio \
  --dsn "postgres://rapla:secret@localhost:5432/rapla?sslmode=disable" \
  --readonly

# MariaDB / MySQL DSN:
#   --dsn "mysql://rapla:secret@localhost:3306/rapla"
```

Then `claude mcp list` should show `rapla-db: ✓ Connected`. New tools surface as `mcp__rapla-db__*` — typically `query`, `list_tables`, `describe_table`, `read_schema`.

### Usage pattern

```
Agent → mcp__rapla-db__list_tables                  → table inventory
Agent → mcp__rapla-db__describe_table USERS         → column list, indexes
Agent → mcp__rapla-db__query "SELECT … FROM USERS"  → rows
```

**Always start with `--readonly`** unless you explicitly need to mutate. dbhub honors it (rejects writes at the driver level). For a write-capable session, register a second MCP with a different name (`rapla-db-rw`) so the read tools are still the default.

### When to use dbhub vs. `psql` / `mariadb` CLI

- **dbhub** when you want structured tool output the agent can iterate on (list of rows as JSON, schema as structured data).
- **`psql` / `mariadb`** when you're prototyping a query interactively or doing one-off probes — `gh`-style raw output is sometimes faster to eyeball.

## Cross-backend invariants

These hold regardless of which backend is active:

- **Entity IDs are the same shape** — UUID strings produced by `IdCreator`. Don't assume a SQL `BIGINT id` column; `DBOperator` uses `VARCHAR` for IDs.
- **The `local_id` column in JDBC mode mirrors the XML `id` attribute** — same value, same lookup semantics.
- **Permission filtering happens in Java, not SQL.** Don't expect a `WHERE owner_id = …` clause to enforce read scope — `PermissionController.canRead(entity, user)` is the gate. AGENTS.md §12 applies whether you're reading via REST or via dbhub.
- **`FileOperator` and `DBOperator` are interchangeable runtime paths over the same in-memory `LocalCache`** — once data is loaded, the operator difference is invisible to the rest of the code. Storage-level bugs (deserialization, ID resolution) reproduce identically across backends, which is why `FacadeTestSupport` uses the XML path even when testing storage-tier code.

## Footguns

- **Don't run dbhub against the file-mode dev DB.** `application.yml` says `file-datasources` → there is no JDBC endpoint, dbhub will fail to connect with `Connection refused`. Switch to HSQL/MariaDB mode first if you want JDBC tooling against dev.
- **`data.xml` writes are NOT atomic.** Don't read it while rapla is running and `dispatch()` is mid-flight — you'll get a torn file. Use `curl /api/storage/resources` for the live view.
- **HSQL embedded mode locks the data dir.** If `sqltool` says "Database lock acquisition failure", rapla itself is holding the lock — connect via `jdbc:hsqldb:hsql://localhost/rapla` (server mode) instead of `jdbc:hsqldb:file:…` (embedded).
