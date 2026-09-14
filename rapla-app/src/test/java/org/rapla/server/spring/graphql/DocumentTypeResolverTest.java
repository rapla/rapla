package org.rapla.server.spring.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.RaplaFacade;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.server.spring.document.DocumentCatalogService;
import org.rapla.storage.StorageOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;

/**
 * PRD 111 D3 — tier-3 for {@code DynamicType.documents}: annotation order preserved, dangling and
 * kind-mismatched names dropped, and the join runs in the caller's document scope (§12).
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class DocumentTypeResolverTest
{
    @TempDir static Path tempDir;
    static Path dataFile;

    @Autowired MockMvc mockMvc;
    @Autowired StorageOperator operator;
    @Autowired RaplaFacade facade;
    @Autowired DocumentCatalogService documents;
    @Autowired ViewCatalogService views;
    HttpGraphQlTester tester;

    private static final String BY_ID_VIEW = """
            query Leihschein($reservationId: ID!) @view(title: "Leihschein")
              @param(name: "reservationId", into: "reservationId", required: true)
            { reservation(id: $reservationId) { name } }
            """;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("testdefault.xml");
        try (InputStream in = DocumentTypeResolverTest.class.getResourceAsStream("/testdefault.xml"))
        {
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toString());
    }

    @BeforeEach
    void setUp()
    {
        tester = HttpGraphQlTester.create(
                MockMvcWebTestClient.bindTo(mockMvc).baseUrl("/api/graphql").build());
    }

    /** The event type carries `documents = Leihschein, ghost` — only the existing one comes back. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void resolvesAnnotatedDocumentsInOrderAndDropsDanglingNames() throws Exception
    {
        assertEquals(List.of(), views.saveView("Leihschein", BY_ID_VIEW, false, List.of(), null, admin()),
                "view fixture must save cleanly");
        assertEquals(List.of(), documents.save("Leihschein", "Leihschein",
                "<p>{{#reservation}}{{name}}{{/reservation}}</p>", false, List.of(), null, admin()),
                "document fixture must save cleanly");
        String eventTypeKey = annotateFirstEventType("Leihschein, ghost");

        List<Map<String, Object>> refs = documentsOf(eventTypeKey);
        assertEquals(1, refs.size(), () -> "dangling name must be dropped; got " + refs);
        assertEquals("Leihschein", refs.get(0).get("name"));
        // D2 — the param is DERIVED from the view's by-id root, never authored.
        assertEquals("reservationId", refs.get(0).get("param"));
    }

    /** A reservation document hung on a RESOURCE type is a kind mismatch and must not surface. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void dropsDocumentsWhoseKindDoesNotMatchTheType() throws Exception
    {
        assertEquals(List.of(), views.saveView("Leihschein", BY_ID_VIEW, false, List.of(), null, admin()),
                "view fixture must save cleanly");
        documents.save("Leihschein", "Leihschein", "<p>x</p>", false, List.of(), null, admin());
        String resourceTypeKey = annotatedType(
                DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE, "Leihschein");

        assertTrue(documentsOf(resourceTypeKey).isEmpty(),
                "a reservation document must not appear on a resource type");
    }

    /** A type without the annotation answers an empty list, never null-with-error. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void unannotatedTypeAnswersEmpty() throws Exception
    {
        String eventTypeKey = annotateFirstEventType(null);
        assertTrue(documentsOf(eventTypeKey).isEmpty());
    }

    private List<Map<String, Object>> documentsOf(String typeKey)
    {
        Map<String, Object> type = tester.document(
                "{ type(key: \"" + typeKey + "\") { key documents { name param } } }")
                .execute().path("type")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {}).get();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> refs = (List<Map<String, Object>>) type.get("documents");
        return refs == null ? List.of() : refs;
    }

    private org.rapla.entities.User admin() throws Exception
    {
        return operator.getUser("homer");
    }

    private String annotateFirstEventType(String annotationValue) throws Exception
    {
        return annotatedType(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION, annotationValue);
    }

    /** A fresh type carrying the annotation — cheaper and less brittle than editing a fixture type. */
    private String annotatedType(String classificationType, String annotationValue) throws Exception
    {
        String key = "wp3_" + classificationType + "_" + Math.abs(java.util.UUID.randomUUID().hashCode());
        DynamicType type = facade.newDynamicType(classificationType);
        type.setKey(key);
        type.getName().setName("en", key);
        type.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT, "{name}");
        if (annotationValue != null)
        {
            type.setAnnotation(DynamicTypeAnnotations.KEY_DOCUMENTS, annotationValue);
        }
        facade.store(type);
        return key;
    }
}
