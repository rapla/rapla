package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.RaplaFacade;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.impl.server.AdditiveMigrationState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 090 — tier-3 MockMvc coverage for {@code /api/admin/permission-migration/*}.
 *
 * <p>Drives the worklist directly (seeds a soft-deny allocatable + injects its id
 * into the worklist system pref) rather than relying on the boot-time freeze, so
 * the controller is exercised independently of {@code AdditivePermissionMigration}
 * (tested at tier 2). Verifies the admin gate (§12), the recompute-on-GET, and
 * resolve → acknowledged → drops off.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class PermissionMigrationControllerTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = PermissionMigrationControllerTest.class.getResourceAsStream("/testdefault.xml"))
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

    @Autowired MockMvc mockMvc;
    @Autowired RaplaFacade facade;

    /** Seed a user-below-group soft-deny on a fresh room and freeze its id into the
     * worklist pref. Returns the allocatable id. */
    private String seedSoftDenyInWorklist() throws Exception
    {
        User admin = facade.getUser("homer");
        User monty = facade.getUser("monty");
        DynamicType room = facade.getDynamicType("room");

        Allocatable a = facade.newAllocatable(room.newClassification(), admin);
        a.getClassification().setValue("name", "PRD090-soft-deny-room");
        for (Permission p : new java.util.ArrayList<>(a.getPermissionList())) a.removePermission(p);
        Permission g = a.newPermission();
        g.setGroup(facade.getUserGroupsCategory().getCategory("powerplant"));
        g.setAccessLevel(AccessLevel.ALLOCATE);
        a.addPermission(g);
        Permission u = a.newPermission();
        u.setUser(monty);
        u.setAccessLevel(AccessLevel.READ);
        a.addPermission(u);
        facade.store(a);

        Preferences edit = facade.edit(facade.getSystemPreferences());
        edit.putEntry(AdditiveMigrationState.WORKLIST_KEY, a.getId());
        edit.removeEntry(AdditiveMigrationState.ACK_KEY.getId());
        facade.store(edit);
        return a.getId();
    }

    private String loginAs(String username, String password) throws Exception
    {
        return OAuthTestSupport.loginAs(mockMvc, username, password);
    }

    @Test
    void adminSeesTheEscalationFinding() throws Exception
    {
        String id = seedSoftDenyInWorklist();
        String adminToken = loginAs("homer", "duffs");

        mockMvc.perform(get("/api/admin/permission-migration/findings")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.allocatableId=='" + id + "')]").exists())
                .andExpect(jsonPath("$[?(@.allocatableId=='" + id + "')].escalations[0].principalName").value(org.hamcrest.Matchers.hasItem("monty")))
                .andExpect(jsonPath("$[?(@.allocatableId=='" + id + "')].escalations[0].currentLevel").value(org.hamcrest.Matchers.hasItem("READ")))
                .andExpect(jsonPath("$[?(@.allocatableId=='" + id + "')].escalations[0].additiveLevel").value(org.hamcrest.Matchers.hasItem("ALLOCATE")));
    }

    @Test
    void nonAdminIsForbiddenAndLeaksNothing() throws Exception
    {
        seedSoftDenyInWorklist();
        String montyToken = loginAs("monty", "burns");

        mockMvc.perform(get("/api/admin/permission-migration/findings")
                        .header("Authorization", "Bearer " + montyToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsUnauthorized() throws Exception
    {
        mockMvc.perform(get("/api/admin/permission-migration/findings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resolveAcknowledgesAndDropsOff() throws Exception
    {
        String id = seedSoftDenyInWorklist();
        String adminToken = loginAs("homer", "duffs");

        mockMvc.perform(post("/api/admin/permission-migration/" + id + "/resolve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.allocatableId=='" + id + "')]").doesNotExist());

        mockMvc.perform(get("/api/admin/permission-migration/findings")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.allocatableId=='" + id + "')]").doesNotExist());
    }
}
