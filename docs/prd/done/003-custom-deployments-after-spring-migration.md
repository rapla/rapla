# PRD 003: Custom Deployment Model After Spring Migration

**Status:** done — 2026-09-13 — superseded by [PRD 045](../045-end-user-deployment-and-db-config.md) (drop-in plugin JARs). The full planning record described one customer deployment's internal integrations; it moved to that deployment's private docs on 2026-09-14 (AGENTS.md §17).
**Date:** 2026-05-06

## Outcome — the generic pattern

- rapla-server ships as a Spring Boot `@AutoConfiguration` (`RaplaServerAutoConfiguration` + `META-INF/spring/AutoConfiguration.imports`); a custom deployment's own `@SpringBootApplication` pulls in the full server stack from the classpath (`AutoConfigImportTest`). No `@Import` of `RaplaSpringBootApplication`.
- Custom code registers through standard Spring DI: `@Component` / `@Service` (optionally `@Named("id")`) replace `@Extension` / `@DefaultImplementation`; extension points stay interfaces collected as `Set<T>` / `Map<String, T>`.
- Server-level settings move from `Preferences` into `@ConfigurationProperties`; secondary data sources are explicit `@Bean`s; optional plugins are gated with `@ConditionalOnProperty`.
- Scheduled jobs use `@Scheduled` and `@EventListener(ApplicationReadyEvent.class)` ([PRD 019](019-spring-boot-lifecycle-migration.md)).
- Custom Swing code lives in `rapla-client`; only `rapla-app` signs the `webclient/` jars (single signing).
- Drop-in plugin JARs and the deployment layout: [PRD 045](../045-end-user-deployment-and-db-config.md), [`docs/plugins.md`](../../plugins.md).
