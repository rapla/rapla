package org.rapla.server.spring.graphql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import graphql.schema.idl.SchemaPrinter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/graphql/schema prints generated classification fields in attribute declaration order —
 * the SPA renders its form in that order. Fixture type {@code room} declares name, seats, belongsto.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class GraphQlSchemaOrderTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = GraphQlSchemaOrderTest.class.getResourceAsStream("/testdefault.xml"))
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
    HotSwappableGraphQlSource source;

    @Test
    void schemaListsClassificationFieldsInDeclarationOrder() throws Exception
    {
        String sdl = mockMvc.perform(get("/api/graphql/schema")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(isDeclarationOrder(roomBlock(sdl)), () -> "room fields not in declaration order:\n" + roomBlock(sdl));
    }

    /** Guard: the default (alphabetical) printer fails the same check, so the test above discriminates. */
    @Test
    void alphabeticalPrinterWouldFailTheOrderCheck()
    {
        String sorted = new SchemaPrinter().print(source.schema());
        assertTrue(!isDeclarationOrder(roomBlock(sorted)), "precondition: alphabetical order must differ");
    }

    private static String roomBlock(String sdl)
    {
        int start = sdl.indexOf("type roomClassification ");
        assertTrue(start >= 0, "roomClassification missing from the SDL");
        return sdl.substring(start, sdl.indexOf("\n}", start));
    }

    private static boolean isDeclarationOrder(String block)
    {
        int name = block.indexOf("\n  name:");
        int seats = block.indexOf("\n  seats:");
        int belongsto = block.indexOf("\n  belongsto:");
        return name >= 0 && name < seats && seats < belongsto;
    }
}
