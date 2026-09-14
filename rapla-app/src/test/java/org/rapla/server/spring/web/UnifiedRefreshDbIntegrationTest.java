package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.RaplaFacade;
import org.rapla.server.internal.ServerStorageSelector;
import org.rapla.storage.dbsql.DBOperator;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.server.spring.RefreshSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for the 2026-05-20 live-server bug: after a fresh
 * {@code grant_type=password} login on a DB-backed deployment, the issued
 * refresh token gets rejected at {@code /oauth2/token grant_type=refresh_token}
 * with {@code invalid_grant} — because
 * {@link org.rapla.server.spring.RefreshSessionService#persistSession(org.rapla.entities.User, String)}
 * 's write to user prefs isn't visible to the validate call that follows.
 *
 * <p>The file-backed twin {@code UnifiedRefreshIntegrationTest} passes — the
 * persistSession round-trip works fine over FileOperator. This test uses the
 * SAME flow against an HSQLDB-backed DBOperator so the failure mode is reproduced
 * deterministically. If this test passes too, the bug is dhbw-deployment
 * specific (multi-pod, archiver, or similar) and a narrower repro is needed.
 *
 * <p>Mirrors {@link org.rapla.server.spring.DbDatasourceBootIntegrationTest}'s
 * datasource wiring (embedded HSQLDB + XML seed import on first connect).
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
@Tag("e2e")
@Tag("db")
class UnifiedRefreshDbIntegrationTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = UnifiedRefreshDbIntegrationTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in);
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
        // Unique in-memory schema name per test class so parallel runs don't collide.
        registry.add("rapla.db-datasources.rapladb.url", () -> "jdbc:hsqldb:mem:rapla-refresh-db-test");
        registry.add("rapla.db-datasources.rapladb.username", () -> "SA");
        registry.add("rapla.db-datasources.rapladb.password", () -> "");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RaplaFacade raplaFacade;

    @Autowired
    RefreshSessionService refreshSessionService;

    @Autowired
    ServerStorageSelector serverStorageSelector;

    /** Reads the raw STRING_VALUE column for a given (user_id, role) — bypasses
     *  the cache entirely. Tells us whether the in-flight INSERT made it to the row store. */
    private String readRawPrefValueFromDb(String userId, String role) throws Exception
    {
        DBOperator op = (DBOperator) serverStorageSelector.get();
        try (java.sql.Connection c = op.createConnection();
             java.sql.PreparedStatement stmt = c.prepareStatement(
                     "SELECT STRING_VALUE FROM PREFERENCE WHERE USER_ID = ? AND ROLE = ?"))
        {
            stmt.setString(1, userId);
            stmt.setString(2, role);
            try (java.sql.ResultSet rs = stmt.executeQuery())
            {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /**
     * Pinpoints the persistence layer: bypass the HTTP / OAuth machinery and
     * call {@link RefreshSessionService#persistSession(User, String)} directly,
     * then re-resolve the user's preferences from the facade and assert the
     * SESSION entry is visible. If THIS fails on the DB store, the bug is in
     * the operator's dispatch (preferences-patch write doesn't refresh the
     * in-memory cache). If this passes but the OAuth round-trip test below
     * still fails, the bug is in the OAuth wiring instead.
     */
    @Test
    void persistSessionWriteIsVisibleViaTheFacadeOnDbBackedStore() throws Exception
    {
        User admin = findAdmin(raplaFacade);
        String token = "synthetic-test-token-" + System.nanoTime();

        refreshSessionService.persistSession(admin, token);

        // (a) Did the row hit the actual database table?
        String onDisk = readRawPrefValueFromDb(admin.getId(), RefreshSessionService.SESSION.getId());
        System.err.println("RAW DB STRING_VALUE for SESSION: " + (onDisk == null ? "<NULL>" : onDisk.substring(0, Math.min(120, onDisk.length()))));

        // (b) Re-resolve prefs from the facade. The cache should reflect the just-stored entry.
        Preferences prefs = raplaFacade.getPreferences(admin);
        String fromCache = prefs.getEntryAsString(RefreshSessionService.SESSION, null);
        System.err.println("FACADE-READ SESSION:           " + (fromCache == null ? "<NULL>" : fromCache.substring(0, Math.min(120, fromCache.length()))));

        assertNotNull(onDisk, "INSERT into PREFERENCE table must succeed");
        assertNotNull(fromCache, "SESSION pref must be readable through the facade right after persistSession()");
    }

    @Test
    void refreshTokenIssuedByPasswordGrantIsRedeemableOnDbBackedStore() throws Exception
    {
        // 1. Login via /oauth2/token grant_type=password — this goes through
        //    PasswordGrantAuthenticationProvider, which calls
        //    RefreshSessionService.issueAndPersist(user). On a fresh user
        //    that hits the mint+persistSession path.
        OAuthTestSupport.TokenPair pair = OAuthTestSupport.loginAsWithRefresh(mockMvc, "homer", "duffs");

        // 2. Immediately redeem the refresh token. Goes through
        //    RaplaRefreshTokenAuthenticationProvider → RefreshSessionService.validate(),
        //    which re-reads the SESSION pref and compares to the presented token.
        //    Must succeed: presented == just-persisted.
        mockMvc.perform(post("/oauth2/token")
                        .contentType("application/x-www-form-urlencoded")
                        .content("grant_type=refresh_token"
                                + "&refresh_token=" + URLEncoder.encode(pair.refreshToken(), StandardCharsets.UTF_8)
                                + "&client_id=rapla-client"))
                .andExpect(status().isOk());
    }

    private static User findAdmin(RaplaFacade facade) throws Exception
    {
        for (User u : facade.getUsers())
        {
            if (u.isAdmin()) return u;
        }
        throw new IllegalStateException("no admin user in bootstrap fixture");
    }
}
