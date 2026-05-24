package org.rapla.server.spring.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 041 — captures the four OpenAPI group specs as static build artifacts.
 *
 * <p>Spec files live under {@code rapla-app/src/main/resources/openapi/}. At runtime
 * {@link StaticOpenApiController} serves them at {@code /api/v3/api-docs} +
 * {@code /api/v3/api-docs/{group}}. SpringDoc, swagger-core, and the transitive
 * Jackson 2 stay in test scope only.
 *
 * <h2>Two modes</h2>
 * <ul>
 *   <li><b>Default (CI-friendly):</b> capture the live spec from this test's Spring
 *       context, compare to the committed file in {@code src/main/resources/openapi/},
 *       fail on byte-mismatch with a "run with -Dopenapi.update=true" hint. Catches
 *       drift from controller / DTO changes that need a spec refresh.</li>
 *   <li><b>Update mode</b> ({@code -Dopenapi.update=true}): overwrite the committed
 *       file with the freshly-captured spec. Used by devs after intentional changes.</li>
 * </ul>
 *
 * <p>The fixture controller from {@link OpenApiFieldDiscoveryFixture} is NOT enabled
 * here so it doesn't pollute the production spec — {@link OpenApiSmokeTest} validates
 * field-based-introspection separately.
 */
@SpringBootTest(classes = {RaplaSpringBootApplication.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    // springdoc settings are test-only — see the comment in application.yml.
    // api-docs.path overrides springdoc's default /v3/api-docs so it lands under
    // the /api/ prefix this test (and the runtime StaticOpenApiController) expects.
    // default-produces-media-type makes endpoints without explicit produces=...
    // serialize as application/json in the captured spec, so generated TS clients
    // don't treat responses as Blobs.
    "springdoc.api-docs.path=/api/v3/api-docs",
    "springdoc.default-produces-media-type=application/json"
})
@Tag("e2e")
class OpenApiSpecCaptureTest
{
    /** The four PRD 031 group specs we capture. */
    private static final List<String> GROUPS = List.of("auth", "client", "rest", "exports");

    /**
     * Where the committed specs live. Resolved by locating the {@code rapla-app}
     * module directory anchored on this test class's classpath URL — surefire runs
     * with {@code forkCount=0} (per rapla-bom plugin config), so the JVM CWD is
     * wherever Maven was invoked (usually the reactor root), NOT the module dir.
     * Don't change this to a CWD-relative path.
     */
    private static final Path SPEC_DIR = locateSpecDir();

    private static Path locateSpecDir()
    {
        try
        {
            // test-classes URL → walk up to find pom.xml of rapla-app
            Path testClassesDir = Paths.get(OpenApiSpecCaptureTest.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI());
            Path moduleRoot = testClassesDir;
            while (moduleRoot != null && !Files.exists(moduleRoot.resolve("pom.xml")))
            {
                moduleRoot = moduleRoot.getParent();
            }
            if (moduleRoot == null)
            {
                throw new IllegalStateException("Couldn't find module root from " + testClassesDir);
            }
            return moduleRoot.resolve("src/main/resources/openapi");
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Failed to resolve spec dir", e);
        }
    }

    @TempDir static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = OpenApiSpecCaptureTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in);
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
        // Production spec — leave the test fixture out.
    }

    @Autowired MockMvc mockMvc;

    @TestFactory
    Stream<DynamicTest> capturesEachGroup()
    {
        boolean update = Boolean.getBoolean("openapi.update");
        return GROUPS.stream().map(group -> DynamicTest.dynamicTest(
                "openapi/" + group + ".json " + (update ? "(update)" : "(verify)"),
                () -> captureOrVerify(group, update)));
    }

    private void captureOrVerify(String group, boolean update) throws Exception
    {
        String fresh = pretty(fetchGroupSpec(group));
        Path target = SPEC_DIR.resolve(group + ".json");

        if (update)
        {
            Files.createDirectories(target.getParent());
            Files.writeString(target, fresh);
            return;
        }

        if (!Files.exists(target))
        {
            fail("Missing committed spec " + target.toAbsolutePath()
                    + " — run `mvn -pl rapla-app -am test -Dtest=OpenApiSpecCaptureTest "
                    + "-Dopenapi.update=true -Dgroups=e2e` to generate it.");
        }
        String committed = Files.readString(target);
        if (!fresh.equals(committed))
        {
            fail("OpenAPI spec drift in " + target + ".\n"
                    + "Controllers / DTOs changed but the committed spec wasn't refreshed.\n"
                    + "If the change is intentional, regenerate:\n"
                    + "  mvn -pl rapla-app -am test -Dtest=OpenApiSpecCaptureTest "
                    + "-Dopenapi.update=true -Dgroups=e2e\n"
                    + "Then commit the diff under " + SPEC_DIR + "/.");
        }
    }

    private String fetchGroupSpec(String group) throws Exception
    {
        MvcResult mvc = mockMvc.perform(get("/api/v3/api-docs/" + group))
                .andExpect(status().isOk())
                .andReturn();
        return mvc.getResponse().getContentAsString();
    }

    /**
     * Pretty-print + post-process so committed JSON diffs are readable AND the
     * spec is suitable for runtime serving.
     *
     * <p><b>Strips the top-level {@code servers} block:</b> springdoc captures it as
     * {@code [{"url":"http://localhost"}]} during MockMvc tests (no real Tomcat
     * → no port). At runtime both Scalar and Swagger UI would resolve the
     * relative {@code /oauth2/authorize} against that, sending the browser to
     * {@code http://localhost:80} (port 80) instead of the actual server port,
     * causing ERR_CONNECTION_REFUSED on the OAuth flow. With {@code servers}
     * removed, both explorers fall back to {@code window.location.origin} —
     * whichever port the user actually visited.
     */
    private static String pretty(String json) throws Exception
    {
        ObjectMapper m = new ObjectMapper();
        java.util.LinkedHashMap<?, ?> tree = m.readValue(json, java.util.LinkedHashMap.class);
        tree.remove("servers");
        return m.writerWithDefaultPrettyPrinter().writeValueAsString(tree) + "\n";
    }
}
