package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD-follow-up (2026-06-05): the Spring-GraphQL schema printer endpoint
 * ({@code spring.graphql.schema.printer.enabled=true}) is served by
 * {@code SchemaHandler.handleRequest}, which hard-codes
 * {@code MediaType.TEXT_PLAIN} with <b>no charset</b>. The SDL bytes are
 * UTF-8 (em-dashes in the descriptions etc.), but a bare {@code text/plain}
 * header makes legacy browsers/clients fall back to ISO-8859-1 → mojibake
 * ("â€"" instead of "—").
 *
 * <p>{@code GraphQlSchemaCharsetFilter} compensates by stamping
 * {@code text/plain;charset=UTF-8} on responses for the schema path only
 * (option B — path-scoped, no side effect on the JSON {@code /api/graphql}
 * endpoint). This test pins both the public reachability (matches the
 * SecurityConfig whitelist) and the charset header.
 */
@SpringBootTest(classes = {RaplaSpringBootApplication.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.graphql.schema.printer.enabled=true"
})
@Tag("e2e")
class GraphQlSchemaCharsetTest
{
    @TempDir static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = GraphQlSchemaCharsetTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in);
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
    }

    @Autowired MockMvc mockMvc;

    @Test
    void schemaEndpointIsPublicAndDeclaresUtf8() throws Exception
    {
        mockMvc.perform(get("/api/graphql/schema"))
                .andExpect(status().isOk())
                // text/plain WITH charset — the whole point of the filter.
                .andExpect(header().string("Content-Type", "text/plain;charset=UTF-8"))
                .andExpect(content().contentTypeCompatibleWith("text/plain"));
    }
}
