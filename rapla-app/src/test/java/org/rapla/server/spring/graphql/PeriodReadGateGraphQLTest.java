package org.rapla.server.spring.graphql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.facade.RaplaFacade;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.StorageOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 113 § 5e part 2 — {@code periods} lists every rapla:period the caller may read information of
 * ({@code canReadInformation}, admin bypass), regardless of its categories, sorted by start.
 *
 * <p>Fixture (testdefault.xml): homer (admin), monty (non-admin; member of my-group and powerplant, not of
 * powerplant-staff). New periods copy the period type's everyone-READ row.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class PeriodReadGateGraphQLTest
{
    private static final ParameterizedTypeReference<Map<String, Object>> ROW = new ParameterizedTypeReference<>() {};
    private static final String QUERY = "{ periods { id name start } }";

    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = PeriodReadGateGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
        {
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
    @Autowired
    CachableStorageOperator operator;
    @Autowired
    RaplaFacade facade;

    WebTestClient client;
    HttpGraphQlTester tester;
    User homer;

    @BeforeEach
    void setUp() throws Exception
    {
        client = MockMvcWebTestClient.bindTo(mockMvc).build();
        tester = HttpGraphQlTester.builder(client.mutate()).url("/api/graphql").build();
        homer = operator.getUser("homer");
    }

    /** Stores a period; {@code category} null = uncategorised; {@code readGroup} non-null replaces the rows by one READ row for it. */
    private String period(LocalDateTime start, Category category, Category readGroup) throws Exception
    {
        Classification c = operator.getDynamicType(StorageOperator.PERIOD_TYPE).newClassification();
        c.setValue("name", "P6-" + UUID.randomUUID());
        c.setValue("start", start);
        c.setValue("end", start.plusMonths(3));
        if (category != null)
        {
            Attribute att = c.getAttribute("category");
            c.setValues(att, List.of(category));
        }
        Allocatable a = facade.newAllocatable(c, homer);
        if (readGroup != null)
        {
            for (Permission p : a.getPermissionList().toArray(new Permission[0]))
            {
                a.removePermission(p);
            }
            Permission read = a.newPermission();
            read.setGroup(readGroup);
            read.setAccessLevel(Permission.AccessLevel.READ);
            a.addPermission(read);
        }
        facade.storeObjects(new Entity[] { a });
        return a.getId();
    }

    private Category outsideUserGroups()
    {
        for (Category c : operator.getSuperCategory().getCategories())
        {
            if (!"user-groups".equals(c.getKey())) return c;
        }
        throw new AssertionError("fixture must have a category outside user-groups");
    }

    private List<String> ids()
    {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> row : tester.document(QUERY).execute().path("periods").entityList(ROW).get())
        {
            out.add((String) row.get("id"));
        }
        return out;
    }

    private String raw()
    {
        return client.post().uri("/api/graphql").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", QUERY)).exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
    }

    private static void runAs(String username, boolean admin)
    {
        List<SimpleGrantedAuthority> roles = admin ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN")) : List.of();
        TestSecurityContextHolder.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new org.springframework.security.core.userdetails.User(username, "x", roles), null, roles));
    }

    @Test
    @WithMockUser(username = "monty")
    void categorisedPeriodStaysListed() throws Exception
    {
        String categorised = period(LocalDateTime.of(2026, 4, 1, 0, 0), outsideUserGroups(), null);
        assertTrue(ids().contains(categorised), "a period with categories must still be listed");
    }

    @Test
    @WithMockUser(username = "monty")
    void defaultPeriodsVisibleToNonAdmin() throws Exception
    {
        String plain = period(LocalDateTime.of(2026, 5, 1, 0, 0), null, null);
        assertTrue(ids().contains(plain), "the copied everyone-READ row makes a period visible");
    }

    @Test
    @WithMockUser(username = "monty")
    void unreadablePeriodAbsent() throws Exception
    {
        period(LocalDateTime.of(2026, 6, 1, 0, 0), null, null);
        String before = raw();

        Category staff = operator.getSuperCategory().getCategory("user-groups").getCategory("powerplant").getCategory("powerplant-staff");
        String hidden = period(LocalDateTime.of(2026, 7, 1, 0, 0), null, staff);
        assertEquals(before, raw(), "§12 — the response is byte-identical to the one without the hidden period");

        runAs("homer", true);
        assertTrue(ids().contains(hidden), "admin bypass");
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void sortedByStart() throws Exception
    {
        period(LocalDateTime.of(2027, 1, 1, 0, 0), null, null);
        period(LocalDateTime.of(2025, 1, 1, 0, 0), outsideUserGroups(), null);
        List<LocalDateTime> starts = new ArrayList<>();
        for (Map<String, Object> row : tester.document(QUERY).execute().path("periods").entityList(ROW).get())
        {
            starts.add(LocalDateTime.parse((String) row.get("start")));
        }
        assertFalse(starts.size() < 2, "fixture must list at least the two seeded periods");
        List<LocalDateTime> sorted = new ArrayList<>(starts);
        sorted.sort(null);
        assertEquals(sorted, starts, "periods are sorted by start ascending");
    }
}
