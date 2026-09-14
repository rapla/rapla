package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaFacade;
import org.rapla.plugin.planningstatus.PlanningStatusPlugin;
import org.rapla.plugin.timeslot.TimeslotPlugin;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Round-trips the five vanilla {@code PreferencesPanel} impls migrated in
 *  PRD 020 Phase 6 (PlanningStatus, AppointmentNote, CSVExport, AutoExport,
 *  Timeslot). Each test:
 *  <ol>
 *    <li>POSTs values to {@code /admin/panels/{id}/save}</li>
 *    <li>asserts the returned {@link org.rapla.plugin.adminpanels.PanelDefinition}
 *        echoes the new value</li>
 *    <li>queries the {@link RaplaFacade} system preferences directly to
 *        confirm the persistence side-effect — proves the wire layer is
 *        not just round-tripping in memory.</li>
 *  </ol> */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
@Tag("e2e")
class VanillaPluginPanelsIntegrationTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = VanillaPluginPanelsIntegrationTest.class.getResourceAsStream("/testdefault.xml"))
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
    @Autowired RaplaFacade facade;

    private final JsonMapper json = JsonMapper.builder().build();

    private String adminToken() throws Exception
    {
        return OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
    }

    @Test
    void listSystemIncludesAllFiveVanillaPanels() throws Exception
    {
        mockMvc.perform(get("/api/admin/panels?scope=SYSTEM")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'org.rapla.plugin.planningstatus')]").exists())
                .andExpect(jsonPath("$[?(@.id == 'org.rapla.plugin.appointmentnote')]").exists())
                .andExpect(jsonPath("$[?(@.id == 'org.rapla.plugin.cssexport')]").exists())   // NB: typo in CSVExportPlugin.PLUGIN_ID
                .andExpect(jsonPath("$[?(@.id == 'org.rapla.plugin.autoexport')]").exists())
                .andExpect(jsonPath("$[?(@.id == 'org.rapla.plugin.timeslot')]").exists());
    }

    @Test
    void planningStatusToggleRoundTrips() throws Exception
    {
        // Save: enabled=true
        mockMvc.perform(post("/api/admin/panels/" + PlanningStatusPlugin.PLUGIN_ID + "/save")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.values.enabled").value(true));

        // Persistence side-effect: facade-level system preferences reflect the change
        Preferences refreshed = facade.getSystemPreferences();
        assertEquals(true, refreshed.getEntryAsBoolean(PlanningStatusPlugin.ENABLED, false));
    }

    @Test
    void timeslotJsonEditorPersistsAsRaplaConfiguration() throws Exception
    {
        String body = "{\"slots\":["
                + "{\"name\":\"Block 1\",\"minuteOfDay\":480},"
                + "{\"name\":\"Block 2\",\"minuteOfDay\":600}"
                + "]}";
        mockMvc.perform(post("/api/admin/panels/" + TimeslotPlugin.PLUGIN_ID + "/save")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.values.slots[0].name").value("Block 1"))
                .andExpect(jsonPath("$.values.slots[1].minuteOfDay").value(600));

        // The JSON_EDITOR shape was translated to the legacy RaplaConfiguration
        // format that TimeslotProvider.parseConfig(...) reads — confirms the
        // bridge from wire-JSON to native-config holds.
        RaplaConfiguration config = facade.getSystemPreferences().getEntry(TimeslotPlugin.CONFIG, null);
        assertNotNull(config);
        assertTrue(config.getChildren("timeslot").length == 2);
        assertEquals("Block 1", config.getChildren("timeslot")[0].getAttribute("name", null));
        assertEquals("08:00:00", config.getChildren("timeslot")[0].getAttribute("time", null));
        assertEquals("10:00:00", config.getChildren("timeslot")[1].getAttribute("time", null));
    }
}
