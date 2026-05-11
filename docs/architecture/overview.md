# Architecture overview

Rapla is a resource-scheduling and event-planning system. A small admin
defines schemas (Dynamic Types) for what counts as a "resource"
(rooms, equipment, persons) and a "reservation" (course, meeting,
exam). End users then book those resources through a Swing client or
a web JNLP launch, and a server enforces conflicts and permissions.

This page is the map. It tells you which module owns what, how the
pieces talk, and where to look next.

> Build & launch lifecycle (Maven reactor, dev server, Swing client,
> JNLP, signing) lives in [AGENTS.md](../../AGENTS.md). This document
> assumes you can run `mvn -pl rapla-app -am spring-boot:run` and
> connect a Swing client.

---

## The five-module reactor

The repo is one Maven reactor with five sibling modules. The reactor
aggregator is `pom.xml` at the repo root (`artifactId=rapla-aggregator`,
`packaging=pom`). Each module has its own role:

| Module | Role | Key packages |
|---|---|---|
| `rapla-bom` | Parent POM, dependency BOM, plugin config. No code. | — |
| `rapla-core` | Shared layer: entities, facade, framework, scheduler, REST DTOs/endpoint interfaces, plugins (interface side). **No Spring Boot, no Swing.** | `org.rapla.entities`, `org.rapla.facade`, `org.rapla.storage`, `org.rapla.framework`, `org.rapla.rest`, `org.rapla.scheduler`, `org.rapla.plugin.*` (interfaces) |
| `rapla-client` | Swing client + presenters. Uses `spring-context` only — **explicit `AnnotationConfigApplicationContext`, NOT `@SpringBootApplication`**. | `org.rapla.client.swing.*`, `org.rapla.components.calendar*`, `org.rapla.plugin.*.client.*` |
| `rapla-server` | Server-side: storage, conflicts, REST handlers, Spring autoconfig, plugin server-side. | `org.rapla.server.*`, `org.rapla.storage.dbsql`, `org.rapla.storage.impl.server`, `org.rapla.plugin.*.server.*` |
| `rapla-app` | The runnable Spring Boot app: `RaplaSpringBootApplication`, `application.yml`, distribution / signing / JNLP webclient. | `org.rapla.server.spring` (the entry-point package) |

Dependency edges: `rapla-server → rapla-client → rapla-core → rapla-bom`.
The server depends on the client because of one shared abstraction
(`RaplaBuilder` and the `abstractcalendar` plugin) — see PRD 005 D3
in `docs/prd/done/005-multi-module-split.md`. This is a known compromise.

`custom/` is intentionally out of the reactor (its WAR-overlay shape
is being rethought; future PRD).

---

## Runtime topology

```
   ┌──────────────────────────────┐         ┌────────────────────────────┐
   │  Swing client (rapla-client) │         │  rapla-app                 │
   │  ─────────────────────────── │         │  Spring Boot 3.2 / Tomcat 10│
   │  AnnotationConfigContext     │         │  (rapla-server beans)       │
   │  RemoteOperator (REST proxy) │ ◀─────▶ │  RemoteStorageImpl          │
   │  LocalCache (subset)         │ HTTP/   │  LocalAbstractCachable…     │
   │  RaplaFacade / FacadeImpl    │  JSON   │  FileOperator | DbsqlOpera… │
   │  Swing UI on EDT             │  + JWT  │  ConflictFinder             │
   └──────────────────────────────┘         │  RaplaSQL ↔ HSQLDB / MySQL  │
                                            └────────────────────────────┘
                                                       │
                                                       ▼
                                           ┌──────────────────────┐
                                           │  rapla.xml (file)    │
                                           │  or JDBC database    │
                                           └──────────────────────┘
```

There is also a **headless web/JNLP path**: `rapla-app` serves
`raplaclient.jnlp` from a signed `webclient/` directory; OpenWebStart
on Windows downloads and launches the same Swing client.
See `docs/development.md` and PRD 018 for the JNLP / classloader story.

The server-to-client transport is **polling**, not push: the client
periodically calls `RemoteOperator.refreshAsync()` and the server
returns an incremental `UpdateEvent`. See [flows.md](flows.md) for
the cycle.

---

## Layering inside rapla-core

`rapla-core` is the spine. It has six sub-packages worth knowing:

```
org.rapla.entities          — domain types (User, Reservation, Allocatable, …)
   ├── domain               — Reservation, Appointment, Allocatable, Permission
   ├── dynamictype          — DynamicType, Attribute, Classification (schema)
   ├── configuration        — Preferences, RaplaConfiguration, CalendarModelConfiguration
   ├── storage              — Entity / EntityResolver / ReferenceInfo
   └── extensionpoints      — FunctionFactory (schema-attribute formulas)
org.rapla.facade            — RaplaFacade, ClientFacade, ModificationEvent, Conflict
org.rapla.storage           — StorageOperator, LocalCache, UpdateEvent, UpdateResult
   ├── dbrm                 — RemoteOperator (REST client transport)
   ├── impl                 — AbstractCachableOperator (base)
   └── xml                  — XML reader/writer (file storage on the server)
org.rapla.framework         — Logger, RaplaException, RaplaLocale, lifecycle
org.rapla.rest              — REST DTO + endpoint interfaces (JAX-RS shapes,
                              implemented by Spring on the server side)
org.rapla.scheduler         — Promise, CommandScheduler, RxJava3 wiring
org.rapla.plugin.*          — Plugin **interfaces** (server/client classes
                              live in their respective modules)
```

The internal subpackages (`*.internal.*`) hold the implementations.
The split is "interface in the public package, impl in `internal`."
That is the same pattern as Eclipse APIs and is followed throughout.

---

## Where to look next

| If you want to know… | Read |
|---|---|
| What the data model looks like | [domain-model.md](domain-model.md) |
| How admins extend the schema | [dynamic-types.md](dynamic-types.md) |
| How double-booking is detected | [conflicts-and-events.md](conflicts-and-events.md) and [../conflict-detection.md](../conflict-detection.md) |
| What happens when a user opens an event | [reservation-edit.md](reservation-edit.md) |
| How permissions are evaluated | [permissions.md](permissions.md) |
| How to add a plugin | [extension-points.md](extension-points.md) |
| The cross-cutting flows (login, query, store, refresh) | [flows.md](flows.md) |
| How to actually run the thing | [../../AGENTS.md](../../AGENTS.md) §8–§9 |

## Spring DI: client vs server (a recurring footgun)

Two different DI patterns coexist in the codebase. **Get this right or
your bean won't be picked up:**

- **Client (`rapla-client`)** uses `@ComponentScan` of `org.rapla.client.*`
  and `org.rapla.plugin.*` in
  `rapla-client/src/main/java/org/rapla/client/spring/SwingClientConfig.java`.
  Adding `@Service` (or `@Service("id")` for `Map<String, T>` consumers)
  to a class in those packages is enough.

- **Server (`rapla-server` + `rapla-app`)** uses `@SpringBootApplication`
  on `org.rapla.server.spring`, which only scans that package. Server
  internals (`org.rapla.server.internal.*`, `org.rapla.plugin.*.server.*`)
  are wired explicitly in `@Bean` factory methods inside
  `ServerCoreConfig` / `ServerServiceConfig`. Adding `@Service` to a
  server-internal class is **dead code** — Spring won't see it.

This split is documented in `AGENTS.md` §4. Keep it in mind whenever
you trace bean wiring.

## See also

- [AGENTS.md](../../AGENTS.md) — build, test, server lifecycle, hard rules
- [docs/conflict-detection.md](../conflict-detection.md) — overlap algorithm in detail
- [docs/development.md](../development.md) — WSL2 / Windows / OpenWebStart specifics
- PRD 005 (multi-module split), PRD 008 (sync server / async client),
  PRD 011 (Spring Boot 4 + Jackson 3) for historical context
