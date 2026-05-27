# `rapla.jar` (Spring Boot fat JAR) — asset inventory

Snapshot of every file inside `rapla.jar` (52.1 MB on disk, 53.3 MB
uncompressed), sorted into buckets by purpose, with notes on what each piece
is for and where it gets served at runtime. Built from the rapla-app Maven
module via `mvn -pl rapla-app -am clean package -DskipTests -Psign-pkcs11`.

## Top-level buckets

| Bucket | Uncompressed | % of total | Files | Role |
|---|---:|---:|---:|---|
| `BOOT-INF/lib/*.jar` | 44.86 MB | 84.2 % | 75 | Server runtime classpath (PropertiesLauncher) |
| `BOOT-INF/classes/static/webclient/` | 4.76 MB | 8.9 % | 5 | JNLP Swing-client jars (signed) — served at `/webclient/*` |
| `BOOT-INF/classes/static/app/` | 2.68 MB | 5.0 % | 25 | Angular SPA bundle — served at `/app/*` |
| `org/springframework/boot/loader/` | 399 KB | 0.75 % | 112 | Spring Boot's PropertiesLauncher / JarLauncher |
| `BOOT-INF/classes/` (rapla-app classes + resources) | 237 KB | 0.45 % | 47 | rapla-app's own Spring config + entry point |
| `BOOT-INF/classes/static/` (root) | 167 KB | 0.31 % | 6 | CSS for server-rendered pages (`/server`, `/login`, `/rapla/*`) |
| `BOOT-INF/classes/openapi/` | 97 KB | 0.18 % | 3 | Captured OpenAPI specs (PRD 041) |
| `META-INF/` | 48 KB | 0.09 % | 4 | Manifest, AutoConfiguration.imports, pom.xml |
| `BOOT-INF/classes/static/swagger-ui/` | 6 KB | 0.01 % | 1 | Swagger UI bootstrap (UI loaded from CDN) |
| `BOOT-INF/{classpath,layers}.idx` | 7 KB | 0.01 % | 2 | Spring Boot layered-jar index |

Total uncompressed: 53,262,560 bytes.
Compressed (on disk): 52,091,698 bytes (the ZIP DEFLATE buys only ~2 %
since the contents are dominated by already-compressed JARs and `.woff2`).

---

## `BOOT-INF/lib/*.jar` — all 75 runtime deps

Loaded by Spring Boot's PropertiesLauncher when the fat JAR boots
(`java -jar rapla.jar`). Grouped by stack.

### Spring Framework (12 jars, 11.6 MB)

| Jar | Size | Role |
|---|---:|---|
| `spring-web-7.0.7.jar` | 2.37 MB | Spring MVC core + HTTP message converters |
| `spring-core-7.0.7.jar` | 2.17 MB | Spring DI, utilities, `Resource` abstraction |
| `spring-context-7.0.7.jar` | 1.50 MB | Application context, event bus, scheduling |
| `spring-webmvc-7.0.7.jar` | 1.11 MB | DispatcherServlet, `@RequestMapping`, view resolvers |
| `spring-beans-7.0.7.jar` | 984 KB | Bean factory, BeanDefinition machinery |
| `spring-jdbc-7.0.7.jar` | 481 KB | JdbcTemplate (used for HSQLDB / MariaDB) |
| `spring-aop-7.0.7.jar` | 462 KB | AOP proxies (`@Transactional`, etc.) |
| `spring-expression-7.0.7.jar` | 342 KB | SpEL — used by Spring Security DSL |
| `spring-tx-7.0.7.jar` | 292 KB | Transaction manager abstraction |
| `spring-graphql-2.0.3.jar` | 715 KB | GraphQL → DispatcherServlet adapter (`/api/graphql`) |

### Spring Boot (16 jars, 4.46 MB)

| Jar | Size | Role |
|---|---:|---|
| `spring-boot-4.0.6.jar` | 1.37 MB | Core: SpringApplication, ConfigData, Environment |
| `spring-boot-autoconfigure-4.0.6.jar` | 371 KB | All `@AutoConfiguration` classes (HSQLDB, web, security, etc.) |
| `spring-boot-jdbc-4.0.6.jar` | 201 KB | DataSource auto-config |
| `spring-boot-webmvc-4.0.6.jar` | 174 KB | MVC auto-config |
| `spring-boot-web-server-4.0.6.jar` | 169 KB | Embedded web-server abstraction |
| `spring-boot-tomcat-4.0.6.jar` | 124 KB | Tomcat auto-config |
| `spring-boot-security-4.0.6.jar` | 98 KB | Security auto-config |
| `spring-boot-graphql-4.0.6.jar` | 72 KB | GraphQL auto-config |
| `spring-boot-jackson-4.0.6.jar` | 68 KB | Jackson auto-config + module discovery |
| `spring-boot-security-oauth2-resource-server-4.0.6.jar` | 61 KB | OAuth2 RS auto-config (JWT decoding) |
| `spring-boot-http-converter-4.0.6.jar` | 54 KB | HTTP message converter auto-config |
| `spring-boot-servlet-4.0.6.jar` | 47 KB | Servlet auto-config |
| `spring-boot-sql-4.0.6.jar` | 36 KB | SQL init scripts |
| `spring-boot-security-oauth2-authorization-server-4.0.6.jar` | 33 KB | Auth server auto-config |
| `spring-boot-transaction-4.0.6.jar` | 21 KB | Transaction auto-config |
| `spring-boot-persistence-4.0.6.jar` | 16 KB | Persistence-units auto-config |
| `spring-boot-reactor-4.0.6.jar` | 12 KB | Reactor auto-config |

### Spring Security (9 jars, 4.6 MB)

| Jar | Size | Role |
|---|---:|---|
| `spring-security-config-7.0.5.jar` | 2.10 MB | Form-login DSL, OAuth2 DSL, filter-chain config |
| `spring-security-web-7.0.5.jar` | 933 KB | Filter chain, `SecurityContextHolder`, CSRF |
| `spring-security-oauth2-authorization-server-7.0.5.jar` | 608 KB | The embedded SAS (issues JWTs at `/oauth2/token`) |
| `spring-security-core-7.0.5.jar` | 618 KB | Authentication, Authorization, UserDetails |
| `spring-security-oauth2-jose-7.0.5.jar` | 140 KB | JOSE (JWT, JWS, JWE) decoder/encoder |
| `spring-security-oauth2-resource-server-7.0.5.jar` | 134 KB | OAuth2 RS support (`@PreAuthorize`, JWT auth) |
| `spring-security-oauth2-core-7.0.5.jar` | 115 KB | OAuth2 abstractions (provider details, tokens) |
| `spring-security-crypto-7.0.5.jar` | 112 KB | Password encoders (BCrypt, etc.) |
| `nimbus-jose-jwt-10.4.jar` | 810 KB | JWT signing/verification primitives |

### Tomcat (3 jars, 4.15 MB)

| Jar | Size | Role |
|---|---:|---|
| `tomcat-embed-core-11.0.21.jar` | 3.59 MB | Servlet container + connectors |
| `tomcat-embed-websocket-11.0.21.jar` | 286 KB | Websocket support |
| `tomcat-embed-el-11.0.21.jar` | 270 KB | JSP EL expression engine |

### GraphQL (4 jars, 4.74 MB) — PRD 035

| Jar | Size | Role |
|---|---:|---|
| `graphql-java-25.0.jar` | 3.81 MB | GraphQL execution engine (parser, validator, runtime) |
| `graphql-java-extended-scalars-24.0.jar` | 101 KB | Date/DateTime/Long scalar types |
| `java-dataloader-6.0.0.jar` | 107 KB | Batching/caching (N+1 mitigation) |
| `reactor-core-3.8.5.jar` | 1.94 MB | Reactive runtime (Spring GraphQL needs it) |

### rapla modules (2 jars, 2.39 MB)

| Jar | Size | Role |
|---|---:|---|
| `rapla-core-2.1-SNAPSHOT.jar` | 1.69 MB | Shared types: entities, facade, REST DTOs, storage interfaces, i18n bundles |
| `rapla-server-2.1-SNAPSHOT.jar` | 701 KB | Server-side: JDBC storage, REST controllers, Spring auto-config |

(`rapla-client` is NOT in `BOOT-INF/lib/` — it's Swing-only and lives in `BOOT-INF/classes/static/webclient/` instead.)

### Storage / Data (5 jars, 4.39 MB)

| Jar | Size | Role |
|---|---:|---|
| `hsqldb-2.7.1-jdk8.jar` | 1.66 MB | HSQLDB driver — used for file-backed dev/test stores |
| `ical4j-4.2.0.jar` | 1.62 MB | iCalendar parser/writer — `/api/ical/*`, `/rapla/ical`, `/rapla/calendar` |
| `joda-time-2.8.jar` | 622 KB | Legacy date types (used by ews-java-api) |
| `threeten-extra-1.8.0.jar` | 281 KB | Extended JDK time types (Interval, etc.) |
| `HikariCP-7.0.2.jar` | 172 KB | JDBC connection pool |

### Exchange + Mail (4 jars, 2.06 MB)

| Jar | Size | Role |
|---|---:|---|
| `ews-java-api-2.0.jar` | 1.20 MB | Microsoft Exchange Web Services client — only used if `rapla.exchange.enabled=true`. Off on rapla-test today. |
| `angus-mail-2.0.5.jar` | 501 KB | Eclipse Angus mail (JavaMail successor) |
| `jakarta.mail-api-2.1.5.jar` | 237 KB | jakarta.mail API |
| `angus-activation-2.0.3.jar` | 27 KB | jakarta.activation provider |

### JSON / Jackson 3 (3 jars, 2.73 MB)

| Jar | Size | Role |
|---|---:|---|
| `jackson-databind-3.1.2.jar` | 2.02 MB | Jackson 3 databind (runtime mapper for rapla wire-format) |
| `jackson-core-3.1.2.jar` | 623 KB | Jackson 3 streaming API |
| `jackson-annotations-2.21.jar` | 91 KB | Annotation package (still under `com.fasterxml.jackson.annotation`, version-shared with Jackson 2) |

### HTTP client (3 jars, 1.19 MB)

| Jar | Size | Role |
|---|---:|---|
| `httpclient-4.5.14.jar` | 786 KB | Apache HttpClient (used by ews-java-api + some plugins) |
| `commons-logging-1.3.6.jar` | 74 KB | Bridge for Apache HttpClient logging |
| `httpcore-4.4.16.jar` | 328 KB | Apache HttpCore |

### Logging (6 jars, 1.49 MB)

| Jar | Size | Role |
|---|---:|---|
| `logback-core-1.5.32.jar` | 691 KB | Logback core |
| `log4j-api-2.25.4.jar` | 351 KB | log4j 2 API surface (used by some deps) |
| `logback-classic-1.5.32.jar` | 306 KB | Logback SLF4J binding |
| `log4j-to-slf4j-2.25.4.jar` | 24 KB | Routes log4j 2 → SLF4J |
| `jcl-over-slf4j-2.0.17.jar` | 22 KB | Routes commons-logging → SLF4J |
| `jul-to-slf4j-2.0.17.jar` | 9 KB | Routes JUL → SLF4J |
| `slf4j-api-2.0.17.jar` | 77 KB | SLF4J API |

### Utilities (8 jars, 1.6 MB)

| Jar | Size | Role |
|---|---:|---|
| `commons-lang3-3.19.0.jar` | 709 KB | Apache Commons Lang utilities |
| `commons-codec-1.14.jar` | 348 KB | Base64, hex, etc. |
| `snakeyaml-2.5.jar` | 340 KB | YAML parser — Spring Boot reads `application.yml` with it |
| `micrometer-observation-1.16.5.jar` | 93 KB | Observation API (auto-wired by Spring Boot, mostly unused on rapla) |
| `micrometer-commons-1.16.5.jar` | 58 KB | Micrometer shared helpers |
| `context-propagation-1.2.1.jar` | 36 KB | Cross-thread context propagation (Reactor) |
| `reactive-streams-1.0.4.jar` | 15 KB | Reactive Streams spec |
| `jakarta.annotation-api-3.0.0.jar` | 31 KB | `@PostConstruct`, `@Resource`, etc. |
| `jakarta.activation-api-2.1.4.jar` | 67 KB | DataHandler / MimeType (mail dep) |
| `jspecify-1.0.0.jar` | 7 KB | `@Nullable`, `@NonNull` annotations |

---

## `BOOT-INF/classes/static/webclient/` — JNLP Swing-client extras (5 files)

Served by `WebClientJarController` at `/webclient/*`. Java Web Start downloads
+ verifies the YubiKey signatures. Only Swing-only artifacts duplicate here;
the other 22 JNLP-set jars come straight out of `BOOT-INF/lib/` (single-copy
strategy — saves ~14 MB).

| File | Size | Role |
|---|---:|---|
| `rxjava-3.1.5.jar` | 2.82 MB | RxJava — used by the Swing client's async layer |
| `rapla-client-2.1-SNAPSHOT.jar` | 1.91 MB | Swing UI code (panels, dialogs, models) |
| `rapla_128x128.png` | 21 KB | JNLP launcher icon |
| `rapla_64x64.png` | 7.3 KB | JNLP launcher icon |
| `rapla_32x32.png` | 2.5 KB | JNLP launcher icon |

---

## `BOOT-INF/classes/static/app/` — Angular SPA bundle (25 files, 2.68 MB)

Built by `npm run build:fast --configuration=production` and copied into the
fat JAR. Served by Spring Boot's static-resource handler at `/app/*`.

### Angular code & assets (15 files, 1.21 MB)

| File | Size | Role |
|---|---:|---|
| `chunk-44OPJ2Z4.js` | 196 KB | Largest Angular code-split chunk (likely Angular runtime + framework) |
| `chunk-GGZBV6KE.js` | 163 KB | Code chunk (likely Material UI / forms) |
| `chunk-PLH5FXLN.js` | 157 KB | Code chunk |
| `chunk-IQM22OBU.js` | 86 KB | Code chunk |
| `chunk-HSBE5IAE.js` | 77 KB | Code chunk |
| `chunk-7HJOXY2T.js` | 66 KB | Code chunk |
| `favicon.ico` | 15 KB | SPA favicon |
| `chunk-WMBUWWTQ.js` | 11 KB | Small chunk |
| `styles-43ROWIGP.css` | 9.7 KB | Compiled global styles (theme, etc.) |
| `chunk-STS2OMCN.js` | 9.6 KB | Small chunk |
| `index.html` | 7.4 KB | SPA shell — loaded at `/app/` |
| `main-336P3QPJ.js` | 7.4 KB | Angular bootstrap entry |
| `chunk-V6N7CNLT.js` | 991 B | Tiny chunk |
| `chunk-Z5YRS4VZ.js` | 125 B | Tiny chunk |
| 10 chunks total | | Code-split, lazy-loaded on first route visit |

### Material Icons fonts (10 files, 1.47 MB)

Five icon-style variants × two formats (`.woff` + `.woff2`). Modern browsers
prefer `.woff2`; the `.woff` is the fallback for old browsers.

| File | Size | Variant |
|---|---:|---|
| `material-icons-two-tone-LCGWGE2N.woff` | 332 KB | Two-tone (woff) |
| `material-icons-two-tone-M5N5K6F5.woff2` | 211 KB | Two-tone (woff2) |
| `material-icons-round-SLOHZIXU.woff` | 201 KB | Round (woff) |
| `material-icons-outlined-PCUTWIDZ.woff` | 178 KB | Outlined (woff) |
| `material-icons-round-WEHMTW23.woff2` | 170 KB | Round (woff2) |
| `material-icons-JLIDJUWE.woff` | 161 KB | Filled / regular (woff) |
| `material-icons-sharp-U4OLFP3G.woff` | 153 KB | Sharp (woff) |
| `material-icons-outlined-7BWLPMFK.woff2` | 152 KB | Outlined (woff2) |
| `material-icons-sharp-HCCYMPXE.woff2` | 133 KB | Sharp (woff2) |
| `material-icons-LEZCGFVT.woff2` | 125 KB | Filled / regular (woff2) |

**Trim opportunity:** if the SPA only uses one icon variant (most often
"outlined" or "filled"), the other 4 sets can be removed via Angular's
`@angular/material` font-config. Each variant is ~330 KB total — dropping 4
variants saves ~1.3 MB of the 2.68 MB SPA bundle.

---

## `BOOT-INF/classes/` (rapla-app's own code + resources)

### Java classes (~165 KB, 35 files)

The fat JAR's "own" classes — i.e. what's compiled from `rapla-app/src/main/java/`.
Listed by size; classes under the same package are the Spring config beans
for the application entry point.

| Class | Size | Role |
|---|---:|---|
| `org/rapla/server/spring/AuthorizationServerConfig.class` | 41 KB | Embedded Spring Authorization Server: form login, OAuth2 endpoints, JWT signing, password grant, refresh-token rotation, revocation |
| `org/rapla/server/spring/graphql/ClassificationGraphQLController.class` | 16 KB | GraphQL controller for the classification-system queries (PRD 035) |
| `org/rapla/server/spring/graphql/GeneratedClassificationWiring.class` | 13 KB | Generated SDL-to-resolver wiring |
| `org/rapla/server/spring/graphql/HotSwappableGraphQlSource.class` | 12 KB | Hot-reloads GraphQL schema at runtime (dev only) |
| `org/rapla/server/spring/graphql/HelloGraphQLController.class` | 10 KB | Stub controller (smoke-test queries) |
| `org/rapla/server/spring/graphql/ClassificationSdlGenerator.class` | 10 KB | Generates `.graphqls` SDL from rapla's `DynamicType`s at runtime |
| `org/rapla/server/spring/web/StaticOpenApiController.class` | 7.8 KB | Serves the captured `openapi/*.json` specs at `/api/v3/api-docs/*` when SpringDoc absent (PRD 041) |
| `org/rapla/server/spring/AuthorizationServerConfig$PasswordGrantAuthenticationProvider.class` | 6.3 KB | OAuth2 password-grant impl (`grant_type=password`) |
| `org/rapla/server/spring/graphql/ClassificationGraphQLController$AttributeValueDto.class` | 6.3 KB | GraphQL DTO |
| `org/rapla/server/spring/AuthorizationServerConfig$RaplaRefreshTokenAuthenticationProvider.class` | 5.5 KB | Refresh-token flow handler |
| `org/rapla/server/spring/graphql/ClassificationGraphQLController$AttributeDescriptorDto.class` | 5.3 KB | GraphQL DTO |
| `org/rapla/server/spring/AuthorizationServerConfig$1.class` | 5.2 KB | Anonymous inner class from AuthorizationServerConfig |
| `org/rapla/server/spring/AuthorizationServerConfig$JwtRefreshTokenGenerator.class` | 4.9 KB | JWT-based refresh-token issuance (signed, persisted) |
| `org/rapla/server/spring/graphql/GraphQlScalarConfig$1.class` | 4.5 KB | GraphQL scalar config |
| `org/rapla/server/spring/AuthorizationServerConfig$PublicClientRefreshTokenAuthenticationProvider.class` | 4.0 KB | Refresh for SPA-style public clients |
| `org/rapla/server/spring/AuthorizationServerConfig$RaplaTokenRevocationAuthenticationProvider.class` | 3.4 KB | `/oauth2/revoke` handler |
| `org/rapla/server/spring/graphql/GraphQlSourceConfig.class` | 3.3 KB | GraphQL bean config |
| `org/rapla/server/spring/LoginPageController.class` | 3.1 KB | Server-rendered `/login` HTML page |
| `org/rapla/server/spring/graphql/HelloGraphQLController$UserDto.class` | 2.8 KB | GraphQL DTO |
| `org/rapla/server/spring/SpaResourceConfig.class` | 2.6 KB | Static-resource handler for `/app/*` (serves the SPA bundle, falls back to `index.html` for client-side routes) |
| `org/rapla/server/spring/graphql/GraphQlScalarConfig.class` | 2.4 KB | Registers extended GraphQL scalars |
| `org/rapla/server/spring/graphql/HelloGraphQLController$PeriodDto.class` | 2.4 KB | GraphQL DTO |
| `org/rapla/server/spring/graphql/ClassificationGraphQLController$AllocatableFilter.class` | 2.3 KB | GraphQL input type |
| `org/rapla/server/spring/graphql/HelloGraphQLController$UserFilter.class` | 2.2 KB | GraphQL input type |
| `org/rapla/server/spring/RaplaSpringBootApplication.class` | 2.2 KB | `@SpringBootApplication` entry — the `main()` |
| `org/rapla/server/spring/AuthorizationServerConfig$PasswordGrantAuthenticationConverter.class` | 2.1 KB | Auth converter |
| `org/rapla/server/spring/AuthorizationServerConfig$PublicClientRefreshTokenAuthenticationConverter.class` | 1.8 KB | Auth converter |
| `org/rapla/server/spring/oauth/RaplaOauthRedirectProperties.class` | 1.8 KB | `@ConfigurationProperties` for OAuth redirect URLs |
| `org/rapla/server/spring/graphql/GraphQlSchemaRebuilder.class` | 1.6 KB | Schema rebuild trigger |
| `org/rapla/server/spring/SpaResourceConfig$1.class` | 1.6 KB | Anonymous inner class |
| `org/rapla/server/spring/web/ExplorerRedirects.class` | 1.3 KB | URL-explorer redirect helper |
| `org/rapla/server/spring/AuthorizationServerConfig$PasswordGrantAuthenticationToken.class` | 1.2 KB | Auth token type |
| Various `$1.class` anonymous inner classes | <1 KB | Compiler-generated |

### Non-class resources (~72 KB, 12 files)

| File | Size | Role |
|---|---:|---|
| `application.yml` | 16 KB | Default Spring Boot config (overridden by `/opt/rapla/config/application.yml`) |
| `graphql/schema.graphqls` | 12 KB | GraphQL schema definition (PRD 035) |
| `logback-spring.xml` | 6.5 KB | Logback config (Spring-profile-aware) |
| `application-local.yml` | 5.3 KB | Default dev-profile overrides |
| `application-standalone.yml` | 2.2 KB | Defaults for the standalone (PRD 054) Tauri build |
| `clientlibs.properties` | 558 B | Semicolon-separated JNLP webclient jar names — read by JNLP descriptor template |
| `ical4j.properties` | 129 B | iCal4j defaults (compatibility mode, etc.) |
| `commons-logging.properties` | 70 B | Routes commons-logging to SLF4J |
| `loader.properties` | 27 B | Spring Boot launcher: `loader.path=lib/,plugins/` |

---

## `BOOT-INF/classes/static/` (root, 6 files, 167 KB)

CSS for the server-rendered HTML pages (the non-SPA endpoints).

| File | Size | Used by |
|---|---:|---|
| `bootstrap.min.css` | 126 KB | All server-rendered pages: `/server`, `/`, `/login`, `/rapla/calendar` |
| `calendar.css` | 7.1 KB | `/rapla/calendar` (legacy public calendar HTML) |
| `rapla.css` | 4.6 KB | Shared site CSS |
| `export.css` | 3.5 KB | `/rapla/calendar.csv` and other export pages |
| `default.css` | 1.1 KB | Default theming |
| `login.css` | 376 B | `/login` page |

---

## `BOOT-INF/classes/static/swagger-ui/` (1 file, 5.7 KB)

| File | Size | Role |
|---|---:|---|
| `index.html` | 5.7 KB | Swagger UI bootstrap — loads Swagger UI JS/CSS from CDN (jsdelivr) at request time, points `configUrl` at `/api/v3/api-docs/swagger-config` |

---

## `BOOT-INF/classes/openapi/` (3 files, 97 KB)

Captured at build time by `OpenApiSpecCaptureTest` (PRD 041) and served by
`StaticOpenApiController` when SpringDoc is absent on the runtime classpath
(i.e. always in production).

| File | Size | Served at | Audience |
|---|---:|---|---|
| `client.json` | 78 KB | `/api/v3/api-docs/client` | SPA + Swing-client internal APIs |
| `auth.json` | 12 KB | `/api/v3/api-docs/auth` | OAuth + JWT lifecycle |
| `exports.json` | 5.7 KB | `/api/v3/api-docs/exports` | Imports, exports, legacy `/rapla/*` iCal feeds |

(`dhbw.json` lives in `dhbwrapla.jar` and is wired in by the plugin's
`OpenApiSpecContribution` bean — not in this fat JAR.)

---

## `BOOT-INF/classpath.idx` + `BOOT-INF/layers.idx` (2 files, 7 KB)

| File | Size | Role |
|---|---:|---|
| `BOOT-INF/layers.idx` | 3.8 KB | Layered-jar definition (Docker layer cache optimization) |
| `BOOT-INF/classpath.idx` | 3.5 KB | Ordered classpath manifest used by `PropertiesLauncher` |

---

## `org/springframework/boot/loader/` (112 files, 399 KB)

Spring Boot's launcher classes. Lives at the JAR root (not under `BOOT-INF/`)
because it's the first thing the JVM loads. Includes:

- `PropertiesLauncher.class` (22 KB) — reads `BOOT-INF/classes/loader.properties`
  and adds `loader.path=lib/,plugins/` to the classpath. Lets operators drop
  `dhbwrapla.jar` into `plugins/` without rebuilding the fat JAR.
- `JarLauncher.class` — the alternative launcher (not used here; we use
  Properties).
- `NestedJarFile.class` (16 KB) — handles the nested-jar protocol (`jar:nested:...`).
- Various ZIP/URL/JarFile helpers — implementation details.

The other 109 classes are internal helpers (zip parsing, URL stream handlers,
etc.) under `org/springframework/boot/loader/{jar,launch,net,zip,...}/`.

---

## `META-INF/` (4 files, 48 KB)

| File | Size | Role |
|---|---:|---|
| `META-INF/maven/org.rapla/rapla-app/pom.xml` | 48 KB | Build-time embedded copy of `rapla-app/pom.xml` |
| `META-INF/MANIFEST.MF` | 561 B | Spring Boot manifest: `Main-Class: PropertiesLauncher`, `Start-Class: RaplaSpringBootApplication` |
| `META-INF/services/java.nio.file.spi.FileSystemProvider` | 66 B | NIO FileSystem provider registration (Spring Boot's nested-jar provider) |
| `META-INF/maven/org.rapla/rapla-app/pom.properties` | 60 B | `groupId=org.rapla` `artifactId=rapla-app` `version=2.1-SNAPSHOT` |

(`META-INF/spring/AutoConfiguration.imports` etc. live INSIDE the per-module
JARs in `BOOT-INF/lib/`, not at the fat-JAR root.)

---

## What's NOT in the fat JAR

Operator-side artifacts that ship separately and get composed at runtime via
`loader.properties:loader.path=lib/,plugins/`:

| Path | Source | Size | Role |
|---|---|---:|---|
| `/opt/rapla/plugins/dhbwrapla.jar` | dhbwrapla aggregator build | 240 KB | DHBW plugin code + `openapi/dhbw.json` + `AutoConfiguration.imports` |
| `/opt/rapla/lib/jtds-1.3.1.jar` | dhbwrapla runtime deps | 310 KB | Dualis SQL Server driver |
| `/opt/rapla/lib/jcifs-2.1.10.jar` | dhbwrapla runtime deps | 1.18 MB | NTLM / SMB for DhbwNtlmAuthStore |
| `/opt/rapla/lib/unboundid-ldapsdk-6.0.11.jar` | dhbwrapla runtime deps | 5.45 MB | LDAP client |
| `/opt/rapla/lib/jakarta.xml.bind-api-4.0.4.jar` | dhbwrapla runtime deps | 128 KB | JAXB for Morada XML |
| `/opt/rapla/lib/jaxb-{impl,core}-4.0.6.jar` | dhbwrapla runtime deps | 1.13 MB | JAXB implementation |
| `/opt/rapla/lib/jakarta.activation-api-2.1.4.jar` | dhbwrapla runtime deps | 66 KB | Activation API |
| `/opt/rapla/lib/angus-activation-2.0.3.jar` | dhbwrapla runtime deps | 26 KB | Angus activation provider |
| `/opt/rapla/lib/HikariCP-7.0.2.jar` | dhbwrapla runtime deps | 168 KB | (Already in fat JAR too — provided-scoped) |
| `/opt/rapla/lib/bcprov-jdk15on-1.61.jar` | dhbwrapla runtime deps | 4.33 MB | BouncyCastle (used by jcifs) |
| `/opt/rapla/lib/annotations-24.1.0.jar` | dhbwrapla compile-only | 30 KB | JetBrains annotations |
| `/opt/rapla/data/rapla-hsqldb.*` | Live HSQLDB | ~660 MB | The actual rapla database |

**Swagger UI itself** loads from `https://cdn.jsdelivr.net/npm/swagger-ui-dist/`
at browser request time — not bundled.

---

## Trim opportunities (largest → smallest impact)

1. **Material Icons font variants** — 1.85 MB across 10 files. If the SPA uses only one variant (most apps do), 4 variants × ~330 KB each = ~1.3 MB savings. Change the Angular `@angular/material` font import config.
2. **GraphQL stack** — `graphql-java` (3.81 MB) + `spring-graphql` (715 KB) + `reactor-core` (1.94 MB) ≈ 6.5 MB. Only used if `/api/graphql` is wanted (PRD 035 still in design). Removing `spring-boot-starter-graphql` from `rapla-app/pom.xml` excises the whole subtree if you're not shipping GraphQL yet.
3. **Exchange connector** — `ews-java-api` (1.20 MB) + `joda-time` (622 KB) ≈ 1.8 MB. Loaded only if `rapla.exchange.enabled=true` (off on rapla-test). If a node will never enable Exchange, `<exclusion>` on the Exchange transitive in `rapla-server/pom.xml` saves it.
4. **Bootstrap CSS** — `bootstrap.min.css` (126 KB) is bundled but the SPA doesn't use it; only the server-rendered pages (`/server`, `/login`, `/rapla/calendar`) do. If those pages move into the SPA, the file can go.
5. **`webclient/rxjava-3.1.5.jar`** — 2.82 MB, Swing-only. Trim path goes through eliminating RxJava from the Swing client (large refactor).

---

## Regeneration

Re-run this inventory anytime with:

```bash
JAR=/home/chris/git/rapla/rapla-app/target/rapla-2.1-SNAPSHOT.jar
unzip -l "$JAR" | awk '/^[[:space:]]*[0-9]/ && NF >= 4 {sum+=$1; n++} END {printf "%d files, %d bytes uncompressed\n", n, sum}'
unzip -l "$JAR" | awk '/^[[:space:]]*[0-9]/ && $NF ~ /\.jar$/' | sort -rn -k1
```
