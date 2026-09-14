package org.rapla.server.spring.graphql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * PRD 095 — tier-3 tests for {@code AppointmentBlock.color}: the single effective
 * block color (event color, else first readable allocatable color), resolved via
 * {@code RaplaBuilder.getColorForClassifiable} + {@code BlockColors}. §12 leak rule
 * (PRD 095 D3): when the color-bearing contributor is not readable by the caller,
 * the color resolves to null but the block itself stays.
 *
 * <p>Fixture: testdefault.xml patched at copy time — the {@code room} type gets a
 * color-annotated attribute, "Room A66" gets the value {@code #ff0000} and loses its
 * everyone-permission (readable only by admins). Reservation 2 (readable by all via
 * its {@code read} permission row) allocates Room A66 → homer (admin) sees the color,
 * monty (non-admin) sees the block with a null color.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class AppointmentBlockColorGraphQLTest
{
    private static final String ROOM_A66_ID = "c24ce517-4697-4e52-9917-ec000c84563c";
    private static final String A66_PERMISSION = "<rapla:permission access=\"allocate_conflicts\"/>";

    @TempDir
    static Path tempDir;

    static Path dataFile;

    @BeforeAll
    static void copyPatchedFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        String xml;
        try (InputStream in = AppointmentBlockColorGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml fixture missing from classpath");
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        // 1. The room type gets a color-annotated string attribute.
        int roomDef = xml.indexOf("name=\"dynatt:room\"");
        assertTrue(roomDef > 0, "room type grammar not found");
        int insertAt = xml.indexOf("</doc:annotations>", roomDef);
        assertTrue(insertAt > 0, "room type annotations not found");
        insertAt += "</doc:annotations>".length();
        String colorAttr = """

                <relax:optional>
                   <relax:element name="colorattr">
                      <doc:name lang="en">color</doc:name>
                      <doc:annotations>
                         <rapla:annotation key="color">true</rapla:annotation>
                      </doc:annotations>
                      <relax:data type="string"/>
                   </relax:element>
                </relax:optional>
                """;
        xml = xml.substring(0, insertAt) + colorAttr + xml.substring(insertAt);

        // 2. Room A66 gets the color value…
        String seatsAnchor = "<dynatt:seats>30</dynatt:seats>";
        assertTrue(xml.indexOf(seatsAnchor) == xml.lastIndexOf(seatsAnchor), "Room A66 seats anchor not unique");
        xml = xml.replace(seatsAnchor, seatsAnchor + "<dynatt:colorattr>#ff0000</dynatt:colorattr>");

        // 3. …and loses its everyone-permission → readable only by admins.
        int a66 = xml.indexOf(ROOM_A66_ID + "\" created-at");
        assertTrue(a66 > 0, "Room A66 resource not found");
        int permAt = xml.indexOf(A66_PERMISSION, a66);
        assertTrue(permAt > 0, "Room A66 permission row not found");
        xml = xml.substring(0, permAt) + xml.substring(permAt + A66_PERMISSION.length());

        Files.writeString(dataFile, xml, StandardCharsets.UTF_8);
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
    }

    @Autowired
    MockMvc mockMvc;

    /** Reservation 2's single appointment on 2001-10-16 allocates the colored Room A66. */
    private static final String BLOCK_QUERY = """
            { appointmentBlocks(filter: { from: "2001-10-16T00:00:00", to: "2001-10-17T00:00:00" }) {
                name  color
            } }
            """;

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void adminSeesEffectiveAllocatableColor() throws Exception
    {
        mockMvc.perform(post("/api/graphql").contentType(MediaType.APPLICATION_JSON).content(gqlBody(BLOCK_QUERY)))
                .andExpect(jsonPath("$.data.appointmentBlocks[?(@.name=='Reservation 2')].color")
                        .value(contains("#ff0000")));
    }

    @Test
    @WithMockUser(username = "monty")
    void unreadableColorContributorNullsColorButKeepsBlock() throws Exception
    {
        mockMvc.perform(post("/api/graphql").contentType(MediaType.APPLICATION_JSON).content(gqlBody(BLOCK_QUERY)))
                // the block itself stays — monty can read the reservation
                .andExpect(jsonPath("$.data.appointmentBlocks[?(@.name=='Reservation 2')]").isNotEmpty())
                // …but the color of the unreadable contributor must not leak (D3: null, not drop)
                .andExpect(jsonPath("$.data.appointmentBlocks[?(@.name=='Reservation 2' && @.color != null)]")
                        .isEmpty());
    }

    private static String gqlBody(String query)
    {
        return "{\"query\":\"" + query.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\"}";
    }
}
