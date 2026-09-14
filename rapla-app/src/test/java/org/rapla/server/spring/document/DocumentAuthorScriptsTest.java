package org.rapla.server.spring.document;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.User;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.server.spring.graphql.ViewCatalogService;
import org.rapla.server.spring.web.OAuthTestSupport;
import org.rapla.storage.StorageOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * PRD 097 D6c (2) — {@code rapla.documents.author-scripts=true}: an admin-authored document keeps
 * its {@code <script>} and is served under the scripted sandbox variant — but a PUBLIC document
 * (anonymous-readable) is always rendered strict, decided per response. Filters ON: the header is
 * written by the security chain after the controller marked the response.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class, properties = "rapla.documents.author-scripts=true")
@AutoConfigureMockMvc
class DocumentAuthorScriptsTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = DocumentAuthorScriptsTest.class.getResourceAsStream("/testdefault.xml"))
        {
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
    }

    private static final String VIEW = "scripts_view";
    private static final String TEMPLATE = "<p id=\"stamp\">{{serverTime}}</p><script>window.print()</script>";

    @Autowired MockMvc mockMvc;
    @Autowired ViewCatalogService views;
    @Autowired DocumentCatalogService documents;
    @Autowired StorageOperator operator;

    @BeforeEach
    void seed() throws Exception
    {
        User admin = operator.getUser("homer");
        String query = "query %s @view(title: \"Scripts\") { serverTime }".formatted(VIEW);
        assertEquals(List.of(), views.saveView(VIEW, query, true, List.of(), null, admin));
        assertEquals(List.of(), documents.save("scripts_private", VIEW, TEMPLATE, false, List.of(), null, admin));
        assertEquals(List.of(), documents.save("scripts_public", VIEW, TEMPLATE, true, List.of(), null, admin));
    }

    @Test
    void aNonPublicDocumentKeepsItsScriptUnderTheScriptedSandbox() throws Exception
    {
        MockHttpServletResponse response = render("scripts_private");
        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsString().contains("<script>window.print()</script>"), response.getContentAsString());
        String policy = response.getHeader("Content-Security-Policy");
        assertTrue(policy.contains("sandbox allow-scripts"), policy);
        assertTrue(policy.contains("script-src 'self' 'unsafe-inline'"), policy);
        assertFalse(policy.contains("allow-same-origin"), policy);
    }

    @Test
    void aPublicDocumentIsAlwaysRenderedStrict() throws Exception
    {
        MockHttpServletResponse response = render("scripts_public");
        assertEquals(200, response.getStatus());
        assertFalse(response.getContentAsString().contains("script"), response.getContentAsString());
        String policy = response.getHeader("Content-Security-Policy");
        assertTrue(policy.contains("script-src 'none'"), policy);
        assertFalse(policy.contains("allow-scripts"), policy);
    }

    private MockHttpServletResponse render(String name) throws Exception
    {
        String token = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        return mockMvc.perform(get("/api/documents/" + name).header("Authorization", "Bearer " + token))
                .andReturn().getResponse();
    }
}
