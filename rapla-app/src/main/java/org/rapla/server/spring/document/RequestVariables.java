package org.rapla.server.spring.document;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PRD 097 OQ9 — expand a document's flat request parameters into the nested GraphQL variables its
 * view declares.
 *
 * <p>A view's variables are input objects ({@code $filter: ReservationFilter!}), but a document is
 * reached by a URL, and a URL is flat. Two conventions bridge that, both borrowed from what URLs
 * already do rather than invented:
 *
 * <ul>
 *   <li><b>A dot nests.</b> {@code ?filter.allocatableIdsIn=r1} → {@code {filter:{allocatableIdsIn:"r1"}}}.
 *       A single value is left a scalar: GraphQL's input coercion wraps it for a {@code [ID!]} field,
 *       so the common "one resource" link needs no special syntax.</li>
 *   <li><b>A repeated key is a list.</b> {@code ?filter.allocatableIdsIn=r1&filter.allocatableIdsIn=r2}.
 *       Deliberately NOT comma-splitting: rapla resource names contain commas, and a value that
 *       silently becomes a list is the kind of surprise that only shows up in production.</li>
 * </ul>
 *
 * <p>Values stay strings; GraphQL coerces them per the variable's declared type. That covers
 * {@code String}, {@code ID}, enums and {@code LocalDateTime} — everything a document link carries.
 */
final class RequestVariables
{
    private RequestVariables() {}

    static Map<String, Object> expand(Map<String, List<String>> params)
    {
        Map<String, Object> root = new LinkedHashMap<>();
        if (params == null) return root;
        for (Map.Entry<String, List<String>> entry : params.entrySet())
        {
            List<String> values = entry.getValue();
            if (values == null || values.isEmpty()) continue;
            put(root, entry.getKey().split("\\."), values.size() == 1 ? values.get(0) : List.copyOf(values));
        }
        return root;
    }

    @SuppressWarnings("unchecked")
    private static void put(Map<String, Object> root, String[] path, Object value)
    {
        // "filter=x" and "filter.from=y" in one URL: the deeper path wins, whichever arrived first.
        // Resolving this by arrival order would make the same URL mean different things.
        Map<String, Object> node = root;
        for (int i = 0; i < path.length - 1; i++)
        {
            Object child = node.get(path[i]);
            if (!(child instanceof Map))
            {
                child = new LinkedHashMap<String, Object>();   // a scalar here loses to the object
                node.put(path[i], child);
            }
            node = (Map<String, Object>) child;
        }
        String leaf = path[path.length - 1];
        if (node.get(leaf) instanceof Map) return;
        node.put(leaf, value);
    }
}
