package org.rapla.storage.dbsql;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.RaplaResources;
import org.rapla.components.i18n.internal.AbstractBundleManager;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.entities.User;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.domain.permission.impl.RaplaDefaultPermissionImpl;
import org.rapla.entities.dynamictype.internal.StandardFunctions;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.StoredArtifact;
import org.rapla.entities.storage.internal.StoredArtifactImpl;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.storage.dbfile.FileOperator;
import org.rapla.storage.impl.server.ImportExportManagerImpl;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 098 Phase 1 — StoredArtifact round-trip through the ARTIFACT table on a real
 * (HSQLDB) DBOperator: table auto-creation, insert, read-through, upsert, delete.
 * Tagged {@code db} for the default fast-lane skip.
 */
@Tag("db")
public class StoredArtifactDbRoundTripTest
{
    private static final String DEFAULT_FIXTURE = "/testdefault.xml";

    @TempDir
    Path tempDir;

    private RaplaResources i18n;
    private CommandScheduler scheduler;
    private FileOperator fileOperator;
    private DBOperator operator;

    @BeforeEach
    void setUp() throws Exception
    {
        Path xmlFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = getClass().getResourceAsStream(DEFAULT_FIXTURE))
        {
            if (in == null) throw new IllegalStateException(DEFAULT_FIXTURE + " not on classpath");
            Files.copy(in, xmlFile, StandardCopyOption.REPLACE_EXISTING);
        }

        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:" + tempDir.resolve("rapla-db-" + UUID.randomUUID()).toAbsolutePath()
                + ";shutdown=true");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        AbstractBundleManager bundleManager = new ServerBundleManager();
        i18n = new RaplaResources(bundleManager);
        RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);
        scheduler = new DefaultScheduler();

        Set<PermissionExtension> permissionExtensions = new LinkedHashSet<>();
        permissionExtensions.add(new RaplaDefaultPermissionImpl());

        Map<String, FunctionFactory> functionFactoryMap = new LinkedHashMap<>();
        functionFactoryMap.put(StandardFunctions.NAMESPACE, new StandardFunctions(raplaLocale));

        fileOperator = new FileOperator(i18n, raplaLocale, scheduler,
                functionFactoryMap, xmlFile.toAbsolutePath().toString(), permissionExtensions);

        operator = new DBOperator(i18n, raplaLocale, scheduler, functionFactoryMap,
                () -> null, dataSource, permissionExtensions);

        ImportExportManagerImpl manager = new ImportExportManagerImpl(fileOperator, operator);
        operator.importExportManager = () -> manager;

        operator.connect();
    }

    @AfterEach
    void tearDown()
    {
        if (operator != null && operator.isConnected())
        {
            try { operator.disconnect(); } catch (Exception ignored) {}
        }
        if (fileOperator != null && fileOperator.isConnected())
        {
            try { fileOperator.disconnect(); } catch (Exception ignored) {}
        }
    }

    private StoredArtifactImpl newArtifact(String kind, String name, String body)
    {
        StoredArtifactImpl artifact = new StoredArtifactImpl(kind, name);
        artifact.setBody(body);
        artifact.setMetadata("{\"isPublic\":false,\"groups\":[\"g1\"]}");
        artifact.setCreateDate(LocalDateTime.of(2026, 1, 1, 8, 0));
        artifact.setLastChanged(LocalDateTime.of(2026, 1, 1, 8, 0));
        return artifact;
    }

    private User getAdmin() throws Exception
    {
        for (User u : operator.getUsers())
        {
            if (u.isAdmin()) return u;
        }
        throw new IllegalStateException("fixture must include an admin user");
    }

    private Optional<StoredArtifact> find(String id) throws Exception
    {
        return operator.getStoredArtifacts().stream().filter(a -> a.getId().equals(id)).findFirst();
    }

    @Test
    void storeReconnectAndReadBack() throws Exception
    {
        String body = "<div class=\"leihschein\">{{name}} &amp; äöü</div>";
        operator.storeAndRemove(List.of(newArtifact(StoredArtifact.KIND_TEMPLATE, "leihschein", body)),
                Collections.emptyList(), getAdmin());

        Optional<StoredArtifact> direct = find("TEMPLATE:leihschein");
        assertTrue(direct.isPresent(), "read-through sees the artifact immediately");

        operator.disconnect();
        operator.connect();

        Optional<StoredArtifact> reloaded = find("TEMPLATE:leihschein");
        assertTrue(reloaded.isPresent(), "artifact survives DB reconnect");
        assertEquals(body, reloaded.get().getBody());
        assertEquals(StoredArtifact.KIND_TEMPLATE, reloaded.get().getKind());
        assertEquals("leihschein", reloaded.get().getName());
        assertEquals("{\"isPublic\":false,\"groups\":[\"g1\"]}", reloaded.get().getMetadata());
    }

    @Test
    void upsertByNaturalKeyOverwrites() throws Exception
    {
        User admin = getAdmin();
        operator.storeAndRemove(List.of(newArtifact(StoredArtifact.KIND_VIEW, "v", "q1")), Collections.emptyList(), admin);
        operator.storeAndRemove(List.of(newArtifact(StoredArtifact.KIND_VIEW, "v", "q2")), Collections.emptyList(), admin);

        List<StoredArtifact> matching = operator.getStoredArtifacts().stream()
                .filter(a -> a.getId().equals("VIEW:v")).toList();
        assertEquals(1, matching.size(), "same natural key -> single row");
        assertEquals("q2", matching.get(0).getBody());
    }

    @Test
    void deleteRemovesRow() throws Exception
    {
        User admin = getAdmin();
        operator.storeAndRemove(List.of(newArtifact(StoredArtifact.KIND_CSS, "print", "@page{}")), Collections.emptyList(), admin);
        assertTrue(find("CSS:print").isPresent());

        operator.storeAndRemove(Collections.emptyList(),
                List.of(new ReferenceInfo<>("CSS:print", StoredArtifact.class)), admin);

        assertFalse(find("CSS:print").isPresent(), "row deleted from ARTIFACT table");
    }
}
