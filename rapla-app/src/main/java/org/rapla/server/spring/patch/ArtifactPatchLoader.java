package org.rapla.server.spring.patch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.rapla.entities.storage.StoredArtifact;
import org.rapla.server.spring.RaplaServerProperties;
import org.rapla.server.spring.document.DocumentCatalogService;
import org.rapla.server.spring.graphql.ArtifactCatalogService;
import org.rapla.server.spring.graphql.ViewCatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * PRD 112 Option A — applies the deployment patch directory ({@code rapla.patch-dir}) to the
 * artifact store at every start: {@code *.graphql} with a {@code # rapla-view} head become
 * stored views, {@code *.mustache} with a {@code {{! rapla-document …}}} head become stored
 * documents (name = file name without extension; a view's name is its operation name, which the
 * catalog enforces). Create when missing, overwrite when the head's {@code updated} is newer than
 * the stored artifact's lastChanged, else skip. Views first (a document validates against its
 * view). System author, full catalog validation. Idempotent by construction (upsert by natural
 * key, same content) — a concurrent pod start yields the same store; a lost race surfaces as a
 * logged failure and is retried at the next start.
 */
@Component
@ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.plugin.patch", matchIfMissing = true)
public class ArtifactPatchLoader
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ArtifactPatchLoader.class);

    public record Report(List<String> created, List<String> updated, List<String> skipped,
            List<String> ignored, List<String> failed) { }

    private final Path dir;
    private final ArtifactCatalogService artifacts;
    private final ViewCatalogService views;
    private final DocumentCatalogService documents;

    public ArtifactPatchLoader(RaplaServerProperties properties, ArtifactCatalogService artifacts,
            ViewCatalogService views, DocumentCatalogService documents)
    {
        this.dir = Path.of(properties.getPatchDir());
        this.artifacts = artifacts;
        this.views = views;
        this.documents = documents;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady()
    {
        Report report = run();
        if (!report.created().isEmpty() || !report.updated().isEmpty() || !report.failed().isEmpty())
        {
            LOGGER.info("PRD 112 patch dir {}: created {}, updated {}, skipped {}, failed {}", dir,
                    report.created(), report.updated(), report.skipped(), report.failed());
        }
    }

    public Report run()
    {
        Report report = new Report(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>());
        if (!Files.isDirectory(dir)) return report;
        List<Path> files;
        try (Stream<Path> listing = Files.list(dir))
        {
            files = listing.filter(Files::isRegularFile).sorted().toList();
        }
        catch (IOException e)
        {
            LOGGER.error("PRD 112 patch dir {} not readable: {}", dir, e.toString());
            return report;
        }
        for (Path file : files) if (file.toString().endsWith(".graphql")) apply(file, report);
        for (Path file : files) if (file.toString().endsWith(".mustache")) apply(file, report);
        return report;
    }

    private void apply(Path file, Report report)
    {
        String fileName = file.getFileName().toString();
        try
        {
            String text = Files.readString(file);
            Optional<FrontMatter> head = FrontMatter.parse(text);
            boolean isView = fileName.endsWith(".graphql");
            String expectedKind = isView ? FrontMatter.VIEW : FrontMatter.DOCUMENT;
            if (head.isEmpty() || !head.get().kind().equals(expectedKind))
            {
                LOGGER.warn("PRD 112 patch file {} has no '{}' head — ignored", file, expectedKind);
                report.ignored().add(fileName);
                return;
            }
            FrontMatter fm = head.get();
            String name = isView ? operationName(text) : fileName.substring(0, fileName.length() - ".mustache".length());
            String kind = isView ? StoredArtifact.KIND_VIEW : StoredArtifact.KIND_DOCUMENT;
            Optional<java.time.LocalDateTime> stored = artifacts.find(kind, name).map(StoredArtifact::getLastChanged);
            FrontMatter.Action action = FrontMatter.decide(fm.updated(), stored);
            if (action == FrontMatter.Action.SKIP)
            {
                report.skipped().add(name);
                return;
            }
            List<String> errors = isView
                    ? views.saveViewAsSystem(name, text, fm.isPublic(), fm.groups(), fm.value("defaultVariables"))
                    : documents.saveAsSystem(name, fm.value("view"), text, fm.isPublic(), fm.groups(),
                            fm.value("defaultVariables"), fm.value("window"));
            if (!errors.isEmpty())
            {
                LOGGER.error("PRD 112 patch file {} rejected: {}", file, errors);
                report.failed().add(fileName + ": " + errors);
                return;
            }
            (action == FrontMatter.Action.CREATE ? report.created() : report.updated()).add(name);
        }
        catch (Exception e)
        {
            LOGGER.error("PRD 112 patch file {} failed: {}", file, e.toString());
            report.failed().add(fileName + ": " + e);
        }
    }

    /** The first {@code query <Name>} — the catalog requires name == operation name anyway. */
    private static String operationName(String queryText)
    {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\bquery\\s+([A-Za-z_][A-Za-z0-9_]*)").matcher(queryText);
        return m.find() ? m.group(1) : "";
    }
}
