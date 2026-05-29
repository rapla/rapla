# PRD 002: Multi-Tenancy Support

**Status:** draft
**Date:** 2026-05-05

## Goal

Allow a single Rapla server instance to serve multiple independent tenants, each with its own data (reservations, resources, users, dynamic types, preferences). Tenant identity is resolved from the URL path (e.g., `/rapla/tenant1/...`, `/rapla/tenant2/...`), Java EE context-path style. Each tenant gets a dedicated `CachableStorageOperator` with its own `LocalCache` and storage backend (file or database).

Enables hosting multiple organisations on one server without sharing data, without separate instances, and without adding `tenant_id` columns.

## Current Architecture (Single-Tenant)

```
HTTP Request
  → Servlet/Controller
    → FacadeImpl (singleton)
      → CachableStorageOperator (singleton)
        → LocalCache (one in-memory store)
          → FileOperator or DBOperator (one datasource)
```

Key singletons binding the server to one tenant:

| Component | Scope | File |
|-----------|-------|------|
| `FacadeImpl` | `@Singleton` | `facade/internal/FacadeImpl.java` |
| `CachableStorageOperator` | `@Singleton` via `ServerStorageSelector` | `server/internal/ServerStorageSelector.java` |
| `LocalCache` | Created per operator | `storage/LocalCache.java` |
| `ServerStorageSelector` | `@Singleton` Provider | `server/internal/ServerStorageSelector.java` |
| `ServerContainerContext` | Holds named datasource maps, `getMainDbDatasource()` returns hardcoded `"jdbc/rapladb"` | `server/internal/ServerContainerContext.java` |
| `UpdateDataManagerImpl` | Broadcasts changes to connected clients (one operator) | `server/internal/UpdateDataManagerImpl.java` |
| `RemoteSessionImpl` | Per-user session, no tenant awareness | `server/internal/RemoteSessionImpl.java` |

`ServerContainerContext.dbDatasources`/`fileDatasources` already hold multiple named datasources, but only the "main" one is wired to a live operator. `ImportExportManagerImpl` is the sole consumer of non-main datasources — creates temp operator, imports, discards.

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
```

### Tenant Resolution: Path-Based

First path segment before `/rapla/`. No fixed `context-path` — `TenantFilter` routes.

| URL | Tenant ID | Remaining path |
|-----|-----------|----------------|
| `/rapla/auth/login` | *(none — default)* | `/rapla/auth/login` |
| `/acme/rapla/api/resources` | `acme` | `/rapla/api/resources` |
| `/globex/rapla/auth/login` | `globex` | `/rapla/auth/login` |
| `/admin/rapla/tenants` | `admin` (system) | `/rapla/tenants` |

**Backward compatibility:** no tenant prefix (path starts `/rapla/`) → default tenant. Existing deployments with `context-path: /rapla` unchanged.

Spring Boot controller mappings use dual `/{tenant}/rapla/...` + no-tenant variant:

```java
@PostMapping({"/rapla/auth/login", "/{tenant}/rapla/auth/login"})
public LoginResponse login(@PathVariable(required = false) String tenant,
                           @RequestBody LoginRequest req) { ... }
```

`TenantFilter` resolves before controller:
1. Path starts `/rapla/` → default tenant
2. Path matches `/{id}/rapla/...` → lookup by `id`
3. Strip prefix, forward to controller's `/rapla/...` mapping

Mirrors old Jetty multi-context pattern.

### Core Components

#### 1. `TenantRegistry` — operator pool

One `TenantContext` per tenant. `TenantContext` bundles operator, facade, update manager, tenant-specific preferences.

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

#### 2. `TenantFilter` — request-scoped resolution

Spring `OncePerRequestFilter` that:
1. Path starts `/rapla/` → default tenant
2. Else extract from first segment → lookup in `TenantRegistry`
3. Set `TenantContextHolder` (ThreadLocal); forward with prefix stripped

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

#### 4. `TenantAwareFacade` — delegating `RaplaFacade`

Replaces singleton `FacadeImpl` bean. Delegates every call to current tenant's `FacadeImpl`:

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

Existing code injecting `RaplaFacade` continues unchanged — just talks to current request's tenant.

#### 5. `TenantStorageSelector` — replaces `ServerStorageSelector`

Creates operators on demand instead of one at startup:

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

Each tenant has its own user store. Flow:
1. Client `POST /acme/rapla/auth/login` (or `POST /rapla/auth/login` for default)
2. `AuthController` extracts tenant from `@PathVariable`
3. Validates against tenant's user store
4. JWT claims: `sub`, `tid` (tenant ID), `roles`
5. Subsequent requests: `TenantFilter` validates JWT `tid` matches URL tenant

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

1. Spring Boot binds `rapla.tenants` to `RaplaServerProperties`
2. `TenantRegistryInitializer` (`@PostConstruct` / `ApplicationRunner`): iterate tenants, create operator via `OperatorFactory`, `connect()`, wrap in `TenantContext`, register
3. `TenantFilter` ready
4. No tenants configured → single-tenant fallback (backward compat)

### Lazy vs Eager Loading

| Strategy | Pros | Cons |
|----------|------|------|
| **Eager** (all at startup) | Immediate error on misconfig; no cold-start latency | Higher memory; slower startup |
| **Lazy** (first request) | Fast startup; lower memory | First request slow; datasource errors at runtime |

**Recommendation:** Eager with configurable timeout. Failed connect → log + mark unavailable (503). Safer for production.

### Impact on Existing Code

| Component | Change | Complexity |
|-----------|--------|------------|
| `RaplaFacade` usages | Replace singleton `FacadeImpl` with `TenantAwareFacade` — no API change | Low |
| `ServerStorageSelector` | → `TenantStorageSelector` | Medium |
| `ServerContainerContext` | → per-tenant config | Medium |
| All REST controllers | Add dual mappings `{"/rapla/...", "/{tenant}/rapla/..."}` + `@PathVariable(required=false)` | Medium (mechanical) |
| `RemoteSessionImpl` | Store + validate tenant | Low |
| `UpdateDataManagerImpl` | One per tenant (already per-operator) | Low |
| `ImportExportManagerImpl` | Unchanged | None |
| `FacadeImpl` | Non-singleton; one per tenant | Low |
| `CachableStorageOperator`, `LocalCache`, `FileOperator`, `DBOperator` | Just instantiated more times | None |
| `application.yml` | Add `rapla.tenants` | Low |
| Angular frontend | Base URL `/rapla/` → `/{tenant}/rapla/` for multi-tenant | Medium (config) |
| Swing client | Server URL change for multi-tenant | Low |

### Backward Compatibility

Empty/absent `rapla.tenants` → **single-tenant mode**:
- Default tenant `"default"` created from existing `rapla.file-datasources`/`rapla.db-datasources`
- All URLs work without prefix — zero breaking change
- Named-tenant URLs (`/default/rapla/...`) also work
- `server.servlet.context-path` can stay unset

### Memory Considerations

Each tenant loads all entities. Per-tenant estimate:

| Entity type | Typical count | Memory per entity (est.) | Total |
|-------------|---------------|--------------------------|-------|
| Reservations | 5,000 | 2 KB | 10 MB |
| Allocatables | 500 | 1 KB | 0.5 MB |
| Users | 50 | 0.5 KB | 0.025 MB |
| Dynamic types | 20 | 5 KB | 0.1 MB |
| Appointments | 15,000 | 0.5 KB | 7.5 MB |
| Preferences | 50 | 1 KB | 0.05 MB |
| **Total per tenant** | | | **~20 MB** |

100 tenants ≈ 2 GB heap (feasible on 4-8 GB). For 1000+: operator eviction, shared-DB mode (`tenant_id` column, separate PRD), or reverse-proxy routing to multiple Rapla instances.

## Scope

### What changes

| Area | Files/packages | Nature |
|------|---------------|--------|
| New: Tenant infrastructure | New package `org.rapla.server.tenant` | `TenantRegistry`, `TenantContext`, `TenantContextHolder`, `TenantFilter`, `TenantAwareFacade`, `TenantStorageSelector`, `OperatorFactory` |
| Config | `RaplaServerProperties`, `application.yml` | Add `tenants` map |
| REST controllers | `org.rapla.server.spring.web`, `org.rapla.server.internal` | Add `@PathVariable tenant` |
| Auth | `AuthController`, JWT claims | Add `tid`, validate match |
| `ServerStorageSelector` | `org.rapla.server.internal` | → `TenantStorageSelector` |
| `FacadeImpl` | `org.rapla.facade.internal` | No longer `@Singleton` |
| Startup | Spring `@Configuration` | Add `TenantRegistryInitializer` |

### What stays

Entity model, `CachableStorageOperator`/`LocalCache`/`FileOperator`/`DBOperator`, `ImportExportManagerImpl`, plugin system (plugins get `TenantAwareFacade` transparently), Angular/Swing (only base URL config), business logic in `*ServiceImpl`.

### Key files/packages affected

**New:**
- `src/main/java/org/rapla/server/tenant/{TenantRegistry,TenantContext,TenantContextHolder,TenantFilter,TenantAwareFacade,TenantStorageSelector,OperatorFactory,TenantRegistryInitializer,TenantConfig}.java`
- `src/test/java/org/rapla/server/tenant/{TenantFilterTest,TenantRegistryTest,TenantAwareFacadeTest,MultiTenantIntegrationTest}.java`

**Modified:**
- `src/main/java/org/rapla/server/spring/RaplaServerProperties.java` — add `tenants`
- `ServerCoreConfig.java` — replace `FacadeImpl` bean with `TenantAwareFacade`
- `ServerServiceConfig.java` — replace `ServerStorageSelector` with `TenantStorageSelector`
- `LegacyServerBridgeConfig.java` — tenant-aware bridge
- `FacadeImpl.java` — remove `@Singleton`
- `application.yml` — add `tenants`

**REST controllers (mechanical `@PathVariable`):**
- `RemoteLoggerController`, `ICalTimezonesController`, all future PRD 001 Phase 3 controllers

## Plan

### Phase T1: Tenant Infrastructure (core, no REST yet)

1. Create `TenantConfig` POJO
2. Add `tenants` field to `RaplaServerProperties`
3. Create `TenantContext` (operator + facade + update manager)
4. Create `TenantRegistry` (ConcurrentHashMap)
5. Create `OperatorFactory` (build `CachableStorageOperator` from config)
6. Create `TenantRegistryInitializer` (eager init at startup)
7. Create `TenantContextHolder`
8. Create `TenantAwareFacade`
9. Tests: `TenantRegistryTest`, `OperatorFactoryTest`
10. Test: single-tenant backward compat

### Phase T2: HTTP Tenant Resolution

1. Create `TenantFilter` — `/rapla/...` → default, `/{id}/rapla/...` → named, strips prefix
2. Configure in Spring Security chain (before auth)
3. Update `application.yml` example (remove `server.servlet.context-path`)
4. `TenantFilterTest` (MockMvc): default + named + 404 + invalid paths

### Phase T3: REST Controller Migration

1. Add dual-path mappings to all methods
2. Change `@PostMapping("/auth/login")` → `@PostMapping({"/rapla/auth/login", "/{tenant}/rapla/auth/login"})`
3. Add `@PathVariable(required = false) String tenant` to each method
4. Verify `TenantFilter` strips consistently
5. `MultiTenantIntegrationTest` — full cycle for two named + default

### Phase T4: Authentication

1. Add `tid` claim to JWT
2. `TenantJwtAuthenticationConverter` — validate `tid` matches URL
3. Update `AuthController` to use tenant's user store
4. Update `RemoteSessionImpl` (if still in use)
5. Test: login as tenant1, access tenant1 succeeds, tenant2 → 403

### Phase T5: Backward Compatibility & Cleanup

1. Single-tenant fallback (no `rapla.tenants` → `default`)
2. Support both `/rapla/resources` + `/rapla/default/resources` in single-tenant
3. Remove old `ServerStorageSelector`/`ServerContainerContext`
4. Update `application.yml` docs
5. Full test suite green
6. Write migration guide

## Tests

| Phase | Test | When |
|-------|------|------|
| T1 | `TenantRegistry` register/get/remove; `OperatorFactory` for file+DB; `TenantAwareFacade` delegates | After T1 |
| T1 | Single-tenant fallback: empty `rapla.tenants` → default created, all beans resolve | After T1 |
| T2 | `TenantFilter` resolves default for `/rapla/...`, named for `/{id}/rapla/...`, 404 for unknown, clears ThreadLocal | After T2 |
| T2 | Two named + default with separate stores; requests return correct data | After T2 |
| T3 | All REST endpoints respond to both `/rapla/...` + `/{tenant}/rapla/...` | After T3 |
| T3 | Angular frontend base URL change documented + verified | After T3 |
| T4 | JWT contains `tid`; mismatch → 403 | After T4 |
| T4 | Login `/acme/rapla/auth/login` with tenant1 creds → token valid for acme only | After T4 |
| T5 | Existing single-tenant config works unchanged | After T5 |
| T5 | Full `mvn test` passes | After T5 |

## Risks

1. **Memory pressure** — 100 tenants × 20 MB = 2 GB. Operator eviction (disconnect idle, drop `LocalCache`, reconnect on demand) should be designed from start.
2. **ThreadLocal leaks** — `TenantContextHolder` must clear in `finally` block. Missing cleanup = cross-tenant leaks. Defensive checks in `TenantAwareFacade`.
3. **Static state** — any static fields holding per-tenant state break. Audit before T1.
4. **Plugin compatibility** — plugins caching `RaplaFacade` references at startup get the wrong tenant. Make `RaplaFacade` bean always `TenantAwareFacade`.
5. **Connection pool exhaustion** — 50 DB tenants × 10 default HikariCP = 500 conns. Tune per-tenant pool sizes.

## Dependencies on Other PRDs

| PRD | Relationship |
|-----|-------------|
| **001: Spring Boot Migration** | **Prerequisite.** Assumes Phases 0-3 complete. |
| **001-A: Date → LocalDateTime** | Independent; parallel ok. |

## Open Questions

1. **Tenant ID format?** Recommendation: lowercase alphanumeric + hyphens, regex `[a-z][a-z0-9-]{0,62}`, validated at registration.
2. **Shared entities across tenants?** Recommendation: no sharing in v1; explicit "publish" mechanism later if needed.
3. **File-based tenant isolation?** Recommendation: both — absolute path or relative to `rapla.tenants-root` (defaults `/var/rapla/tenants`).
