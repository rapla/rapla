package org.rapla.server.spring.document;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.rapla.entities.User;
import org.rapla.entities.internal.UserImpl;
import org.rapla.entities.storage.StoredArtifact;
import org.rapla.framework.RaplaException;
import org.rapla.server.spring.graphql.ArtifactCatalogService;
import org.rapla.server.spring.graphql.ViewCatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * PRD 097 Phase 1 — CRUD over stored documents, a thin consumer of the PRD 098 artifact store
 * (kind={@link StoredArtifact#KIND_DOCUMENT}). The artifact body is the Mustache template; its
 * metadata JSON carries {@code viewName}, visibility and default variables.
 *
 * <p>Mirrors {@link ViewCatalogService}: admin-only writes (PRD 098 D6 — a template renders HTML
 * into other users' browsers), validation at save AND at read (a view can be renamed away after
 * the document was saved), visibility interpreted at the application level (D4).
 */
@Component
public class DocumentCatalogService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentCatalogService.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** Seam onto the view catalog: empty = no such view, otherwise the view's validity. */
    @FunctionalInterface
    public interface ViewLookup
    {
        Optional<Boolean> viewValid(String viewName);

        /** Whether the caller may see the view at all. Defaults to "yes" for tests that don't care. */
        default boolean viewVisible(String viewName, User caller)
        {
            return true;
        }
    }

    private final ArtifactCatalogService artifacts;
    private final DocumentRenderer renderer;
    private final ViewLookup views;

    @Autowired
    public DocumentCatalogService(ArtifactCatalogService artifacts, DocumentRenderer renderer, ViewCatalogService viewCatalog)
    {
        this(artifacts, renderer, new ViewLookup()
        {
            @Override
            public Optional<Boolean> viewValid(String viewName)
            {
                return viewCatalog.findView(viewName).map(v -> v.valid());
            }

            @Override
            public boolean viewVisible(String viewName, User caller)
            {
                return viewCatalog.findViewForCaller(viewName, caller).isPresent();
            }
        });
    }

    DocumentCatalogService(ArtifactCatalogService artifacts, DocumentRenderer renderer, ViewLookup views)
    {
        this.artifacts = artifacts;
        this.renderer = renderer;
        this.views = views;
    }

    /** All documents visible to the caller, validated against the current view catalog. */
    public List<DocumentEntry> list(User caller)
    {
        List<DocumentEntry> result = new ArrayList<>();
        for (StoredArtifact listed : artifacts.list(StoredArtifact.KIND_DOCUMENT))
        {
            find(listed.getName()).filter(doc -> isVisible(doc, caller)).ifPresent(result::add);
        }
        return result;
    }

    /** Find by name, ignoring visibility (the render path gates separately). */
    public Optional<DocumentEntry> find(String name)
    {
        return artifacts.find(StoredArtifact.KIND_DOCUMENT, name).map(this::toEntry);
    }

    /**
     * Find by name, but only if the caller may see it. §12: callers must not be able to tell
     * "does not exist" from "exists but is not yours" — both yield {@code empty}.
     */
    public Optional<DocumentEntry> findVisible(String name, User caller)
    {
        return find(name).filter(doc -> isVisible(doc, caller));
    }

    /**
     * Create or overwrite a document. Admin-only. Returns validation errors (empty on success);
     * a document that would not render is never stored.
     */
    public List<String> save(String name, String viewName, String template, boolean isPublic,
            List<String> groups, String defaultVariables, User caller) throws RaplaException
    {
        // gate first: a non-admin must not be able to probe template/view validity through the
        // error channel (§12 — the response must not reveal what the caller may not see)
        artifacts.checkWrite(caller);
        List<String> errors = validateForSave(viewName, template);
        if (!errors.isEmpty()) return errors;
        DocumentMeta meta = new DocumentMeta(viewName, isPublic, groups == null ? List.of() : groups, defaultVariables);
        artifacts.save(StoredArtifact.KIND_DOCUMENT, name, template, MAPPER.writeValueAsString(meta), caller);
        return List.of();
    }

    /**
     * The authoring gate (PRD 098 D6). Reading a document's template is an authoring action, not a
     * reading one — a template is code that runs in other users' browsers, so the editor surfaces
     * ({@code /source}, save, delete) sit behind the same check as a write.
     */
    public void requireAuthor(User caller) throws RaplaException
    {
        artifacts.checkWrite(caller);
    }

    /** Delete a document. Admin-only. False when it does not exist. */
    public boolean delete(String name, User caller) throws RaplaException
    {
        return artifacts.delete(StoredArtifact.KIND_DOCUMENT, name, caller);
    }

    /**
     * The save gate: reject what can never be repaired by fixing something else — a template the
     * real engine cannot parse, and a view name that does not exist (a typo). A view that exists
     * but is currently *invalid* is NOT a save blocker: the admin may be fixing it next, and the
     * document surfaces as invalid at read time until then.
     */
    public List<String> validateForSave(String viewName, String template)
    {
        List<String> errors = new ArrayList<>();
        if (viewName == null || viewName.isBlank()) errors.add("A document must reference a view");
        else if (views.viewValid(viewName).isEmpty()) errors.add("Referenced view '" + viewName + "' does not exist");
        renderer.validate(template).ifPresent(
                e -> errors.add("Template parse error at line " + e.line() + ": " + e.message()));
        return errors;
    }

    /** Read-time validation — everything from the save gate, plus the health of the referenced view. */
    public List<String> validateForRead(String viewName, String template)
    {
        List<String> errors = validateForSave(viewName, template);
        if (viewName != null && !viewName.isBlank() && views.viewValid(viewName).orElse(Boolean.TRUE) == Boolean.FALSE)
        {
            errors.add("Referenced view '" + viewName + "' is itself invalid");
        }
        return errors;
    }

    private DocumentEntry toEntry(StoredArtifact artifact)
    {
        DocumentMeta meta;
        try
        {
            String metadata = artifact.getMetadata();
            meta = metadata == null || metadata.isBlank()
                    ? new DocumentMeta(null, false, List.of(), null)
                    : MAPPER.readValue(metadata, DocumentMeta.class);
        }
        catch (Exception e)
        {
            LOGGER.warn("Ignoring unparseable metadata of document artifact {}", artifact.getId(), e);
            meta = new DocumentMeta(null, false, List.of(), null);
        }
        List<String> errors = validateForRead(meta.viewName(), artifact.getBody());
        return new DocumentEntry(artifact.getName(), meta.viewName(), artifact.getBody(),
                meta.isPublic(), meta.groups() == null ? List.of() : meta.groups(), meta.defaultVariables(),
                errors.isEmpty(), errors);
    }

    /**
     * §12 — a document is visible only when its <b>view</b> is too. Without this, a listing (or a
     * 404-vs-200 probe) would surface {@code viewName} of a view the caller may not see: the
     * document's own visibility flag does not cover the artifact it points at.
     */
    private boolean isVisible(DocumentEntry doc, User caller)
    {
        if (!views.viewVisible(doc.viewName(), caller)) return false;
        if (caller == null) return doc.isPublic();
        if (caller.isAdmin()) return true;
        if (doc.isPublic()) return true;
        if (!doc.groups().isEmpty())
        {
            Collection<String> userGroups = UserImpl.getGroupsIncludingParents(caller);
            for (String groupId : doc.groups())
            {
                if (userGroups.contains(groupId)) return true;
            }
        }
        return false;
    }

    /** Visibility + data-source metadata persisted as the artifact's metadata JSON. */
    record DocumentMeta(String viewName, boolean isPublic, List<String> groups, String defaultVariables) { }
}
