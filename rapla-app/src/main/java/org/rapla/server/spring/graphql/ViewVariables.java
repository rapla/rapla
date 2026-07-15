package org.rapla.server.spring.graphql;

import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.parser.Parser;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * PRD 074 — variable resolution for a stored view, shared by the two callers that execute one:
 * {@link StoredViewInterceptor} (the SPA's {@code /api/graphql} transport) and the PRD 097
 * document render pipeline. One implementation, so a document and the SPA agree on the window.
 *
 * <p>The window comes from the view's own {@code @window} directive (resolved per request by
 * {@link WindowResolver}), else the render-mode default — never a hardcoded week.
 */
public final class ViewVariables
{
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private ViewVariables() {}

    /**
     * Merge variable defaults: stored defaults form the base, caller-supplied vars override them,
     * then the view's resolved window (PRD 074 §"Window and inputs directives") fills any
     * still-missing {@code filter.from}/{@code filter.to}.
     */
    public static Map<String, Object> mergeDefaults(Map<String, Object> callerVars, String storedDefaultsJson,
            String queryText)
    {
        return mergeDefaults(callerVars, storedDefaultsJson, queryText, null);
    }

    /**
     * PRD 097 (2026-07-15) — variant with a DOCUMENT-level window override: anchors are
     * presentation policy and live on the document; the view's {@code @window} is only the
     * default. A non-null {@code overrideWindow} wins over the view's directive; absolute dates
     * already present in the variables still win over both.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> mergeDefaults(Map<String, Object> callerVars, String storedDefaultsJson,
            String queryText, WindowResolver.Window overrideWindow)
    {
        return mergeLayeredDefaults(callerVars, List.of(storedDefaultsJson == null ? "" : storedDefaultsJson),
                queryText, overrideWindow);
    }

    /**
     * PRD 097 (2026-07-15) — LAYERED defaults: {@code defaultsLowToHigh} are deep-merged in order
     * (a document's defaults INHERIT the view's and override per key — nested objects merge, so a
     * document pinning {@code filter.weekdays} keeps the view's {@code filter}-level pins), then
     * caller variables win, then the window fills any still-missing {@code filter.from/to}.
     */
    public static Map<String, Object> mergeLayeredDefaults(Map<String, Object> callerVars,
            List<String> defaultsLowToHigh, String queryText, WindowResolver.Window overrideWindow)
    {
        return mergeLayeredDefaults(callerVars, defaultsLowToHigh, queryText, overrideWindow, LocalDate.now());
    }

    /**
     * PRD 097 nav (2026-07-15) — variant with a caller-supplied reference day ({@code ?date=}):
     * window anchors resolve as if today were {@code referenceDate}, which is how the shell's
     * prev/today/next links navigate an anchored window.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> mergeLayeredDefaults(Map<String, Object> callerVars,
            List<String> defaultsLowToHigh, String queryText, WindowResolver.Window overrideWindow,
            LocalDate referenceDate)
    {
        Map<String, Object> vars = new LinkedHashMap<>();
        for (String layer : defaultsLowToHigh)
        {
            deepMerge(vars, parseDefaults(layer));
        }
        if (callerVars != null) deepMerge(vars, callerVars);

        Map<String, Object> filter = vars.get("filter") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        if (filter.containsKey("from") && filter.containsKey("to")) return vars;

        WindowResolver.Window window = overrideWindow != null ? overrideWindow
                : resolveWindow(queryText, referenceDate);

        Map<String, Object> mergedFilter = new LinkedHashMap<>(filter);
        if (!filter.containsKey("from")) mergedFilter.put("from", window.from());
        if (!filter.containsKey("to")) mergedFilter.put("to", window.to());

        vars.put("filter", mergedFilter);
        return vars;
    }

    /**
     * The view's effective window: its {@code @window} directive when declared, else the
     * render-mode default. Unparseable query text falls back to the table default.
     */
    public static WindowResolver.Window resolveWindow(String queryText)
    {
        return resolveWindow(queryText, LocalDate.now());
    }

    /** The same window, anchored to a caller-supplied reference day instead of today. */
    public static WindowResolver.Window resolveWindow(String queryText, LocalDate referenceDate)
    {
        OperationDefinition op = firstOperation(queryText);
        WindowResolver.Window declared = WindowResolver.fromOperation(op, referenceDate);
        if (declared != null) return declared;
        return WindowResolver.defaultWindow(renderModes(op), referenceDate);
    }

    /** Nested maps merge recursively; scalars and lists in {@code overlay} replace. */
    @SuppressWarnings("unchecked")
    private static void deepMerge(Map<String, Object> base, Map<String, Object> overlay)
    {
        for (Map.Entry<String, Object> entry : overlay.entrySet())
        {
            Object existing = base.get(entry.getKey());
            if (existing instanceof Map<?, ?> baseMap && entry.getValue() instanceof Map<?, ?> overlayMap)
            {
                Map<String, Object> merged = new LinkedHashMap<>((Map<String, Object>) baseMap);
                deepMerge(merged, (Map<String, Object>) overlayMap);
                base.put(entry.getKey(), merged);
            }
            else
            {
                base.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /** Parse a stored {@code defaultVariables} JSON object; unparseable or absent yields an empty map. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseDefaults(String json)
    {
        if (json == null || json.isBlank()) return Map.of();
        try
        {
            Object parsed = MAPPER.readValue(json, Object.class);
            return parsed instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        }
        catch (Exception e)
        {
            return Map.of();
        }
    }

    private static OperationDefinition firstOperation(String queryText)
    {
        if (queryText == null || queryText.isBlank()) return null;
        try
        {
            Document doc = new Parser().parseDocument(queryText);
            return doc.getDefinitions().stream()
                    .filter(d -> d instanceof OperationDefinition)
                    .map(d -> (OperationDefinition) d)
                    .findFirst().orElse(null);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /** Explicit {@code @view(renderModes:)} names off the operation — derived modes never add week/month. */
    private static List<String> renderModes(OperationDefinition op)
    {
        if (op == null) return List.of();
        return op.getDirectives().stream()
                .filter(d -> "view".equals(d.getName()))
                .flatMap(d -> d.getArguments().stream())
                .filter(a -> "renderModes".equals(a.getName()))
                .filter(a -> a.getValue() instanceof graphql.language.ArrayValue)
                .flatMap(a -> ((graphql.language.ArrayValue) a.getValue()).getValues().stream())
                .filter(v -> v instanceof graphql.language.EnumValue)
                .map(v -> ((graphql.language.EnumValue) v).getName())
                .toList();
    }
}
