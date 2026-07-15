package org.rapla.server.spring.document;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.server.spring.graphql.ArtifactCatalogService;
import org.rapla.server.spring.graphql.RaplaArtifactProperties;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.dbfile.FileOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 097 Phase 1 — DocumentCatalogService over a real FileOperator (no Spring, no mocks).
 * Documents are `kind=DOCUMENT` artifacts (OQ8) whose body is the Mustache template and whose
 * metadata carries `viewName` + visibility. Covers the OQ1-A properties: a document has its own
 * visibility, its own name, and a dangling view reference is surfaced — never silently broken.
 */
class DocumentCatalogServiceTest
{
    @TempDir Path tempDir;

    private FileOperator operator;
    private DocumentCatalogService catalog;

    /** Known views for the seam: "termine" is valid, "kaputt" exists but is invalid. */
    private static final DocumentCatalogService.ViewLookup VIEWS = viewName -> switch (viewName)
    {
        case "termine" -> Optional.of(Boolean.TRUE);
        case "kaputt" -> Optional.of(Boolean.FALSE);
        default -> Optional.empty();
    };

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

        ArtifactCatalogService artifacts = new ArtifactCatalogService(operator, new RaplaArtifactProperties());
        catalog = new DocumentCatalogService(artifacts, new DocumentRenderer(), VIEWS);
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
        for (User u : operator.getUsers()) if (u.isAdmin()) return u;
        throw new IllegalStateException("fixture must include an admin user");
    }

    private User nonAdmin() throws RaplaException
    {
        for (User u : operator.getUsers()) if (!u.isAdmin()) return u;
        throw new IllegalStateException("fixture must include a non-admin user");
    }

    @Test
    void savingIsAdminOnly() throws Exception
    {
        assertThrows(RaplaSecurityException.class,
                () -> catalog.save("leihschein", "termine", "<p>x</p>", false, List.of(), null, nonAdmin()));
        assertThrows(RaplaSecurityException.class, () -> catalog.delete("leihschein", nonAdmin()));
    }

    @Test
    void saveRejectsUnparseableTemplate() throws Exception
    {
        List<String> errors = catalog.save("kaputt", "termine", "<p>{{#open}}</p>", false, List.of(), null, admin());

        assertFalse(errors.isEmpty(), "an unclosed section must not be storable");
        assertTrue(errors.get(0).contains("line"), "the parse error names the line for the editor");
        assertTrue(catalog.find("kaputt").isEmpty(), "a rejected save stores nothing");
    }

    @Test
    void saveRejectsUnknownViewReference() throws Exception
    {
        List<String> errors = catalog.save("doc", "gibtsnicht", "<p>ok</p>", false, List.of(), null, admin());

        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("gibtsnicht"));
        assertTrue(catalog.find("doc").isEmpty());
    }

    @Test
    void roundTripKeepsTemplateAndMetadata() throws Exception
    {
        assertEquals(List.of(), catalog.save("leihschein", "termine", "<h1>{{title}}</h1>",
                false, List.of("g-ausleihe"), "{\"eventId\":null}", admin()));

        DocumentEntry doc = catalog.find("leihschein").orElseThrow();
        assertEquals("leihschein", doc.name());
        assertEquals("termine", doc.viewName());
        assertEquals("<h1>{{title}}</h1>", doc.template());
        assertEquals(List.of("g-ausleihe"), doc.groups());
        assertEquals("{\"eventId\":null}", doc.defaultVariables());
        assertFalse(doc.isPublic());
        assertTrue(doc.valid());
    }

    @Test
    void danglingViewReferenceIsSurfacedNotDeleted() throws Exception
    {
        catalog.save("doc", "termine", "<p>ok</p>", true, List.of(), null, admin());
        // the view is renamed away underneath the document (rename = save-new + delete-old, PRD 098 OQ4)
        DocumentCatalogService withoutView = new DocumentCatalogService(
                new ArtifactCatalogService(operator, new RaplaArtifactProperties()),
                new DocumentRenderer(), viewName -> Optional.empty());

        DocumentEntry doc = withoutView.find("doc").orElseThrow();
        assertFalse(doc.valid(), "a document whose view vanished is invalid…");
        assertFalse(doc.invalidReason().isEmpty(), "…with a reason the catalog can display");
        assertEquals("<p>ok</p>", doc.template(), "…and is never silently deleted");
    }

    @Test
    void invalidReferencedViewMakesTheDocumentInvalid() throws Exception
    {
        catalog.save("doc", "termine", "<p>ok</p>", true, List.of(), null, admin());
        catalog.save("doc2", "kaputt", "<p>ok</p>", true, List.of(), null, admin());

        assertTrue(catalog.find("doc").orElseThrow().valid());
        assertFalse(catalog.find("doc2").orElseThrow().valid(),
                "a document can be no healthier than the view it renders");
    }

    @Test
    void visibilityFollowsIsPublicAndGroups() throws Exception
    {
        User admin = admin();
        User homer = nonAdmin();
        catalog.save("oeffentlich", "termine", "<p>a</p>", true, List.of(), null, admin);
        catalog.save("intern", "termine", "<p>b</p>", false, List.of(), null, admin);

        assertTrue(catalog.findVisible("oeffentlich", homer).isPresent(), "public documents are visible to everyone");
        assertTrue(catalog.findVisible("intern", homer).isEmpty(), "non-public documents hide from non-members");
        assertTrue(catalog.findVisible("intern", admin).isPresent(), "admins see everything");
        assertTrue(catalog.findVisible("oeffentlich", null).isPresent(), "anonymous callers see public documents");
        assertTrue(catalog.findVisible("intern", null).isEmpty());

        // PRD 097 Phase 5 — the BUILTIN documents are always listed; filter to the CUSTOM ones here.
        List<String> visibleToHomer = catalog.list(homer).stream()
                .filter(d -> !d.builtin()).map(DocumentEntry::name).toList();
        assertEquals(List.of("oeffentlich"), visibleToHomer, "listing filters by the same rule");
        assertEquals(2, catalog.list(admin).stream().filter(d -> !d.builtin()).count());
    }

    @Test
    void deleteRemovesAndReportsNotFound() throws Exception
    {
        User admin = admin();
        catalog.save("doc", "termine", "<p>ok</p>", true, List.of(), null, admin);

        assertTrue(catalog.delete("doc", admin));
        assertTrue(catalog.find("doc").isEmpty());
        assertFalse(catalog.delete("doc", admin), "second delete reports not-found");
    }
}
