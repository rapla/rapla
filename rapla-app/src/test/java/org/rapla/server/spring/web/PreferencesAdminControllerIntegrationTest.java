package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.adminpanels.ActionResult;
import org.rapla.plugin.adminpanels.Field;
import org.rapla.plugin.adminpanels.FieldType;
import org.rapla.plugin.adminpanels.PanelDefinition;
import org.rapla.plugin.adminpanels.PanelScope;
import org.rapla.server.adminpanels.PreferencesPanel;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Round-trips list / get / save / invokeAction against the
 *  {@code PreferencesAdminController}. Two stub {@link PreferencesPanel}
 *  beans exercise SYSTEM + PER_USER scopes; tests verify the scope filter,
 *  admin gate, and that values persist + come back through the wire. */
@SpringBootTest(classes = {RaplaSpringBootApplication.class,
        PreferencesAdminControllerIntegrationTest.StubPanelsConfig.class})
@AutoConfigureMockMvc
@Tag("e2e")
class PreferencesAdminControllerIntegrationTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = PreferencesAdminControllerIntegrationTest.class.getResourceAsStream("/testdefault.xml"))
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

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RecordingPanel recordingPanel;

    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void resetRecordingPanel()
    {
        recordingPanel.lastSavedValue.set("hello");
    }

    /** In {@code testdefault.xml}, {@code homer/duffs} has {@code isAdmin=true}
     *  and {@code monty/burns} is a regular user. (The "admin/empty-password"
     *  default in AGENTS.md §8 belongs to the production seed, not this fixture.) */
    private String adminToken() throws Exception
    {
        return loginAs("homer", "duffs");
    }

    private String userToken() throws Exception
    {
        return loginAs("monty", "burns");
    }

    private String loginAs(String username, String password) throws Exception
    {
        MvcResult mvc = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tree = json.readTree(mvc.getResponse().getContentAsString());
        return tree.get("accessToken").asString();
    }

    @Test
    void listPanelsSystemRequiresAdmin() throws Exception
    {
        mockMvc.perform(get("/api/admin/panels?scope=SYSTEM")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));   // non-admin sees no SYSTEM panels
    }

    @Test
    void listPanelsSystemForAdmin() throws Exception
    {
        mockMvc.perform(get("/api/admin/panels?scope=SYSTEM")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'stub.system')]").exists())
                .andExpect(jsonPath("$[?(@.id == 'stub.peruser')]").doesNotExist());
    }

    @Test
    void listPanelsPerUserVisibleToAll() throws Exception
    {
        mockMvc.perform(get("/api/admin/panels?scope=PER_USER")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'stub.peruser')]").exists())
                .andExpect(jsonPath("$[?(@.id == 'stub.system')]").doesNotExist());
    }

    @Test
    void getPanelReturnsDefinition() throws Exception
    {
        mockMvc.perform(get("/api/admin/panels/stub.system")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("stub.system"))
                .andExpect(jsonPath("$.title").value("Stub system panel"))
                .andExpect(jsonPath("$.fields[0].key").value("greeting"))
                .andExpect(jsonPath("$.fields[0].type").value("TEXT"))
                .andExpect(jsonPath("$.values.greeting").value("hello"));
    }

    @Test
    void savePanelPersists() throws Exception
    {
        String body = "{\"greeting\":\"world\"}";
        mockMvc.perform(post("/api/admin/panels/stub.system/save")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.values.greeting").value("world"));
        assertEquals("world", recordingPanel.lastSavedValue.get());
    }

    @Test
    void invokeActionRoundTrips() throws Exception
    {
        String body = "{\"greeting\":\"echo me\"}";
        mockMvc.perform(post("/api/admin/panels/stub.system/action/echo")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("you said: echo me"));
    }

    @Test
    void getPanelDeniedForNonAdminOnSystemPanel() throws Exception
    {
        mockMvc.perform(get("/api/admin/panels/stub.system")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isUnauthorized());
    }

    /** Test-only beans — two stub panels covering SYSTEM + PER_USER, plus the
     *  RecordingPanel reference so the test can assert on save side effects. */
    @TestConfiguration
    static class StubPanelsConfig
    {
        @Bean
        public RecordingPanel recordingPanel()
        {
            return new RecordingPanel();
        }

        @Bean
        public PreferencesPanel stubPerUserPanel()
        {
            return new PreferencesPanel()
            {
                @Override public String getId() { return "stub.peruser"; }
                @Override public PanelScope scope() { return PanelScope.PER_USER; }
                @Override public List<String> path() { return List.of("My preferences"); }
                @Override public String title(Locale locale) { return "Stub user panel"; }
                @Override public PanelDefinition getDefinition(Locale locale, User user)
                {
                    return new PanelDefinition(getId(), scope(), path(), title(locale), null,
                            List.of(new Field("flag", "Flag", FieldType.BOOL, null, false, Map.of())),
                            List.of(),
                            Map.of("flag", true));
                }
                @Override public PanelDefinition save(User user, Map<String, Object> values)
                {
                    return getDefinition(Locale.getDefault(), user);
                }
                @Override public ActionResult invokeAction(User user, String actionId, Map<String, Object> currentValues)
                {
                    return ActionResult.ok("noop");
                }
            };
        }
    }

    /** SYSTEM-scope stub panel that records its last-saved value so a test can
     *  assert that save actually round-tripped through the controller. */
    static class RecordingPanel implements PreferencesPanel
    {
        final AtomicReference<String> lastSavedValue = new AtomicReference<>("hello");

        @Override public String getId() { return "stub.system"; }
        @Override public PanelScope scope() { return PanelScope.SYSTEM; }
        @Override public List<String> path() { return List.of("Test", "Stub"); }
        @Override public String title(Locale locale) { return "Stub system panel"; }

        @Override public PanelDefinition getDefinition(Locale locale, User user)
        {
            return new PanelDefinition(getId(), scope(), path(), title(locale),
                    "stub for testing",
                    List.of(new Field("greeting", "Greeting", FieldType.TEXT, null, false, Map.of())),
                    List.of(/* echo action only declared in actions, action key matched in invokeAction */),
                    Map.of("greeting", lastSavedValue.get()));
        }

        @Override public PanelDefinition save(User user, Map<String, Object> values) throws RaplaException
        {
            lastSavedValue.set(String.valueOf(values.get("greeting")));
            return getDefinition(Locale.getDefault(), user);
        }

        @Override public ActionResult invokeAction(User user, String actionId, Map<String, Object> currentValues)
        {
            assertEquals("echo", actionId);
            return ActionResult.ok("you said: " + currentValues.get("greeting"));
        }
    }
}
