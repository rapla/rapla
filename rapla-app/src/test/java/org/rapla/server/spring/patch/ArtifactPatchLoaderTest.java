package org.rapla.server.spring.patch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.User;
import org.rapla.entities.storage.StoredArtifact;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.server.spring.document.DocumentCatalogService;
import org.rapla.server.spring.graphql.ArtifactCatalogService;
import org.rapla.server.spring.graphql.ViewCatalogService;
import org.rapla.storage.StorageOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 112 Option A — `data/patch/` (here a temp dir) holds self-describing artefact files; the
 * loader creates what is missing, overwrites when the file's `updated` stamp is newer than the
 * stored artifact's lastChanged, and otherwise leaves the store alone. Runs at boot AND is
 * callable, so a second run must be a no-op.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
class ArtifactPatchLoaderTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;
    static Path patchDir;

    private static final String VIEW = """
            # rapla-view
            # public: true
            # updated: 2026-09-01T00:00
            query PatchView @view(title: "Patch") { serverTime }
            """;
    private static final String DOC = """
            {{! rapla-document
            view: PatchView
            public: true
            updated: 2026-09-01T00:00
            }}
            <p id="v1">{{serverTime}}</p>
            """;

    @BeforeAll
    static void fixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ArtifactPatchLoaderTest.class.getResourceAsStream("/testdefault.xml"))
        {
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
        patchDir = tempDir.resolve("patch");
        Files.createDirectories(patchDir);
        Files.writeString(patchDir.resolve("PatchView.graphql"), VIEW);
        Files.writeString(patchDir.resolve("PatchDoc.mustache"), DOC);
        Files.writeString(patchDir.resolve("no-head.mustache"), "<p>ignored</p>");
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
        registry.add("rapla.patch-dir", () -> patchDir.toAbsolutePath().toString());
    }

    @Autowired ArtifactPatchLoader loader;
    @Autowired ArtifactCatalogService artifacts;
    @Autowired ViewCatalogService views;
    @Autowired DocumentCatalogService documents;
    @Autowired StorageOperator operator;

    @Test
    void bootCreatedBothArtefactsAsSystemAndASecondRunIsANoOp() throws Exception
    {
        StoredArtifact view = artifacts.find(StoredArtifact.KIND_VIEW, "PatchView").orElseThrow();
        StoredArtifact doc = artifacts.find(StoredArtifact.KIND_DOCUMENT, "PatchDoc").orElseThrow();
        assertTrue(doc.getBody().contains("id=\"v1\""));
        assertTrue(doc.getBody().contains("rapla-document"), "the head stays in the stored template");
        assertEquals(null, doc.getLastChangedBy(), "system author");
        assertTrue(views.findView("PatchView").orElseThrow().isPublic());
        assertTrue(artifacts.find(StoredArtifact.KIND_DOCUMENT, "no-head").isEmpty());

        ArtifactPatchLoader.Report second = loader.run();
        assertEquals(List.of(), second.created(), second.toString());
        assertEquals(List.of(), second.updated(), second.toString());
        assertEquals(List.of("PatchView", "PatchDoc"), second.skipped(), second.toString());
        assertEquals(List.of("no-head.mustache"), second.ignored(), second.toString());
        assertEquals(view.getLastChanged(), artifacts.find(StoredArtifact.KIND_VIEW, "PatchView").orElseThrow().getLastChanged());
    }

    @Test
    void aNewerStampOverwritesAnOlderOneDoesNot() throws Exception
    {
        User admin = operator.getUser("homer");
        // the admin edits the document in the editor → stored is now younger than the file
        assertEquals(List.of(), documents.save("PatchDoc", "PatchView", "<p id=\"admin\">{{serverTime}}</p>", true, List.of(), null, admin));
        ArtifactPatchLoader.Report olderFile = loader.run();
        assertEquals(List.of(), olderFile.updated(), olderFile.toString());
        assertTrue(artifacts.find(StoredArtifact.KIND_DOCUMENT, "PatchDoc").orElseThrow().getBody().contains("id=\"admin\""));

        // a re-delivery bumps the stamp beyond any edit → the update wins
        Files.writeString(patchDir.resolve("PatchDoc.mustache"), DOC.replace("updated: 2026-09-01T00:00", "updated: 2999-01-01T00:00").replace("v1", "v2"));
        ArtifactPatchLoader.Report newerFile = loader.run();
        assertEquals(List.of("PatchDoc"), newerFile.updated(), newerFile.toString());
        assertTrue(artifacts.find(StoredArtifact.KIND_DOCUMENT, "PatchDoc").orElseThrow().getBody().contains("id=\"v2\""));
    }

    @Test
    void anInvalidFileIsReportedAndSkipped() throws Exception
    {
        Files.writeString(patchDir.resolve("Broken.graphql"), "# rapla-view\n# updated: 2026-09-01T00:00\nquery Broken { noSuchField }\n");
        ArtifactPatchLoader.Report report = loader.run();
        assertEquals(1, report.failed().size(), report.toString());
        assertTrue(report.failed().get(0).contains("Broken"), report.toString());
        assertTrue(artifacts.find(StoredArtifact.KIND_VIEW, "Broken").isEmpty());
        Files.delete(patchDir.resolve("Broken.graphql"));
    }
}
