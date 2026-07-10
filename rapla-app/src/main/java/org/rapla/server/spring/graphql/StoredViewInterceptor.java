package org.rapla.server.spring.graphql;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
                        .variables(ViewVariables.mergeDefaults(input.getVariables(), storedDefaults))
                        .build());
        return chain.next(request);
    }
}
