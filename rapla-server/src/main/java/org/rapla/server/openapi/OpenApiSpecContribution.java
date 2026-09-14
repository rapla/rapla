package org.rapla.server.openapi;

/**
 * A plugin-contributed OpenAPI spec that should appear as its own group in
 * SwaggerUI alongside rapla's built-in {@code auth} / {@code client} /
 * {@code rest} / {@code exports} groups.
 *
 * <p>A plugin contributes by declaring a {@code @Bean OpenApiSpecContribution}
 * in its Spring auto-config and shipping the JSON file at the named classpath
 * location inside its jar. {@code StaticOpenApiController} in rapla-app
 * collects every contribution at startup, loads the bytes once, and serves
 * them at {@code /api/v3/api-docs/<name>}.
 *
 * <p>Lives in rapla-server (not rapla-app) so plugin jars — which depend on
 * rapla-server at {@code provided} scope but not on rapla-app — can declare
 * a bean of this type at compile time. At runtime in the operator's
 * deployable, the class is on the classpath via rapla-app's transitive
 * dependency on rapla-server.
 *
 * <p>Names must be unique across the rapla built-in groups and all plugins; a
 * collision fails startup with a clear message rather than silently shadowing
 * one of them. Plugin authors should namespace with the plugin's short id
 * (e.g. {@code dhbw}, {@code exchange}).
 *
 * @param name              dropdown label + URL segment, e.g. {@code "dhbw"}
 * @param classpathLocation classpath path of the JSON spec, e.g.
 *                          {@code "openapi/dhbw.json"} — must resolve via
 *                          {@code ClassPathResource} at startup or the
 *                          controller fails fast
 */
public record OpenApiSpecContribution(String name, String classpathLocation)
{
}
