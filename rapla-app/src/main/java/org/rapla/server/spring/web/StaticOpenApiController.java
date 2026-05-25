package org.rapla.server.spring.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import tools.jackson.databind.ObjectMapper;

/**
 * PRD 041 — serves the four OpenAPI group specs captured at build time
 * ({@link OpenApiSpecCaptureTest}) so production runtime needs neither SpringDoc
 * nor its transitive Jackson 2 dependency.
 *
 * <p>Conditional on SpringDoc <em>not</em> being on the classpath:
 * <ul>
 *   <li>Production runtime — SpringDoc is test-scope, so this controller is active.</li>
 *   <li>Test runtime — SpringDoc is on the test classpath, so its
 *       {@code OpenApiWebMvcResource} owns these URLs and this controller stays
 *       dormant. Lets {@link OpenApiSpecCaptureTest} fetch live specs to capture.</li>
 * </ul>
 *
 * <p><b>Why no class-level {@code @RequestMapping}:</b> the spec-serving endpoints
 * are meta (they describe the API; they're not the API). Putting them under any
 * SpringDocGroupsConfig group would be self-referential. Class-level mapping kept
 * but ApiPrefixArchitectureTest exempts this class via {@code ALLOWED_NON_API}
 * — same precedent as {@link IndexPageController} / {@link StatusPageController}.
 */
@RestController
@RequestMapping("/api/v3/api-docs")
@ConditionalOnMissingClass("org.springdoc.webmvc.api.OpenApiWebMvcResource")
public class StaticOpenApiController
{
    /**
     * The four PRD 031 group names. Must mirror {@code OpenApiSpecCaptureTest.GROUPS}
     * and the file names committed under {@code src/main/resources/openapi/}. If a
     * group is added there, add it here too — there's no auto-discovery on purpose
     * (so a missing capture file fails loudly at startup, not at first request).
     *
     * <p>Plugins contribute additional groups by declaring a
     * {@link OpenApiSpecContribution} bean — see that class's javadoc.
     */
    static final List<String> GROUPS = List.of("auth", "client", "rest", "exports");

    private static final String CLASSPATH_PREFIX = "openapi/";

    private final Map<String, byte[]> specBytes = new ConcurrentHashMap<>();
    private final List<String> orderedGroupNames;
    private final byte[] swaggerConfigBytes;

    public StaticOpenApiController(List<OpenApiSpecContribution> pluginSpecs)
    {
        List<String> allGroups = new ArrayList<>(GROUPS);
        for (String group : GROUPS)
        {
            specBytes.put(group, loadOrFail(CLASSPATH_PREFIX + group + ".json"));
        }
        for (OpenApiSpecContribution contribution : pluginSpecs)
        {
            if (specBytes.containsKey(contribution.name()))
            {
                throw new IllegalStateException("Plugin OpenAPI group name '" + contribution.name()
                        + "' collides with a rapla built-in or another plugin contribution.");
            }
            specBytes.put(contribution.name(), loadOrFail(contribution.classpathLocation()));
            allGroups.add(contribution.name());
        }
        this.orderedGroupNames = List.copyOf(allGroups);
        this.swaggerConfigBytes = buildSwaggerConfig(orderedGroupNames);
    }

    /**
     * Default endpoint — Swagger UI's {@code configUrl} fallback. Returns the
     * group inventory so the UI's dropdown can list every group.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> apiDocs()
    {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(swaggerConfigBytes);
    }

    /**
     * Swagger UI's group-discovery endpoint. {@code SwaggerUIBundle} fetches this
     * to render the dropdown.
     */
    @GetMapping(value = "/swagger-config", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> swaggerConfig()
    {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(swaggerConfigBytes);
    }

    /**
     * Per-group spec — the actual OpenAPI document. Returns 404 for unknown groups
     * (no leakage of internal directory structure).
     */
    @GetMapping(value = "/{group}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> groupSpec(@PathVariable("group") String group)
    {
        byte[] body = specBytes.get(group);
        if (body == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private static byte[] loadOrFail(String classpath)
    {
        ClassPathResource res = new ClassPathResource(classpath);
        if (!res.exists())
        {
            throw new IllegalStateException(
                    "Missing OpenAPI spec on classpath: " + classpath
                            + " — run `mvn -pl rapla-app -am test -Dtest=OpenApiSpecCaptureTest "
                            + "-Dopenapi.update=true -Dgroups=e2e` to regenerate.");
        }
        try (InputStream in = res.getInputStream())
        {
            return in.readAllBytes();
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Failed to read " + classpath, e);
        }
    }

    /**
     * Builds the Swagger UI {@code swagger-config} payload — same shape SpringDoc
     * emits, so the bundled UI's group dropdown works without further config.
     */
    private static byte[] buildSwaggerConfig(List<String> groupNames)
    {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("configUrl", "/api/v3/api-docs/swagger-config");
        // Deliberately NOT emitting oauth2RedirectUrl — Swagger UI's swagger-config
        // value would OVERRIDE the JS-side window.location.origin + "/swagger-ui/
        // oauth2-redirect.html" computed in static/swagger-ui/index.html. We want
        // the JS-side absolute URL so Spring AS validates the registered
        // http://localhost:8051/swagger-ui/oauth2-redirect.html. A relative path
        // in this field would break the OAuth flow with HTTP 400 invalid_redirect_uri.
        config.put("validatorUrl", "");
        List<Map<String, String>> urls = groupNames.stream()
                .map(g -> Map.of("name", g, "url", "/api/v3/api-docs/" + g))
                .toList();
        config.put("urls", urls);
        try
        {
            return new ObjectMapper().writeValueAsString(config).getBytes(StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Failed to build swagger-config payload", e);
        }
    }
}
