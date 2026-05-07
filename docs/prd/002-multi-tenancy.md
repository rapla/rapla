# PRD 002: Multi-Tenancy Support

**Status:** draft
**Date:** 2026-05-05

## Goal

Allow a single Rapla server instance to serve multiple independent tenants, each with its own data (reservations, resources, users, dynamic types, preferences). Tenant identity is resolved from the URL path, following the same pattern as Java EE context-path deployment (e.g., `/rapla/tenant1/...`, `/rapla/tenant2/...`). Each tenant gets a dedicated `CachableStorageOperator` with its own `LocalCache` and storage backend (file or database).

This enables hosting multiple organisations on one Rapla server without sharing data, without deploying separate instances, and without adding a `tenant_id` column to every entity table.

## Current Architecture (Single-Tenant)

```
HTTP Request
  → Servlet/Controller
    → FacadeImpl (singleton)
      → CachableStorageOperator (singleton)
        → LocalCache (one in-memory store)
          → FileOperator or DBOperator (one datasource)
```

Key singletons that bind the server to one tenant:

| Component | Scope | File |
|-----------|-------|------|
| `FacadeImpl` | `@Singleton` | `facade/internal/FacadeImpl.java` |
| `CachableStorageOperator` | `@Singleton` via `ServerStorageSelector` | `server/internal/ServerStorageSelector.java` |
| `LocalCache` | Created per operator | `storage/LocalCache.java` |
| `ServerStorageSelector` | `@Singleton` Provider | `server/internal/ServerStorageSelector.java` |
| `ServerContainerContext` | Holds named datasource maps, but `getMainDbDatasource()` returns hardcoded `"jdbc/rapladb"` | `server/internal/ServerContainerContext.java` |
| `UpdateDataManagerImpl` | Broadcasts changes to connected clients (one operator) | `server/internal/UpdateDataManagerImpl.java` |
| `RemoteSessionImpl` | Per-user session, no tenant awareness | `server/internal/RemoteSessionImpl.java` |

The existing `ServerContainerContext.dbDatasources` and `fileDatasources` maps already hold multiple named datasources, but only the "main" one (`"jdbc/rapladb"` or `"raplafile"`) is wired to a live operator. `ImportExportManagerImpl` is the sole consumer of non-main datasources — it creates a temporary operator, imports data, then discards it.

## Proposed Architecture (Multi-Tenant)

```
HTTP Request (/rapla/auth/login)                     ← default tenant (backward compat)
  → TenantFilter (no tenant prefix → default tenant)
    → TenantContext (ThreadLocal)
      → TenantAwareFacade → default's FacadeImpl
        → default's CachableStorageOperator → LocalCache → FileOperator/DBOperator

HTTP Request (/acme/rapla/api/resources)             ← named tenant
  → TenantFilter (extracts "acme" from path)
    → TenantContext (ThreadLocal)
      → TenantAwareFacade → acme's FacadeImpl
        → acme's CachableStorageOperator → LocalCache → FileOperator/DBOperator

HTTP Request (/globex/rapla/auth/login)              ← named tenant
  → TenantFilter (extracts "globex" from path)
    → TenantContext (ThreadLocal)
      → TenantAwareFacade → globex's FacadeImpl
        → globex's CachableStorageOperator → LocalCache → FileOperator/DBOperator
```

### Tenant Resolution: Path-Based

Tenant identity is the first path segment before `/rapla/`. No fixed `context-path` — the `TenantFilter` handles routing.

| URL | Tenant ID | Remaining path |
|-----|-----------|----------------|
| `/rapla/auth/login` | *(none — default)* | `/rapla/auth/login` |
| `/acme/rapla/api/resources` | `acme` | `/rapla/api/resources` |
| `/globex/rapla/auth/login` | `globex` | `/rapla/auth/login` |
| `/admin/rapla/tenants` | `admin` (system) | `/rapla/tenants` |

**Backward compatibility:** When no tenant prefix is present (i.e. path starts with `/rapla/`), the request routes to the default tenant. Existing deployments with `context-path: /rapla` continue to work unchanged.

Spring Boot controller mappings use `/{tenant}/rapla/...` with an additional no-tenant variant:

```java
@PostMapping({"/rapla/auth/login", "/{tenant}/rapla/auth/login"})
public LoginResponse login(@PathVariable(required = false) String tenant,
                           @RequestBody LoginRequest req) { ... }
```

The `TenantFilter` resolves the tenant before the controller:
1. If path starts with `/rapla/` → default tenant (no prefix)
2. If path matches `/{id}/rapla/...` → lookup tenant by `id`
3. Strip the tenant prefix, forward to the controller's `/rapla/...` mapping

This mirrors the old Jetty multi-context pattern where each context had its own root path.

### Core Components

#### 1. `TenantRegistry` — the operator pool

Holds one `TenantContext` per registered tenant. A `TenantContext` bundles:

- `CachableStorageOperator` (with its own `LocalCache`)
- `FacadeImpl` (wrapping that operator)
- `UpdateDataManagerImpl` (for push notifications to that tenant's clients)
- Tenant-specific `Preferences` (loaded from the operator)

```java
public class TenantRegistry {
    private final Map<String, TenantContext> tenants = new ConcurrentHashMap<>();

    public TenantContext getTenant(String tenantId) { ... }
    public void registerTenant(String tenantId, StorageConfig config) { ... }
    public void removeTenant(String tenantId) { ... }
    public Collection<String> getTenantIds() { ... }
}

public class TenantContext {
    private final String tenantId;
    private final CachableStorageOperator operator;
    private final FacadeImpl facade;
    private final UpdateDataManagerImpl updateManager;
    // ...
}
```

#### 2. `TenantFilter` — request-scoped tenant resolution

A Spring `OncePerRequestFilter` that:

1. Checks if path starts with `/rapla/` → default tenant (no prefix)
2. Otherwise extracts tenant ID from the first path segment (e.g. `/acme/rapla/...` → `acme`)
3. Looks up the `TenantContext` in `TenantRegistry`
4. Sets it in a `TenantContextHolder` (ThreadLocal)
5. Forwards the request with the tenant prefix stripped (so controllers see `/rapla/...` paths)

```java
@Component
public class TenantFilter extends OncePerRequestFilter {
    private final TenantRegistry tenantRegistry;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) {
        String tenantId = extractTenantId(request);
        TenantContext ctx = tenantRegistry.getTenant(tenantId);
        if (ctx == null) {
            response.sendError(404, "Unknown tenant: " + tenantId);
            return;
        }
        TenantContextHolder.set(ctx);
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
```

#### 3. `TenantContextHolder` — ThreadLocal access

```java
public class TenantContextHolder {
    private static final ThreadLocal<TenantContext> CURRENT = new ThreadLocal<>();

    public static TenantContext get() { return CURRENT.get(); }
    public static void set(TenantContext ctx) { CURRENT.set(ctx); }
    public static void clear() { CURRENT.remove(); }
}
```

#### 4. `TenantAwareFacade` — `RaplaFacade` implementation that delegates

Replaces the singleton `FacadeImpl` bean. Implements `RaplaFacade` by delegating every call to the current tenant's `FacadeImpl`:

```java
@Service
@Primary
public class TenantAwareFacade implements RaplaFacade {
    @Override
    public User getUser(String id) throws EntityNotFoundException {
        return TenantContextHolder.get().getFacade().getUser(id);
    }
    // ... delegates all methods
}
```

This means existing code that injects `RaplaFacade` continues to work unchanged — it just talks to the current request's tenant.

#### 5. `TenantStorageSelector` — replaces `ServerStorageSelector`

Instead of creating one operator at startup, creates operators on demand:

```java
@Service
public class TenantStorageSelector {
    private final TenantRegistry registry;
    private final OperatorFactory operatorFactory;

    public TenantStorageSelector(TenantRegistry registry,
                                  OperatorFactory operatorFactory) {
        this.registry = registry;
        this.operatorFactory = operatorFactory;
    }

    public CachableStorageOperator getOperator(String tenantId) {
        return registry.getTenant(tenantId).getOperator();
    }

    public void initTenant(String tenantId, StorageConfig config) {
        CachableStorageOperator op = operatorFactory.create(config);
        registry.registerTenant(tenantId, new TenantContext(tenantId, op, ...));
    }
}
```

### Configuration

Each tenant's storage is configured in `application.yml`:

```yaml
rapla:
  tenants:
    acme:
      storage-type: file
      file-datasource: acmedata
      file-datasources:
        acmedata: /var/rapla/tenants/acme
    globex:
      storage-type: db
      db-datasource: globexdb
      db-datasources:
        globexdb:
          url: jdbc:postgresql://db:5432/globex_rapla
          username: rapla
          password: secret
          driver-class-name: org.postgresql.Driver
    initech:
      storage-type: db
      db-datasource: initechdb
      db-datasources:
        initechdb:
          url: jdbc:mysql://db:3306/initech_rapla
          username: rapla
          password: secret
          driver-class-name: com.mysql.cj.jdbc.Driver
```

This maps to a `@ConfigurationProperties` class:

```java
@ConfigurationProperties(prefix = "rapla")
public class RaplaServerProperties {
    private Map<String, TenantConfig> tenants = new LinkedHashMap<>();
    // existing fields retained for backward compatibility
}

public class TenantConfig {
    private String storageType; // "file" or "db"
    private String dbDatasource;
    private Map<String, DataSourceProperties> dbDatasources;
    private String fileDatasource;
    private Map<String, String> fileDatasources;
}
```

### Authentication

Each tenant has its own user store (in its own `LocalCache`). Authentication flow:

1. Client sends `POST /acme/rapla/auth/login` with credentials (or `POST /rapla/auth/login` for default tenant)
2. `AuthController` extracts tenant from `@PathVariable` (or uses default)
3. Looks up tenant's `FacadeImpl` and validates credentials against that tenant's user store
4. Issues JWT with claims: `sub` (username), `tid` (tenant ID), `roles` (user's groups)
5. Subsequent requests include JWT; `TenantFilter` validates that the JWT's `tid` matches the URL tenant

```java
public class TenantJwtAuthenticationConverter extends JwtAuthenticationConverter {
    @Override
    public Authentication convert(Jwt jwt) {
        String tokenTenant = jwt.getClaimAsString("tid");
        String pathTenant = TenantContextHolder.get().getTenantId();
        if (!tokenTenant.equals(pathTenant)) {
            throw new TenantMismatchException(tokenTenant, pathTenant);
        }
        return super.convert(jwt);
    }
}
```

### Startup Sequence

1. Spring Boot reads `application.yml`, binds `rapla.tenants` to `RaplaServerProperties`
2. `TenantRegistryInitializer` (`@PostConstruct` or `ApplicationRunner`):
   - Iterates `RaplaServerProperties.tenants`
   - For each tenant, creates a `CachableStorageOperator` via `OperatorFactory`
   - Calls `operator.connect()` (loads data from file/DB)
   - Wraps in `TenantContext` and registers in `TenantRegistry`
3. `TenantFilter` is ready to route requests
4. If no tenants configured, fall back to single-tenant mode (backward compatible)

### Lazy vs Eager Loading

Two strategies for operator creation:

| Strategy | Pros | Cons |
|----------|------|------|
| **Eager** (all at startup) | Immediate error if a tenant's datasource is misconfigured; no cold-start latency on first request | Higher memory usage; slower startup with many tenants |
| **Lazy** (on first request) | Fast startup; lower memory if some tenants are rarely used | First request to each tenant is slow (operator connect = data load); datasource errors surface at runtime |

**Recommendation:** Eager loading with a configurable timeout. If a tenant's operator fails to connect, log the error and mark that tenant as unavailable (return 503 for its requests). This is safer than lazy loading for a production system.

### Impact on Existing Code

| Component | Change | Complexity |
|-----------|--------|------------|
| `RaplaFacade` usages (REST endpoints, plugins) | Replace singleton `FacadeImpl` bean with `TenantAwareFacade` — no API change, just delegates | Low |
| `ServerStorageSelector` | Replaced by `TenantStorageSelector` | Medium |
| `ServerContainerContext` | Replaced by per-tenant config | Medium |
| All REST controllers | Add dual mappings `{"/rapla/...", "/{tenant}/rapla/..."}` with `@PathVariable(required=false) String tenant` | Medium (mechanical) |
| `RemoteSessionImpl` | Store tenant ID in session; validate against request tenant | Low |
| `UpdateDataManagerImpl` | One per tenant (already per-operator) | Low |
| `ImportExportManagerImpl` | Works unchanged — already creates temporary operators | None |
| `FacadeImpl` | Becomes non-singleton; one instance per tenant | Low |
| `CachableStorageOperator` | Already not truly singleton — `ServerStorageSelector` creates it. Just create more of them. | None |
| `LocalCache` | Already per-operator | None |
| `FileOperator` / `DBOperator` | No change — already parameterized by datasource/path | None |
| `application.yml` | Add `rapla.tenants` section | Low |
| Angular frontend | Base URL changes from `/rapla/` to `/{tenant}/rapla/` for multi-tenant deployments (unchanged for single-tenant) | Medium (config change) |
| Swing client | Server URL changes from `http://host:8051/rapla/` to `http://host:8051/{tenant}/rapla/` for multi-tenant (unchanged for single-tenant) | Low |

### Backward Compatibility

If `rapla.tenants` is empty or absent in `application.yml`, the server operates in **single-tenant mode**:

- A default tenant named `"default"` is created from the existing `rapla.file-datasources` or `rapla.db-datasources` config
- All URLs work without a tenant prefix: `/rapla/auth/login`, `/rapla/api/resources` — zero breaking change for existing deployments
- Named-tenant URLs also work: `/default/rapla/auth/login` routes to the same default tenant
- `server.servlet.context-path` can remain unset (or set to `/`); the `TenantFilter` handles both `/rapla/...` and `/{tenant}/rapla/...` patterns

### Memory Considerations

Each tenant's `CachableStorageOperator` loads all entities into its `LocalCache`. Rough estimate per tenant:

| Entity type | Typical count | Memory per entity (est.) | Total |
|-------------|---------------|--------------------------|-------|
| Reservations | 5,000 | 2 KB | 10 MB |
| Allocatables | 500 | 1 KB | 0.5 MB |
| Users | 50 | 0.5 KB | 0.025 MB |
| Dynamic types | 20 | 5 KB | 0.1 MB |
| Appointments | 15,000 | 0.5 KB | 7.5 MB |
| Preferences | 50 | 1 KB | 0.05 MB |
| **Total per tenant** | | | **~20 MB** |

100 tenants = ~2 GB heap. This is feasible for a dedicated server with 4-8 GB heap.

For larger deployments (1000+ tenants), consider:
- Eviction: disconnect idle operators (drop `LocalCache`, reconnect on next request)
- Shared-DB mode: single operator with `tenant_id` column (not in this PRD)
- External routing: reverse proxy routes tenants to different Rapla instances

## Scope

### What changes

| Area | Files/packages | Nature of change |
|------|---------------|-----------------|
| New: Tenant infrastructure | New package `org.rapla.server.tenant` | `TenantRegistry`, `TenantContext`, `TenantContextHolder`, `TenantFilter`, `TenantAwareFacade`, `TenantStorageSelector`, `OperatorFactory` |
| Config | `RaplaServerProperties`, `application.yml` | Add `tenants` map |
| REST controllers | `org.rapla.server.spring.web`, `org.rapla.server.internal` | Add `@PathVariable tenant` to mappings |
| Auth | `AuthController`, JWT claims | Add `tid` claim, validate tenant match |
| `ServerStorageSelector` | `org.rapla.server.internal` | Replaced by `TenantStorageSelector` |
| `FacadeImpl` | `org.rapla.facade.internal` | No longer `@Singleton`; created per tenant |
| Startup | Spring `@Configuration` classes | Add `TenantRegistryInitializer` |

### What stays

- Entity model (all entity classes unchanged)
- `CachableStorageOperator`, `LocalCache`, `FileOperator`, `DBOperator` (just instantiated more times)
- `ImportExportManagerImpl` (unchanged)
- Plugin system (plugins receive `RaplaFacade` — now `TenantAwareFacade` — transparent to them)
- Angular frontend (only base URL config changes)
- Swing client (only server URL config changes)
- Business logic in all `*ServiceImpl` classes

### Key files/packages affected

**New files:**
- `src/main/java/org/rapla/server/tenant/TenantRegistry.java`
- `src/main/java/org/rapla/server/tenant/TenantContext.java`
- `src/main/java/org/rapla/server/tenant/TenantContextHolder.java`
- `src/main/java/org/rapla/server/tenant/TenantFilter.java`
- `src/main/java/org/rapla/server/tenant/TenantAwareFacade.java`
- `src/main/java/org/rapla/server/tenant/TenantStorageSelector.java`
- `src/main/java/org/rapla/server/tenant/OperatorFactory.java`
- `src/main/java/org/rapla/server/tenant/TenantRegistryInitializer.java`
- `src/main/java/org/rapla/server/tenant/TenantConfig.java`
- `src/test/java/org/rapla/server/tenant/TenantFilterTest.java`
- `src/test/java/org/rapla/server/tenant/TenantRegistryTest.java`
- `src/test/java/org/rapla/server/tenant/TenantAwareFacadeTest.java`
- `src/test/java/org/rapla/server/tenant/MultiTenantIntegrationTest.java`

**Modified files:**
- `src/main/java/org/rapla/server/spring/RaplaServerProperties.java` — add `tenants` map
- `src/main/java/org/rapla/server/spring/ServerCoreConfig.java` — replace `FacadeImpl` bean with `TenantAwareFacade`
- `src/main/java/org/rapla/server/spring/ServerServiceConfig.java` — replace `ServerStorageSelector` with `TenantStorageSelector`
- `src/main/java/org/rapla/server/spring/LegacyServerBridgeConfig.java` — tenant-aware config bridge
- `src/main/java/org/rapla/facade/internal/FacadeImpl.java` — remove `@Singleton`, make freely instantiable
- `src/main/resources/application.yml` — add `tenants` section

**REST controllers (mechanical `@PathVariable` addition):**
- `src/main/java/org/rapla/server/spring/web/RemoteLoggerController.java`
- `src/main/java/org/rapla/server/spring/web/ICalTimezonesController.java`
- All future controllers migrated in PRD 001 Phase 3

## Plan

### Phase T1: Tenant Infrastructure (core, no REST changes yet)

1. Create `TenantConfig` POJO (storage type, datasource maps)
2. Add `tenants` field to `RaplaServerProperties`
3. Create `TenantContext` (bundles operator + facade + update manager)
4. Create `TenantRegistry` (ConcurrentHashMap of tenant ID → TenantContext)
5. Create `OperatorFactory` — builds a `CachableStorageOperator` from a `TenantConfig`
6. Create `TenantRegistryInitializer` — reads tenants from config, creates and registers all operators at startup
7. Create `TenantContextHolder` (ThreadLocal)
8. Create `TenantAwareFacade` implementing `RaplaFacade` — delegates to `TenantContextHolder.get().getFacade()`
9. Write tests: `TenantRegistryTest`, `OperatorFactoryTest`
10. Write test: single-tenant backward compatibility (no `rapla.tenants` in config → default tenant created)

### Phase T2: HTTP Tenant Resolution

1. Create `TenantFilter` (OncePerRequestFilter) — `/rapla/...` → default tenant, `/{id}/rapla/...` → named tenant; strips tenant prefix before forwarding
2. Configure filter in Spring Security chain (before auth filters)
3. Update `application.yml` with example multi-tenant config (remove `server.servlet.context-path`)
4. Write test: `TenantFilterTest` with MockMvc — verify default tenant resolved for `/rapla/...`, named tenant for `/{id}/rapla/...`, 404 for unknown tenant, 404 for invalid paths

### Phase T3: REST Controller Migration

1. Add dual-path mappings to all existing controller methods: `{"/rapla/...", "/{tenant}/rapla/..."}`
2. Change `@PostMapping("/auth/login")` to `@PostMapping({"/rapla/auth/login", "/{tenant}/rapla/auth/login"})`
3. Add `@PathVariable(required = false) String tenant` parameter to each method
4. Verify `TenantFilter` strips tenant prefix consistently so controllers see `/rapla/...` paths
5. Write test: `MultiTenantIntegrationTest` — full request cycle for two named tenants + default tenant

### Phase T4: Authentication

1. Add `tid` (tenant ID) claim to JWT issued by `AuthController`
2. Create `TenantJwtAuthenticationConverter` — validates `tid` matches URL tenant
3. Update `AuthController` to use tenant's user store for credential validation
4. Update `RemoteSessionImpl` (if still in use) to store and validate tenant
5. Write test: login as tenant1 user, access tenant1 data succeeds, access tenant2 data returns 403

### Phase T5: Backward Compatibility & Cleanup

1. Implement single-tenant fallback (no `rapla.tenants` → `default` tenant from legacy config)
2. Support both `/rapla/resources` and `/rapla/default/resources` in single-tenant mode
3. Remove old `ServerStorageSelector` and `ServerContainerContext` (now fully replaced)
4. Update `application.yml` documentation
5. Run full test suite — all existing tests pass
6. Write migration guide for existing deployments

## Tests

| Phase | Test | When |
|-------|------|------|
| T1 | `TenantRegistry` register/get/remove; `OperatorFactory` creates operator for file and DB configs; `TenantAwareFacade` delegates correctly | After T1 |
| T1 | Single-tenant fallback: empty `rapla.tenants` → default tenant created, all beans resolve | After T1 |
| T2 | `TenantFilter` resolves default tenant for `/rapla/...`, named tenant for `/{id}/rapla/...`, returns 404 for unknown, clears ThreadLocal after request | After T2 |
| T2 | Two named tenants + default tenant with separate file stores; requests to each return correct data | After T2 |
| T3 | All REST endpoints respond to both `/rapla/...` (default) and `/{tenant}/rapla/...` (named) URLs | After T3 |
| T3 | Angular frontend base URL change documented and verified | After T3 |
| T4 | JWT contains `tid` claim; mismatched tenant returns 403 | After T4 |
| T4 | Login with `/acme/rapla/auth/login` with tenant1 credentials → token valid for acme only | After T4 |
| T5 | Existing single-tenant deployment config works unchanged (`/rapla/...` URLs with no tenant prefix) | After T5 |
| T5 | Full `mvn test` passes | After T5 |

## Risks

1. **Memory pressure** — Each tenant loads all entities into heap. 100 tenants * 20 MB = 2 GB. Operator eviction (disconnect idle tenants) should be designed from the start, even if not implemented in T1.
2. **ThreadLocal leaks** — `TenantContextHolder` must be cleared in `finally` block of `TenantFilter`. Missing cleanup causes cross-tenant data leaks. Mitigate with defensive checks in `TenantAwareFacade`.
3. **Static state** — Any static fields in entity classes or utilities that hold per-tenant state will break. Audit for static mutable state before T1.
4. **Plugin compatibility** — Plugins that cache `RaplaFacade` references at startup will get the wrong tenant. All plugins must resolve facade per-request via `TenantAwareFacade`. Mitigate by making `RaplaFacade` bean always be `TenantAwareFacade`.
5. **Connection pool exhaustion** — Each DB tenant opens its own connection pool. With 50 DB tenants, default HikariCP settings (10 connections each) = 500 connections. Tune per-tenant pool sizes based on expected load.

## Dependencies on Other PRDs

| PRD | Relationship |
|-----|-------------|
| **001: Spring Boot Migration** | **Prerequisite.** Multi-tenancy builds on Spring Boot DI, `@ConfigurationProperties`, Spring Security, and the `TenantFilter` pattern. Phases T1-T5 assume PRD 001 Phases 0-3 are complete. |
| **001-A: Date → LocalDateTime** | Independent. Can proceed in parallel. |

## Open Questions

1. **Tenant ID format?** What characters are valid in a tenant ID? Used in URL paths, config keys, and JWT claims. **Recommendation:** lowercase alphanumeric + hyphens, regex `[a-z][a-z0-9-]{0,62}`, validated at registration.

2. **Shared entities across tenants?** Can dynamic types or categories be shared between tenants? **Recommendation:** No sharing in v1 — complete isolation. Cross-tenant sharing can be added later via explicit "publish" mechanism if needed.

3. **File-based tenant isolation?** File-based tenants each need their own data directory. Should directories be named by tenant ID under a common root (e.g., `/var/rapla/tenants/acme/`) or configured individually? **Recommendation:** Both — `file-datasources` can be an absolute path or relative to `rapla.tenants-root` (new property, defaults to `/var/rapla/tenants).
