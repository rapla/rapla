package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class GraphQlEndpointAuthGateTest
{
    private static final String QUERY = "{\"query\":\"{ __typename }\"}";

    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = GraphQlEndpointAuthGateTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml must be on the classpath");
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

    @Test
    void anonymousGraphqlPostIsRejected() throws Exception
    {
        mockMvc.perform(post("/api/graphql").contentType(MediaType.APPLICATION_JSON).content(QUERY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousSchemaStaysPublic() throws Exception
    {
        mockMvc.perform(get("/api/graphql/schema"))
                .andExpect(status().isOk());
    }

    @Test
    void bearerGraphqlPostSucceeds() throws Exception
    {
        String token = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        mockMvc.perform(post("/api/graphql")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QUERY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.__typename").value("Query"));
    }
}
