package org.rapla.server.spring.document;

import java.util.List;
import java.util.Map;
import org.rapla.framework.RaplaException;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

/**
 * PRD 097 OQ4 — the REST contract for stored documents. Two audiences on one prefix:
 *
 * <ul>
 *   <li><b>Readers</b> navigate to {@code GET /api/documents/{name}} and get a complete, printable
 *       HTML page. Query parameters become GraphQL variables of the referenced view, so a Leihschein
 *       is just {@code /api/documents/leihschein?eventId=…}.</li>
 *   <li><b>Authors</b> (admins) use the JSON CRUD below from the template editor.</li>
 * </ul>
 *
 * <p>Routing metadata lives here, not on the controller (AGENTS.md §15 / PRD 049).
 * §12: a document the caller may not see is reported exactly like one that does not exist.
 */
@HttpExchange("/api/documents")
public interface DocumentApi
{
    /**
     * Render a document to a standalone HTML page. Query params are the view's variables, expanded
     * by {@code RequestVariables}: a dot nests ({@code ?filter.allocatableIdsIn=r1}), a repeated key
     * is a list. {@code MultiValueMap} — not {@code Map} — so a repeat is not silently dropped.
     */
    @GetExchange(value = "/{name}", accept = "text/html")
    ResponseEntity<String> render(@PathVariable("name") String name,
            @RequestParam MultiValueMap<String, String> variables);

    /**
     * The same document as a downloadable CSV: same view, same parameters, same rows the page
     * shows (grouping and {@code minGroupSize} included). The columns are the view's own
     * {@code @column} metadata — a template is HTML, not a table, so it cannot define them.
     */
    @GetExchange(value = "/{name}/csv", accept = "text/csv")
    ResponseEntity<byte[]> csv(@PathVariable("name") String name,
            @RequestParam MultiValueMap<String, String> variables);

    /** The documents the caller may see — metadata only, no template bodies. */
    @GetExchange
    List<DocumentSummary> list();

    /** The full document including its template, for the editor. Admin-only (it is a write surface). */
    @GetExchange("/{name}/source")
    DocumentSource source(@PathVariable("name") String name) throws RaplaException;

    /** Create or overwrite. Admin-only; a document that cannot render is never stored. */
    @PutExchange("/{name}")
    ResponseEntity<SaveResult> save(@PathVariable("name") String name,
            @RequestBody SaveDocumentRequest body) throws RaplaException;

    /** Delete. Admin-only; 404 when unknown. */
    @DeleteExchange("/{name}")
    ResponseEntity<Void> delete(@PathVariable("name") String name) throws RaplaException;

    /**
     * Render an unsaved template (PRD 097 Phase 4). Admin-only. Rendered with the real engine
     * against the real view, executed in the author's own read-scope — a client-side mustache.js
     * preview could differ from what readers will get, which is the one thing a preview must not do.
     */
    @PostExchange("/preview")
    PreviewResult preview(@RequestBody PreviewRequest body) throws RaplaException;

    /**
     * The data tree a template may address, derived from the referenced view's selection set.
     * Feeds the fields pane, completion and unknown-field warnings. Admin-only.
     */
    @PostExchange("/result-shape")
    ResultShapeService.ShapeNode resultShape(@RequestBody ShapeRequest body) throws RaplaException;

    /** {@code variables} are the same request parameters the rendered document takes. */
    record PreviewRequest(String viewName, String template, String defaultVariables, String window,
            Map<String, List<String>> variables) { }

    /** Exactly one of {@code html} / {@code errorMessage} is set. {@code errorLine} drives the editor
     *  marker; {@code resolvedVariables} (pretty JSON) feeds the editor's variables pane. */
    record PreviewResult(String html, Integer errorLine, String errorMessage, String resolvedVariables) { }

    /** Which view's shape to project. */
    record ShapeRequest(String viewName) { }

    /** Catalog entry: everything but the template body. {@code builtin} = shipped default, name-reserved. */
    record DocumentSummary(String name, String viewName, boolean builtin, boolean isPublic, List<String> groups,
            boolean valid, List<String> invalidReason) { }

    /** Editor payload. {@code builtin} documents load as starter kits — saving needs a NEW name. */
    record DocumentSource(String name, String viewName, String template, boolean builtin, boolean isPublic,
            List<String> groups, String defaultVariables, String window, boolean valid,
            List<String> invalidReason) { }

    /** Editor save payload; {@code name} comes from the path. {@code window} = document-level anchors. */
    record SaveDocumentRequest(String viewName, String template, boolean isPublic, List<String> groups,
            String defaultVariables, String window) { }

    /** Empty {@code errors} means stored. */
    record SaveResult(List<String> errors) { }
}
