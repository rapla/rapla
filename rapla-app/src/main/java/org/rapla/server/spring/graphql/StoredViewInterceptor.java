package org.rapla.server.spring.graphql;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * PRD 074 — named-operation transport for stored views.
 *
 * <p>Protocol: the SPA sends
 * {@code { "operationName": "ViewName", "query": "{__typename}",
 *           "extensions": { "storedView": true }, "variables": {...} }}.
 * This interceptor detects {@code extensions.storedView == true}, looks up the
 * view by {@code operationName}, and replaces the query in the {@link graphql.ExecutionInput}
 * before graphql-java runs. If the view is not found or is invalid, a context
 * marker is set and {@link ViewMetaInstrumentation} replaces the result with a
 * proper GraphQL error.
 *
 * <p>From/to defaults: when {@code filter.from}/{@code filter.to} are absent from
 * the variables, the interceptor injects the current Monday → Monday+7 window so
 * the SPA can render meaningful first-load data without knowing the right date range.
 */
@Component
public class StoredViewInterceptor implements WebGraphQlInterceptor
{
    /** {@code extensions} key that marks a stored-view execution request. */
    public static final String STORED_VIEW_EXT = "storedView";

    /** GraphQL-context key set when the requested view is not found. Value = view name (String). */
    public static final String VIEW_NOT_FOUND_CTX = "rapla.view.notFound";

    /** GraphQL-context key set when the requested view is invalid. Value = List&lt;String&gt; reasons. */
    public static final String VIEW_INVALID_CTX = "rapla.view.invalid";

    private static final DateTimeFormatter ISO_DT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ViewCatalogService catalog;

    public StoredViewInterceptor(ViewCatalogService catalog)
    {
        this.catalog = catalog;
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain)
    {
        Map<String, Object> extensions = request.getExtensions();
        if (extensions == null || !Boolean.TRUE.equals(extensions.get(STORED_VIEW_EXT)))
            return chain.next(request);

        String viewName = request.getOperationName();
        if (viewName == null || viewName.isBlank())
            return chain.next(request);

        Optional<ViewEntry> found = catalog.findView(viewName);

        if (found.isEmpty())
        {
            String name = viewName;
            request.configureExecutionInput((input, builder) ->
                    builder.query("{ __typename }")
                            .operationName(null)
                            .graphQLContext(c -> c.put(VIEW_NOT_FOUND_CTX, name))
                            .build());
            return chain.next(request);
        }

        ViewEntry view = found.get();
        if (!view.valid())
        {
            List<String> reasons = view.invalidReason();
            request.configureExecutionInput((input, builder) ->
                    builder.query("{ __typename }")
                            .operationName(null)
                            .graphQLContext(c -> c.put(VIEW_INVALID_CTX, reasons))
                            .build());
            return chain.next(request);
        }

        String storedQuery = view.queryText();
        String storedDefaults = view.defaultVariables();
        request.configureExecutionInput((input, builder) ->
                builder.query(storedQuery)
                        .variables(mergeDefaults(input.getVariables(), storedDefaults))
                        .build());
        return chain.next(request);
    }

    /**
     * Merge variable defaults: stored view defaults form the base, client-supplied vars
     * override them, then Monday-week fills any still-missing filter.from/to.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeDefaults(Map<String, Object> clientVars, String storedDefaultsJson)
    {
        Map<String, Object> base = parseDefaults(storedDefaultsJson);
        Map<String, Object> vars = new LinkedHashMap<>(base);
        if (clientVars != null) vars.putAll(clientVars);

        Map<String, Object> filter = vars.containsKey("filter")
                ? (Map<String, Object>) vars.get("filter") : Map.of();
        if (filter.containsKey("from") && filter.containsKey("to")) return vars;

        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDateTime from = monday.atStartOfDay();
        LocalDateTime to = monday.plusDays(7).atStartOfDay();

        Map<String, Object> mergedFilter = new LinkedHashMap<>(filter);
        if (!filter.containsKey("from")) mergedFilter.put("from", from.format(ISO_DT));
        if (!filter.containsKey("to")) mergedFilter.put("to", to.format(ISO_DT));

        vars.put("filter", mergedFilter);
        return vars;
    }

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseDefaults(String json)
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
}
