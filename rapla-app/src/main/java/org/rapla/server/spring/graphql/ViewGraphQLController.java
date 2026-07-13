package org.rapla.server.spring.graphql;

import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.server.spring.graphql.RequestContextInstrumentation.RequestCtx;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * PRD 074 — GraphQL resolvers for the view catalog:
 * {@code listViews}, {@code getViewQuery}, {@code saveView}, {@code deleteView}.
 */
@Controller
public class ViewGraphQLController
{
    private final ViewCatalogService catalog;

    public ViewGraphQLController(ViewCatalogService catalog)
    {
        this.catalog = catalog;
    }

    @QueryMapping
    public List<Map<String, Object>> listViews(DataFetchingEnvironment env)
    {
        RequestCtx rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        List<ViewEntry> views = catalog.listViewsForCaller(rc.caller());
        List<Map<String, Object>> result = new ArrayList<>(views.size());
        for (ViewEntry v : views)
        {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", v.name());
            m.put("title", v.title());
            m.put("source", v.builtin() ? "BUILTIN" : "CUSTOM");
            m.put("valid", v.valid());
            m.put("invalidReason", v.invalidReason());
            m.put("public", v.isPublic());
            m.put("groups", v.groups());
            m.put("defaultVariables", v.defaultVariables());
            result.add(m);
        }
        return result;
    }

    @QueryMapping
    public String getViewQuery(@Argument("name") String name, DataFetchingEnvironment env)
    {
        RequestCtx rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        User caller = rc.caller();
        Optional<ViewEntry> entry = catalog.findView(name);
        if (entry.isEmpty()) return null;
        ViewEntry v = entry.get();
        if (!v.builtin() && (caller == null || !caller.isAdmin())) return null;
        return v.queryText();
    }

    /**
     * PRD 074 — dry-run validation for the editor. Same checks as {@link #saveView}, stores
     * nothing, and every issue carries a line/column so GraphiQL (Monaco-based) can mark it red.
     * Admin-only: it reflects the schema shape, and authoring is an admin action.
     */
    @QueryMapping
    public List<Map<String, Object>> validateView(@Argument("query") String query,
            DataFetchingEnvironment env)
    {
        requireAdmin(env);
        return catalog.validateQuery(query).stream()
                .map(i -> Map.<String, Object>of(
                        "message", i.message(), "line", i.line(), "column", i.column()))
                .toList();
    }

    /**
     * PRD 074 — the completion source for {@code @param(into: "…")}. Given the query being
     * edited, enumerate the dotted variable paths it could legally target — the same walk the
     * validator uses, so completion and validation cannot disagree.
     */
    @QueryMapping
    public List<Map<String, Object>> intoPaths(@Argument("query") String query,
            DataFetchingEnvironment env)
    {
        requireAdmin(env);
        return catalog.intoPathsFor(query).stream()
                .map(p -> Map.<String, Object>of("path", p.path(), "type", p.type()))
                .toList();
    }

    private void requireAdmin(DataFetchingEnvironment env)
    {
        RequestCtx rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        User caller = rc.caller();
        if (caller == null || !caller.isAdmin())
        {
            throw new IllegalStateException("admin required");
        }
    }

    @MutationMapping
    public Map<String, Object> saveView(
            @Argument("name") String name,
            @Argument("query") String query,
            @Argument("public") Boolean isPublic,
            @Argument("groups") List<String> groups,
            @Argument("defaultVariables") String defaultVariables,
            DataFetchingEnvironment env) throws RaplaException
    {
        RequestCtx rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        User caller = rc.caller();
        if (caller == null || !caller.isAdmin())
            return Map.of("ok", false, "invalidReason", List.of("Admin permission required"));

        List<String> errors = catalog.saveView(name, query,
                Boolean.TRUE.equals(isPublic), groups, defaultVariables, caller);
        return Map.of("ok", errors.isEmpty(), "invalidReason", errors);
    }

    @MutationMapping
    public boolean deleteView(@Argument("name") String name, DataFetchingEnvironment env)
            throws RaplaException
    {
        RequestCtx rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        User caller = rc.caller();
        if (caller == null || !caller.isAdmin()) return false;
        return catalog.deleteView(name, caller);
    }
}
