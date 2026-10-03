package org.rapla.server.spring.graphql;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * PRD 120 Phase 3 (idIn bullet) — the nested {@code Appointment.resources(filter: {idIn})} matches a resource when
 * the resource or one of its ancestors is in idIn, over both kinds: a course matches via the group that packages it,
 * as the calendar's getDependentRef path already does (fixture: DozGruppe packages Burns Monty; reservation 02c84e71
 * allocates Burns Monty).
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Tag("e2e")
class AppointmentResourcesIdInHierarchyGraphQLTest
{
    private static final String RESERVATION = "02c84e71-5d80-458a-97ad-86822780dc09";
    private static final String DOZ_GRUPPE = "r9b69d90-46a0-41bb-94fa-82079b424c03";
    private static final String MONTY = "f92e9a11-c342-4413-a924-81eee17ccf92";

    @TempDir
    static Path tempDir;

    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = AppointmentResourcesIdInHierarchyGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml fixture missing from classpath");
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
    }

    @Autowired
    MockMvc mockMvc;

    HttpGraphQlTester tester;

    @BeforeEach
    void setUp()
    {
        tester = HttpGraphQlTester.builder(MockMvcWebTestClient.bindTo(mockMvc).build().mutate()).url("/api/graphql").build();
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void aCourseMatchesViaTheGroupThatPackagesIt()
    {
        List<Object> ids = resourceIds(DOZ_GRUPPE);
        assertEquals(List.of(MONTY), ids, "the packaged resource matches its group's id");
    }

    @SuppressWarnings("unchecked")
    private List<Object> resourceIds(String idIn)
    {
        String query = "{ reservation(id: \"" + RESERVATION + "\") { appointments { resources(filter: { idIn: [\"" + idIn + "\"] }) { id } } } }";
        List<Map<String, Object>> appointments = tester.document(query).execute().path("reservation.appointments")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {}).get();
        return appointments.stream()
                .flatMap(app -> ((List<Map<String, Object>>) app.get("resources")).stream())
                .map(r -> r.get("id")).distinct().toList();
    }
}
