package org.rapla.server.spring.document;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.server.spring.JwtUserResolver;
import org.rapla.server.spring.graphql.ViewCatalogService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * PRD 097 Phase 2 — the document surface. Implements {@link DocumentApi}; routing lives there.
 *
 * <p><b>Render</b> ({@code GET /{name}}) returns a complete HTML page, or 404 — and the 404 is the
 * <i>same</i> response whether the document does not exist, is hidden from the caller, references a
 * view the caller may not see, or is invalid (§12: existence never leaks). The response's
 * {@code Content-Security-Policy: sandbox …} comes from {@code RaplaCspHeaderWriter}, which scopes
 * it to this path prefix — the page runs in an opaque origin with no scripts and no network.
 *
 * <p><b>Editor CRUD</b> is admin-only. {@code /source} is gated like a write: a template is code
 * that renders into other users' browsers, so reading one is an authoring action.
 */
@RestController
public class DocumentController implements DocumentApi
{
    private final DocumentCatalogService documents;
    private final DocumentRenderService renderService;
    private final DocumentRenderer renderer;
    private final ResultShapeService shapes;
    private final ViewCatalogService views;
    private final JwtUserResolver jwtUserResolver;

    public DocumentController(DocumentCatalogService documents, DocumentRenderService renderService,
            DocumentRenderer renderer, ResultShapeService shapes, ViewCatalogService views,
            JwtUserResolver jwtUserResolver)
    {
        this.documents = documents;
        this.renderService = renderService;
        this.renderer = renderer;
        this.shapes = shapes;
        this.views = views;
        this.jwtUserResolver = jwtUserResolver;
    }

    @Override
    public ResponseEntity<String> render(String name, MultiValueMap<String, String> variables)
    {
        User caller = jwtUserResolver.resolveCurrentUserOrNull();
        Optional<String> page;
        try
        {
            // Raw params go through — the render service gates them against the view's
            // declared @param/@window surface (PRD 074 §"Window and inputs directives").
            page = renderService.render(name, variables, caller);
        }
        catch (DocumentRenderService.UndeclaredParameterException e)
        {
            return ResponseEntity.badRequest()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .build();
        }
        if (page.isEmpty()) return notFound();
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(page.get());
    }

    @Override
    public List<DocumentSummary> list()
    {
        User caller = jwtUserResolver.resolveCurrentUserOrNull();
        return documents.list(caller).stream()
                .map(d -> new DocumentSummary(d.name(), d.viewName(), d.builtin(), d.isPublic(), d.groups(),
                        d.valid(), d.invalidReason()))
                .toList();
    }

    @Override
    public DocumentSource source(String name) throws RaplaException
    {
        User caller = jwtUserResolver.resolveCurrentUserOrNull();
        documents.requireAuthor(caller);
        DocumentEntry d = documents.find(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return new DocumentSource(d.name(), d.viewName(), d.template(), d.builtin(), d.isPublic(), d.groups(),
                d.defaultVariables(), d.window(), d.valid(), d.invalidReason());
    }

    @Override
    public ResponseEntity<SaveResult> save(String name, SaveDocumentRequest body) throws RaplaException
    {
        User caller = jwtUserResolver.resolveCurrentUserOrNull();
        List<String> errors = documents.save(name, body.viewName(), body.template(), body.isPublic(),
                body.groups(), body.defaultVariables(), body.window(), caller);
        return errors.isEmpty()
                ? ResponseEntity.ok(new SaveResult(List.of()))
                : ResponseEntity.badRequest().body(new SaveResult(errors));
    }

    @Override
    public ResponseEntity<Void> delete(String name) throws RaplaException
    {
        User caller = jwtUserResolver.resolveCurrentUserOrNull();
        return documents.delete(name, caller)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @Override
    public PreviewResult preview(PreviewRequest body) throws RaplaException
    {
        User author = jwtUserResolver.resolveCurrentUserOrNull();
        documents.requireAuthor(author);

        Optional<DocumentRenderer.TemplateError> parseError = renderer.validate(body.template());
        if (parseError.isPresent())
        {
            return new PreviewResult(null, parseError.get().line(), parseError.get().message(), null);
        }
        try
        {
            // The vars field carries the document URL's raw query params — gated identically to
            // the live render (PRD 097 § params, 2026-07-15). Gate problems become editor
            // messages, not HTTP errors: the author is mid-edit, not mid-attack.
            return renderService
                    .preview(body.viewName(), body.template(), body.defaultVariables(), body.window(),
                            body.variables(), author)
                    .map(p -> new PreviewResult(p.html(), null, null, prettyVariables(p.variables())))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "No such view, or it is not visible to you"));
        }
        catch (DocumentRenderService.UndeclaredParameterException e)
        {
            return new PreviewResult(null, null, "Unknown URL parameter — the vars field takes the "
                    + "document URL's query params: declared @param names, plus from/to/date on "
                    + "windowed views", null);
        }
        catch (DocumentRenderService.PreviewProblemException e)
        {
            return new PreviewResult(null, null, e.getMessage(), null);
        }
    }

    /** The variables pane content: the resolved variables of the preview, pretty-printed. */
    private static String prettyVariables(java.util.Map<String, Object> variables)
    {
        try
        {
            return tools.jackson.databind.json.JsonMapper.builder().build()
                    .writerWithDefaultPrettyPrinter().writeValueAsString(variables);
        }
        catch (Exception e)
        {
            return String.valueOf(variables);
        }
    }

    @Override
    public ResultShapeService.ShapeNode resultShape(ShapeRequest body) throws RaplaException
    {
        User author = jwtUserResolver.resolveCurrentUserOrNull();
        documents.requireAuthor(author);
        return views.findViewForCaller(body.viewName(), author)
                .flatMap(view -> shapes.shapeOf(view.queryText()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No such view, or its query does not parse"));
    }

    /** The one 404 shape. No body, no header that could distinguish the four reasons for it. */
    private static ResponseEntity<String> notFound()
    {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

}
