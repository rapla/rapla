package org.rapla.server.spring.document;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.rapla.entities.User;
import org.rapla.server.spring.graphql.HotSwappableGraphQlSource;
import org.rapla.server.spring.graphql.ViewCatalogService;
import org.rapla.server.spring.graphql.ViewEntry;
import org.rapla.server.spring.graphql.ViewVariables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * PRD 097 Phase 2 — the render pipeline: resolve the document, execute its referenced view in the
 * <b>caller's</b> §12 read-scope, feed the resulting data tree to Mustache, sanitize, wrap in the
 * server-authored shell.
 *
 * <p>Authorization has three independent layers, in this order:
 * <ol>
 *   <li><b>Both artifacts must be visible</b> to the caller — the document and the view it
 *       references. Either one hidden yields {@code empty}, indistinguishable from "no such
 *       document" (§12: existence never leaks).</li>
 *   <li><b>The view executes as the caller.</b> {@code RequestContextInstrumentation} resolves the
 *       caller from the security context at {@code beginExecution}, so every resolver applies
 *       {@code canRead}. Request parameters (an {@code eventId}, say) are ordinary GraphQL
 *       variables: guessing an id the caller may not read yields a resolver {@code null}, hence an
 *       empty document — byte-identical to a non-existent id.</li>
 *   <li><b>The template can only render what the query already released</b> (D3): Mustache has no
 *       independent data access.</li>
 * </ol>
 */
@Service
public class DocumentRenderService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentRenderService.class);

    /** Key under which grouped rows are exposed to the template (Phase 3). */
    static final String GROUPS_KEY = "groups";

    private final DocumentCatalogService documents;
    private final ViewCatalogService views;
    private final HotSwappableGraphQlSource graphQlSource;
    private final DocumentRenderer renderer;

    public DocumentRenderService(DocumentCatalogService documents, ViewCatalogService views,
            HotSwappableGraphQlSource graphQlSource, DocumentRenderer renderer)
    {
        this.documents = documents;
        this.views = views;
        this.graphQlSource = graphQlSource;
        this.renderer = renderer;
    }

    /**
     * Render a document to a complete HTML page. Empty when the caller may not see the document
     * or its view, or when either is invalid — the caller cannot tell these apart.
     */
    public Optional<String> render(String documentName, Map<String, Object> requestVariables, User caller)
    {
        Optional<DocumentEntry> document = documents.findVisible(documentName, caller);
        if (document.isEmpty()) return Optional.empty();

        DocumentEntry doc = document.get();
        if (!doc.valid())
        {
            LOGGER.info("Document '{}' is invalid and will not render: {}", documentName, doc.invalidReason());
            return Optional.empty();
        }

        Optional<ViewEntry> referenced = views.findViewForCaller(doc.viewName(), caller);
        if (referenced.isEmpty() || !referenced.get().valid())
        {
            LOGGER.info("Document '{}' references view '{}', not available to this caller", documentName, doc.viewName());
            return Optional.empty();
        }

        Map<String, Object> model = executeView(referenced.get(), doc.defaultVariables(), doc.name(), requestVariables);
        return Optional.of(page(title(referenced.get(), doc.name()), doc.template(), model));
    }

    /**
     * PRD 097 Phase 4 — render an <b>unsaved</b> template for the authoring editor. Same engine,
     * same sanitizer, same shell as {@link #render} (a preview that renders differently is worse
     * than none), and the view still executes in the author's own §12 read-scope: the preview shows
     * the author only data they may already see. Empty when the view is unknown or not theirs.
     */
    public Optional<String> preview(String viewName, String template, String defaultVariables,
            Map<String, Object> requestVariables, User author)
    {
        Optional<ViewEntry> referenced = views.findViewForCaller(viewName, author);
        if (referenced.isEmpty()) return Optional.empty();

        Map<String, Object> model = executeView(referenced.get(), defaultVariables, "<preview>", requestVariables);
        return Optional.of(page(title(referenced.get(), viewName), template, model));
    }

    private String page(String title, String template, Map<String, Object> model)
    {
        return DocumentShell.wrap(title, DocumentSanitizer.sanitizeFragment(renderer.render(template, model)));
    }

    private static String title(ViewEntry view, String fallback)
    {
        return view.title() != null ? view.title() : fallback;
    }

    /** Execute the view in-process; the security context of the current request supplies the caller. */
    private Map<String, Object> executeView(ViewEntry view, String documentDefaults, String documentName,
            Map<String, Object> requestVariables)
    {
        String defaults = documentDefaults != null ? documentDefaults : view.defaultVariables();
        ExecutionInput input = ExecutionInput.newExecutionInput()
                .query(view.queryText())
                .variables(ViewVariables.mergeDefaults(requestVariables, defaults))
                .build();

        ExecutionResult result = graphQlSource.graphQl().execute(input);
        if (!result.getErrors().isEmpty())
        {
            // Field errors are normal (§12 nulls a resolver the caller may not read). Log, render
            // what survived: the document renders empty rather than revealing why.
            LOGGER.debug("View '{}' produced {} GraphQL error(s) while rendering document '{}': {}",
                    view.name(), result.getErrors().size(), documentName,
                    result.getErrors().stream().map(GraphQLError::getMessage).toList());
        }

        Map<String, Object> data = result.getData();
        Map<String, Object> model = new LinkedHashMap<>(data == null ? Map.of() : data);
        applyGrouping(model, result.getExtensions());
        return model;
    }

    /**
     * Phase 3 / OQ3 — when the view declares a grouping column ({@code @column(group: true)}),
     * {@code ViewMetaInstrumentation} reports its alias in {@code extensions.view.groupBy}. Bucket
     * the single root row list into sections so nested template sections can paint them. No new
     * GraphQL field, and the grouping semantics stay single-sourced in the view's own directive.
     */
    @SuppressWarnings("unchecked")
    private void applyGrouping(Map<String, Object> model, Map<Object, Object> extensions)
    {
        if (extensions == null || !(extensions.get("view") instanceof Map<?, ?> viewMeta)) return;
        Object groupBy = viewMeta.get("groupBy");
        if (!(groupBy instanceof String alias) || alias.isBlank()) return;

        List<Map<String, Object>> rows = rootRowList(model);
        if (rows == null) return;

        Object format = viewMeta.get("groupFormat");
        model.put(GROUPS_KEY, RowGrouping.groupByColumn(rows, alias, format instanceof String f ? f : null));
    }

    /** The one root field that carries the row list (e.g. {@code appointmentBlocks}). */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rootRowList(Map<String, Object> model)
    {
        for (Object value : model.values())
        {
            if (value instanceof List<?> list && list.stream().allMatch(e -> e instanceof Map))
            {
                return new ArrayList<>((List<Map<String, Object>>) list);
            }
        }
        return null;
    }
}
