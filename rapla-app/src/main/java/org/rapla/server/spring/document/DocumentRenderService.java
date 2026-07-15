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
import org.rapla.server.spring.graphql.ViewParamDirectives;
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
    /** Optional — present when the timeslot plugin is on the classpath (band-domain grouping). */
    private final org.springframework.beans.factory.ObjectProvider<
            org.rapla.plugin.timeslot.TimeslotProvider> timeslotProvider;

    public DocumentRenderService(DocumentCatalogService documents, ViewCatalogService views,
            HotSwappableGraphQlSource graphQlSource, DocumentRenderer renderer,
            org.springframework.beans.factory.ObjectProvider<
                    org.rapla.plugin.timeslot.TimeslotProvider> timeslotProvider)
    {
        this.documents = documents;
        this.views = views;
        this.graphQlSource = graphQlSource;
        this.renderer = renderer;
        this.timeslotProvider = timeslotProvider;
    }

    /**
     * Render a document to a complete HTML page. Empty when the caller may not see the document
     * or its view, when either is invalid, or when a {@code required @param} is absent — the
     * caller cannot tell these apart (§12). Raw URL parameters are gated by the view's declared
     * {@code @param}/{@code @window} surface: an undeclared key is a 400
     * ({@link UndeclaredParameterException}), a declared public {@code name} is translated to its
     * private {@code into} path before variable expansion.
     */
    public Optional<String> render(String documentName, Map<String, List<String>> rawParams, User caller)
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

        Optional<Map<String, Object>> requestVariables = gateParams(referenced.get(), doc.window(), rawParams);
        if (requestVariables.isEmpty()) return Optional.empty();   // required @param absent → same 404

        ViewEntry view = referenced.get();
        java.time.LocalDate dateParam = parseReferenceDate(rawParams);
        java.time.LocalDate referenceDate = dateParam != null ? dateParam : java.time.LocalDate.now();
        // ?date= is a CALLER gesture, ranked like ?from/?to: it outranks stored defaults. An
        // explicitly navigated window is injected at the caller layer (explicit ?from/?to still win).
        Map<String, Object> callerVars = dateParam == null ? requestVariables.get()
                : withNavigatedWindow(requestVariables.get(), view, doc.window(), dateParam);
        Map<String, Object> variables = resolveVariables(view, doc.defaultVariables(), doc.window(),
                callerVars, referenceDate);
        Map<String, Object> model = executeResolved(view, variables, doc.name());
        putNav(model, view, doc.window(), rawParams, variables, false);
        return Optional.of(page(title(view, doc.name()), doc.template(), model));
    }

    /** The {@code ?date=} reference day (nav, 2026-07-15); malformed → 400, absent → null. */
    private static java.time.LocalDate parseReferenceDate(Map<String, List<String>> rawParams)
    {
        List<String> values = rawParams == null ? null : rawParams.get("date");
        if (values == null || values.isEmpty()) return null;
        try
        {
            return java.time.LocalDate.parse(values.get(0));
        }
        catch (java.time.format.DateTimeParseException e)
        {
            throw new UndeclaredParameterException();
        }
    }

    /** The document's effective window (document anchors over view {@code @window}) at a reference day. */
    private static org.rapla.server.spring.graphql.WindowResolver.Window effectiveWindow(
            ViewEntry view, String documentWindow, java.time.LocalDate date)
    {
        return DocumentWindow.resolve(documentWindow, date)
                .orElseGet(() -> ViewVariables.resolveWindow(view.queryText(), date));
    }

    /** The effective window for an explicit {@code ?date=}, injected as caller-level bounds. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> withNavigatedWindow(Map<String, Object> callerVars,
            ViewEntry view, String documentWindow, java.time.LocalDate date)
    {
        org.rapla.server.spring.graphql.WindowResolver.Window window =
                effectiveWindow(view, documentWindow, date);
        Map<String, Object> vars = new LinkedHashMap<>(callerVars == null ? Map.of() : callerVars);
        Map<String, Object> filter = vars.get("filter") instanceof Map<?, ?> m
                ? new LinkedHashMap<>((Map<String, Object>) m) : new LinkedHashMap<>();
        filter.putIfAbsent("from", window.from());
        filter.putIfAbsent("to", window.to());
        vars.put("filter", filter);
        return vars;
    }

    /**
     * PRD 097 nav (2026-07-15) — navigation is TEMPLATE data, not shell chrome: the model gets a
     * {@code nav} entry ({@code prevUrl}/{@code todayUrl}/{@code nextUrl}/{@code label}) and the
     * author places {@code {{#nav}}…{{/nav}}} wherever they want (the shell CSS offers
     * {@code .rapla-nav} as a ready-made look). Present for every windowed document. Prev/next
     * are unit-free: prev = window start minus one day (the anchor snaps it into the previous
     * period), next = the exclusive window end's date (the first day of the next period) —
     * weeks, months and day-set windows all step correctly.
     */
    private static void putNav(Map<String, Object> model, ViewEntry view, String documentWindow,
            Map<String, List<String>> rawParams, Map<String, Object> variables, boolean previewMode)
    {
        ViewParamDirectives.Declarations decl = ViewParamDirectives.parse(view.queryText());
        boolean windowed = decl.hasWindow() || (documentWindow != null && !documentWindow.isBlank());
        if (!windowed) return;
        java.time.LocalDateTime from = boundOf(variables, "from");
        java.time.LocalDateTime to = boundOf(variables, "to");
        if (from == null || to == null || !to.isAfter(from)) return;

        java.time.LocalDate prev = from.toLocalDate().minusDays(1);
        java.time.LocalDate next = to.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)
                ? to.toLocalDate() : to.toLocalDate().plusDays(1);
        java.time.format.DateTimeFormatter label = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy");
        Map<String, Object> nav = new LinkedHashMap<>();
        nav.put("prevUrl", previewMode ? "#" : navUrl(rawParams, prev));
        nav.put("todayUrl", previewMode ? "#" : navUrl(rawParams, null));
        nav.put("nextUrl", previewMode ? "#" : navUrl(rawParams, next));
        nav.put("label", label.format(from.toLocalDate()) + " – " + label.format(next.minusDays(1)));
        if (previewMode)
        {
            // Working preview nav (2026-07-15, "full transparency"): a click cannot navigate the
            // sandboxed srcdoc iframe, so the shell's preview script postMessages the TARGET
            // WINDOW to the editor, which writes it into the vars box and re-previews. The
            // neighbor windows are resolved HERE so the editor does zero date arithmetic; the
            // data-nav-* attributes (see the rapla/nav partial) are the contract custom nav
            // markup can adopt too.
            org.rapla.server.spring.graphql.WindowResolver.Window prevWin =
                    effectiveWindow(view, documentWindow, prev);
            org.rapla.server.spring.graphql.WindowResolver.Window nextWin =
                    effectiveWindow(view, documentWindow, next);
            nav.put("prevFrom", prevWin.from());
            nav.put("prevTo", prevWin.to());
            nav.put("nextFrom", nextWin.from());
            nav.put("nextTo", nextWin.to());
        }
        model.put("nav", nav);
    }

    private static java.time.LocalDateTime boundOf(Map<String, Object> variables, String key)
    {
        if (!(variables.get("filter") instanceof Map<?, ?> filter)) return null;
        try
        {
            return java.time.LocalDateTime.parse(String.valueOf(filter.get(key)));
        }
        catch (java.time.format.DateTimeParseException e)
        {
            return null;
        }
    }

    /**
     * Relative link: the current request's params with {@code from}/{@code to}/{@code date}
     * dropped (a nav click means "leave any pinned range, go anchored") and the new reference
     * day appended. Declared {@code @param}s ({@code ?resource=…}) carry through.
     */
    private static String navUrl(Map<String, List<String>> rawParams, java.time.LocalDate date)
    {
        StringBuilder query = new StringBuilder();
        if (rawParams != null)
        {
            for (Map.Entry<String, List<String>> entry : rawParams.entrySet())
            {
                String key = entry.getKey();
                if ("from".equals(key) || "to".equals(key) || "date".equals(key)) continue;
                for (String value : entry.getValue())
                {
                    if (query.length() > 0) query.append('&');
                    query.append(java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8))
                            .append('=')
                            .append(java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        }
        if (date != null)
        {
            if (query.length() > 0) query.append('&');
            query.append("date=").append(date);
        }
        return "?" + query;
    }

    /** Thrown for a URL parameter outside the view's declared {@code @param}/{@code @window} surface → 400. */
    public static class UndeclaredParameterException extends RuntimeException
    {
        UndeclaredParameterException() { super("undeclared request parameter"); }
    }

    /**
     * PRD 074 §"Window and inputs directives" — the document-path input gate. The view's
     * {@code @param} names (plus {@code from}/{@code to} iff it declares {@code @window}) are the
     * ONLY accepted URL keys; each is translated public {@code name} → private {@code into} path,
     * then expanded to nested variables. Empty when a {@code required} param is absent — the
     * caller sees the same 404 as for a document that does not exist. Fires only on this path:
     * the SPA transport and the authoring preview construct their own variables.
     */
    private static Optional<Map<String, Object>> gateParams(ViewEntry view, String documentWindow,
            Map<String, List<String>> rawParams)
    {
        ViewParamDirectives.Declarations decl = ViewParamDirectives.parse(view.queryText());
        // PRD 097 (2026-07-15) — a document-level window opens the ?from/?to surface exactly like
        // a view-level @window; it targets the default "filter" variable.
        boolean windowed = decl.hasWindow() || (documentWindow != null && !documentWindow.isBlank());
        String windowInto = decl.hasWindow() ? decl.windowInto() : "filter";
        Map<String, List<String>> translated = new LinkedHashMap<>();
        if (rawParams != null)
        {
            for (Map.Entry<String, List<String>> entry : rawParams.entrySet())
            {
                String key = entry.getKey();
                ViewParamDirectives.Param param = decl.byName(key);
                if (param != null)
                {
                    translated.put(param.into(), entry.getValue());
                }
                else if (windowed && ("from".equals(key) || "to".equals(key)))
                {
                    translated.put(windowInto + "." + key, entry.getValue());
                }
                else if (windowed && "date".equals(key))
                {
                    // The nav reference day — consumed by the render pipeline, not a variable.
                }
                else
                {
                    throw new UndeclaredParameterException();
                }
            }
        }
        for (ViewParamDirectives.Param p : decl.params())
        {
            if (!p.required()) continue;
            List<String> values = rawParams == null ? null : rawParams.get(p.name());
            if (values == null || values.isEmpty() || values.stream().allMatch(String::isBlank))
            {
                return Optional.empty();
            }
        }
        return Optional.of(RequestVariables.expand(translated));
    }

    /**
     * PRD 097 Phase 4 — render an <b>unsaved</b> template for the authoring editor. Same engine,
     * same sanitizer, same shell as {@link #render} (a preview that renders differently is worse
     * than none), and the view still executes in the author's own §12 read-scope: the preview shows
     * the author only data they may already see. Empty when the view is unknown or not theirs.
     */
    /** Preview outcome: the page plus the RESOLVED variables the render actually used (editor pane). */
    public record Preview(String html, Map<String, Object> variables) { }

    /** A preview input problem the editor should show as a message (not an HTTP error). */
    public static class PreviewProblemException extends RuntimeException
    {
        PreviewProblemException(String message) { super(message); }
    }

    /**
     * PRD 097 § params (2026-07-15) — the preview takes the SAME raw URL params as the rendered
     * document and routes them through the SAME gate: the editor's vars field is literally the
     * document URL's query string (public {@code @param} names, {@code from/to/date} on windowed
     * views; an undeclared key errors here exactly as the live URL 400s). A preview that speaks
     * a different parameter language than the page it previews is a lie.
     */
    public Optional<Preview> preview(String viewName, String template, String defaultVariables,
            String window, Map<String, List<String>> rawParams, User author)
    {
        Optional<ViewEntry> referenced = views.findViewForCaller(viewName, author);
        if (referenced.isEmpty()) return Optional.empty();
        ViewEntry view = referenced.get();

        Optional<Map<String, Object>> requestVariables = gateParams(view, window, rawParams);
        if (requestVariables.isEmpty())
        {
            throw new PreviewProblemException("A required parameter is missing — the live URL responds 404");
        }
        java.time.LocalDate dateParam = parseReferenceDate(rawParams);
        java.time.LocalDate referenceDate = dateParam != null ? dateParam : java.time.LocalDate.now();
        Map<String, Object> callerVars = dateParam == null ? requestVariables.get()
                : withNavigatedWindow(requestVariables.get(), view, window, dateParam);
        Map<String, Object> variables = resolveVariables(view, defaultVariables, window, callerVars, referenceDate);
        Map<String, Object> model = executeResolved(view, variables, "<preview>");
        // Nav renders in the preview too — preview mode: clicks postMessage to the editor.
        putNav(model, view, window, null, variables, true);
        return Optional.of(new Preview(page(title(view, viewName), template, model, true), variables));
    }

    private String page(String title, String template, Map<String, Object> model)
    {
        return page(title, template, model, false);
    }

    private String page(String title, String template, Map<String, Object> model, boolean inertLinks)
    {
        return DocumentShell.wrap(title,
                DocumentSanitizer.sanitizeFragment(renderer.render(template, model)), inertLinks);
    }

    private static String title(ViewEntry view, String fallback)
    {
        return view.title() != null ? view.title() : fallback;
    }

    /** Execute the view in-process; the security context of the current request supplies the caller. */
    /**
     * PRD 097 (2026-07-15) — the effective variables of a render: the view's defaultVariables are
     * the BASELINE, the document's deep-merge on top (per key, nested objects merged), caller
     * variables win over both, the window (document anchors over view {@code @window} default)
     * fills any still-missing {@code filter.from/to}.
     */
    private Map<String, Object> resolveVariables(ViewEntry view, String documentDefaults, String documentWindow,
            Map<String, Object> requestVariables, java.time.LocalDate referenceDate)
    {
        List<String> layers = new ArrayList<>();
        if (view.defaultVariables() != null) layers.add(view.defaultVariables());
        if (documentDefaults != null) layers.add(documentDefaults);
        org.rapla.server.spring.graphql.WindowResolver.Window window =
                DocumentWindow.resolve(documentWindow, referenceDate).orElse(null);
        return ViewVariables.mergeLayeredDefaults(requestVariables, layers, view.queryText(), window,
                referenceDate);
    }

    private Map<String, Object> executeResolved(ViewEntry view, Map<String, Object> variables, String documentName)
    {
        ExecutionInput input = ExecutionInput.newExecutionInput()
                .query(view.queryText())
                .variables(variables)
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
        model.put(GROUPS_KEY, RowGrouping.groupByColumn(rows, alias,
                format instanceof String f ? f : null, domainFor(viewMeta.get("groupField"))));
    }

    /**
     * PRD 097 Phase 5 — the configured domain of a grouping field, so configured-but-empty groups
     * still render (an empty "nachmittags" band keeps its row — the frame argument, band edition).
     * Currently only {@code timeslot} has one: the server-configured band labels, in band order.
     */
    private List<String> domainFor(Object groupField)
    {
        if (!"timeslot".equals(groupField)) return null;
        org.rapla.plugin.timeslot.TimeslotProvider provider = timeslotProvider.getIfAvailable();
        if (provider == null) return null;
        return provider.getTimeslots().stream().map(org.rapla.plugin.timeslot.Timeslot::getName).toList();
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
