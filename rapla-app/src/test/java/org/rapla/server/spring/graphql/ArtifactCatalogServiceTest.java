package org.rapla.server.spring.graphql;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.rapla.entities.storage.StoredArtifact;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.dbfile.FileOperator;
import org.springframework.util.unit.DataSize;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 098 Phase 2 — ArtifactCatalogService over a real FileOperator (no Spring):
 * admin-only write seam (D6), size cap (OQ2), read-your-own-writes despite the TTL
 * snapshot, and per-artifact save granularity.
 */
public class ArtifactCatalogServiceTest
{
    @TempDir Path tempDir;

    private FileOperator operator;
    private ArtifactCatalogService catalog;
    private RaplaArtifactProperties properties;

    @BeforeEach
    void setUp() throws Exception
    {
        Path dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = getClass().getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml fixture missing");
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
        AbstractBundleManager bundleManager = new ServerBundleManager();
        RaplaResources i18n = new RaplaResources(bundleManager);
        RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);
        CommandScheduler scheduler = new DefaultScheduler();
        Set<PermissionExtension> permissionExtensions = new LinkedHashSet<>();
        permissionExtensions.add(new RaplaDefaultPermissionImpl());
        Map<String, FunctionFactory> functionFactoryMap = new LinkedHashMap<>();
        functionFactoryMap.put(StandardFunctions.NAMESPACE, new StandardFunctions(raplaLocale));
        operator = new FileOperator(i18n, raplaLocale, scheduler, functionFactoryMap,
                dataFile.toAbsolutePath().toString(), permissionExtensions);
        operator.connect();

        properties = new RaplaArtifactProperties();
        catalog = new ArtifactCatalogService(operator, properties);
    }

    @AfterEach
    void tearDown()
    {
        if (operator != null && operator.isConnected())
        {
            try { operator.disconnect(); } catch (Exception ignored) {}
        }
    }

    private User admin() throws RaplaException
    {
        for (User u : operator.getUsers())
        {
            if (u.isAdmin()) return u;
        }
        throw new IllegalStateException("fixture must include an admin user");
    }

    private User nonAdmin() throws RaplaException
    {
        for (User u : operator.getUsers())
        {
            if (!u.isAdmin()) return u;
        }
        throw new IllegalStateException("fixture must include a non-admin user");
    }

    @Test
    void saveIsAdminOnly() throws Exception
    {
        User homer = nonAdmin();
        assertThrows(RaplaSecurityException.class,
                () -> catalog.save(StoredArtifact.KIND_VIEW, "v", "query", null, homer));
        assertThrows(RaplaSecurityException.class,
                () -> catalog.save(StoredArtifact.KIND_VIEW, "v", "query", null, null));
        assertThrows(RaplaSecurityException.class,
                () -> catalog.delete(StoredArtifact.KIND_VIEW, "v", homer));
    }

    @Test
    void bodySizeCapRejectsOversizedContent() throws Exception
    {
        properties.setMaxBodySize(DataSize.ofKilobytes(1));
        String oversized = "x".repeat(2048);
        RaplaException ex = assertThrows(RaplaException.class,
                () -> catalog.save(StoredArtifact.KIND_IMAGE, "logo", oversized, null, admin()));
        assertTrue(ex.getMessage().contains("max-body-size"));
    }

    @Test
    void readYourOwnWritesDespiteTtlSnapshot() throws Exception
    {
        // prime the snapshot before the write
        assertTrue(catalog.list(StoredArtifact.KIND_TEMPLATE).isEmpty());

        catalog.save(StoredArtifact.KIND_TEMPLATE, "doc", "<div/>", "{\"isPublic\":true}", admin());

        Optional<StoredArtifact> found = catalog.find(StoredArtifact.KIND_TEMPLATE, "doc");
        assertTrue(found.isPresent(), "invalidate-on-write must make the save immediately visible");
        assertEquals("<div/>", found.get().getBody());
        assertNotNull(found.get().getCreateDate());
    }

    @Test
    void savesOfDifferentArtifactsDoNotConflict() throws Exception
    {
        User admin = admin();
        catalog.save(StoredArtifact.KIND_VIEW, "a", "query a", null, admin);
        catalog.save(StoredArtifact.KIND_VIEW, "b", "query b", null, admin);

        assertEquals(2, catalog.list(StoredArtifact.KIND_VIEW).size(),
                "two different artifacts persist independently — per-row granularity");
        assertEquals("query a", catalog.find(StoredArtifact.KIND_VIEW, "a").orElseThrow().getBody());
        assertEquals("query b", catalog.find(StoredArtifact.KIND_VIEW, "b").orElseThrow().getBody());
    }

    @Test
    void listReturnsMetadataWithoutBodies() throws Exception
    {
        catalog.save(StoredArtifact.KIND_TEMPLATE, "letter", "<html/>", "{\"isPublic\":true}", admin());

        StoredArtifact entry = catalog.list(StoredArtifact.KIND_TEMPLATE).get(0);
        assertEquals("letter", entry.getName());
        assertEquals("{\"isPublic\":true}", entry.getMetadata());
        assertNull(entry.getBody(), "list is metadata-only — bodies load through find()");

        assertEquals("<html/>", catalog.find(StoredArtifact.KIND_TEMPLATE, "letter").orElseThrow().getBody(),
                "stripping the listed entry must not damage the stored artifact");
    }

    @Test
    void largeBodyIsServedReadThroughAndNeverListed() throws Exception
    {
        String large = "y".repeat(2 * 1024 * 1024);   // > 1 MiB cache limit, < 10 MB cap
        catalog.save(StoredArtifact.KIND_IMAGE, "big", large, null, admin());

        assertEquals(large, catalog.find(StoredArtifact.KIND_IMAGE, "big").orElseThrow().getBody());
        assertEquals(large, catalog.find(StoredArtifact.KIND_IMAGE, "big").orElseThrow().getBody(),
                "uncached large body stays retrievable on repeated reads");
        assertNull(catalog.list(StoredArtifact.KIND_IMAGE).get(0).getBody());
    }

    @Test
    void upsertKeepsCreateDateAndDeleteRemoves() throws Exception
    {
        User admin = admin();
        catalog.save(StoredArtifact.KIND_CSS, "print", "v1", null, admin);
        java.time.LocalDateTime created = catalog.find(StoredArtifact.KIND_CSS, "print").orElseThrow().getCreateDate();

        catalog.save(StoredArtifact.KIND_CSS, "print", "v2", null, admin);
        StoredArtifact updated = catalog.find(StoredArtifact.KIND_CSS, "print").orElseThrow();
        assertEquals("v2", updated.getBody());
        assertEquals(created, updated.getCreateDate(), "upsert preserves the original create date");

        assertTrue(catalog.delete(StoredArtifact.KIND_CSS, "print", admin));
        assertTrue(catalog.find(StoredArtifact.KIND_CSS, "print").isEmpty());
        assertFalse(catalog.delete(StoredArtifact.KIND_CSS, "print", admin), "second delete reports not-found");
    }
}
