package org.rapla.server.spring.graphql;

import graphql.ExecutionResult;
import graphql.GraphQLContext;
import graphql.GraphqlErrorBuilder;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.Directive;
import graphql.language.EnumValue;
import graphql.language.Field;
import graphql.language.InlineFragment;
import graphql.language.IntValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Component;

/**
 * PRD 074 — render-meta layer. When the query operation carries the {@code @view}
 * directive, this emits {@code extensions.view = { key, title, columns }} alongside
 * {@code data} (the contract the SPA renderer / PRD 078 consumes). The column
 * descriptors come from the first root field's sub-selection + the per-field
 * presentation directives ({@code @column(header:,order:)}, {@code @hidden},
 * {@code @join(separator:)}). Field directives feed the descriptor only — they do
 * NOT alter {@code data}. No {@code @view} → no extensions, zero overhead.
 *
 * <p>Auto-discovered as an {@code Instrumentation} bean via the
 * {@link org.springframework.beans.factory.ObjectProvider} chain on
 * {@link HotSwappableGraphQlSource} (same as {@link RequestContextInstrumentation}).
 *
 * <p>v1: {@code title} is the literal directive arg. A composition title
 * (VIEW_TITLE-level functions) needs the calendar/view context and lands with the
 * server-side export path (PRD 077).
 */
@Component
public class ViewMetaInstrumentation extends SimplePerformantInstrumentation
{
    private static final String CTX_KEY = "rapla.view.meta";
    /** Resolver-written pagination meta ({@code appointmentBlocks}), nested under {@code view.page}. */
    public static final String PAGE_CTX_KEY = "rapla.view.page";

    @Override
    public InstrumentationContext<ExecutionResult> beginExecuteOperation(
            InstrumentationExecuteOperationParameters parameters, InstrumentationState state)
    {
        OperationDefinition op = parameters.getExecutionContext().getOperationDefinition();
        if (op == null) return SimpleInstrumentationContext.noOp();
        Directive view = findDirective(op.getDirectives(), "view");
        if (view == null) return SimpleInstrumentationContext.noOp();

        Map<String, Object> meta = new LinkedHashMap<>();
        if (op.getName() != null) meta.put("key", op.getName());
        String title = stringArg(view, "title");
        if (title != null) meta.put("title", title);
        String rowLabel = stringArg(view, "rowLabel");
        if (rowLabel != null) meta.put("rowLabel", rowLabel);
        String groupLabel = stringArg(view, "groupLabel");
        if (groupLabel != null) meta.put("groupLabel", groupLabel);
        List<Map<String, Object>> columns = columnsFrom(op.getSelectionSet(),
                parameters.getExecutionContext().getGraphQLSchema());
        meta.put("columns", columns);
        // PRD 074 — render-hint: the column marked @column(group: true) is the row-grouping key.
        // Emitted as the column's alias so a generic renderer reads one field (row[view.groupBy]).
        boolean hasGroup = false;
        for (Map<String, Object> c : columns)
        {
            if (Boolean.TRUE.equals(c.get("group")))
            {
                meta.put("groupBy", c.get("alias"));
                if (c.get("format") != null) meta.put("groupFormat", c.get("format"));
                hasGroup = true;
                break;
            }
        }
        // renderModes: an explicit @view(renderModes:) list wins; otherwise derive from the view's
        // shape — `table` always (columns render as a flat table), plus `grouped` when the view has a
        // @column(group:true). Grids (week/day/month) are never auto-added; they must be opted in.
        List<ViewRenderMode> renderModes = typedEnumListArg(view, "renderModes", ViewRenderMode.class);
        List<String> renderModeNames;
        if (!renderModes.isEmpty())
        {
            renderModeNames = renderModes.stream().map(Enum::name).toList();
        }
        else
        {
            List<String> derived = new java.util.ArrayList<>();
            derived.add(ViewRenderMode.table.name());
            if (hasGroup) derived.add(ViewRenderMode.grouped.name());
            renderModeNames = derived;
        }
        meta.put("renderModes", renderModeNames);

        // PRD 074 §"Window and inputs directives" — the server-resolved date window (@window
        // directive, else the render-mode default). The SPA seeds its date-nav from this; no
        // client-side anchor resolution.
        WindowResolver.Window window = WindowResolver.fromOperation(op, java.time.LocalDate.now());
        if (window == null) window = WindowResolver.defaultWindow(renderModeNames, java.time.LocalDate.now());
        meta.put("window", Map.of("from", window.from(), "to", window.to()));

        // PRD 074/078 — the variable signature (name + GraphQL type) is the type-driven
        // binding contract: the SPA fills each variable by TYPE without seeing the query.
        List<Map<String, Object>> variables = variablesFrom(op.getVariableDefinitions());
        if (!variables.isEmpty()) meta.put("variables", variables);

        parameters.getExecutionContext().getGraphQLContext().put(CTX_KEY, meta);
        return SimpleInstrumentationContext.noOp();
    }

    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(
            ExecutionResult executionResult, InstrumentationExecutionParameters parameters,
            InstrumentationState state)
    {
        GraphQLContext ctx = parameters.getGraphQLContext();
        if (ctx != null)
        {
            String notFound = ctx.get(StoredViewInterceptor.VIEW_NOT_FOUND_CTX);
            if (notFound != null)
            {
                ExecutionResult err = executionResult.transform(b -> b
                        .data(null)
                        .errors(List.of(GraphqlErrorBuilder.newError()
                                .message("View '" + notFound + "' not found")
                                .extensions(Map.of("code", "VIEW_NOT_FOUND"))
                                .build())));
                return CompletableFuture.completedFuture(err);
            }
            @SuppressWarnings("unchecked")
            List<String> invalidReasons = ctx.get(StoredViewInterceptor.VIEW_INVALID_CTX);
            if (invalidReasons != null)
            {
                ExecutionResult err = executionResult.transform(b -> b
                        .data(null)
                        .errors(List.of(GraphqlErrorBuilder.newError()
                                .message("View is invalid: " + String.join("; ", invalidReasons))
                                .extensions(Map.of("code", "VIEW_INVALID"))
                                .build())));
                return CompletableFuture.completedFuture(err);
            }
        }
        Object meta = ctx != null ? ctx.get(CTX_KEY) : null;
        if (meta == null)
        {
            return CompletableFuture.completedFuture(executionResult);
        }
        if (meta instanceof Map<?, ?> m)
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> mm = (Map<String, Object>) m;
            Object page = ctx.get(PAGE_CTX_KEY);
            if (page != null) mm.put("page", page);
        }
        Map<Object, Object> ext = new LinkedHashMap<>();
        if (executionResult.getExtensions() != null) ext.putAll(executionResult.getExtensions());
        ext.put("view", meta);
        return CompletableFuture.completedFuture(executionResult.transform(b -> b.extensions(ext)));
    }

    /**
     * The operation's variable signature ({@code name} + GraphQL {@code type} as an
     * SDL string, e.g. {@code ReservationFilter!}). The SPA binds each variable by
     * TYPE — ReservationFilter ← window+selection, AllocatableFilter ← selection —
     * so it can fill ALL required variables (e.g. a stats view's two filters) without
     * the stored query text. Unknown types are simply not auto-filled (server default).
     */
    private static List<Map<String, Object>> variablesFrom(List<VariableDefinition> vars)
    {
        if (vars == null) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (VariableDefinition v : vars)
        {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", v.getName());
            m.put("type", typeName(v.getType()));
            out.add(m);
        }
        return out;
    }

    /** Render an AST type to its SDL string — {@code ReservationFilter!}, {@code [ID!]!}, … */
    private static String typeName(graphql.language.Type<?> t)
    {
        if (t instanceof graphql.language.NonNullType nn) return typeName(nn.getType()) + "!";
        if (t instanceof graphql.language.ListType lt) return "[" + typeName(lt.getType()) + "]";
        if (t instanceof graphql.language.TypeName tn) return tn.getName();
        return "";
    }

    /**
     * Columns = the sub-selection of the first root data field, sorted by {@code @column(order:)}.
     * The root field's GraphQL type (resolved from the schema) gives each column a {@code type}
     * hint (the unwrapped scalar/object type name, e.g. {@code LocalDateTime}/{@code Allocatable})
     * so the GUI can pick alignment + formatting without re-deriving it.
     */
    private static List<Map<String, Object>> columnsFrom(SelectionSet opSel, GraphQLSchema schema)
    {
        List<Map<String, Object>> cols = new ArrayList<>();
        if (opSel == null) return cols;
        GraphQLObjectType queryType = schema != null ? schema.getQueryType() : null;
        for (Selection<?> s : opSel.getSelections())
        {
            if (s instanceof Field root && root.getSelectionSet() != null)
            {
                GraphQLFieldsContainer container = resolveContainer(queryType, root.getName());
                // PRD 079/080 — a stats root (BlockStatBucket) is logically ONE flat row whose
                // columns live in the generic keys[]/values[] lists keyed by the groupBy/aggregate
                // `key`s. Describe that flat row from the operation args + the keys{entity{…}}
                // selection (data shape stays untouched). Else: the flat-table columnizer.
                if (container != null && "BlockStatBucket".equals(container.getName()))
                {
                    return statsColumns(root);
                }
                int idx = 0;
                for (Selection<?> cs : root.getSelectionSet().getSelections())
                {
                    if (cs instanceof Field col)
                    {
                        cols.add(columnDescriptor(col, container, idx++));
                    }
                }
                break;   // first root field only
            }
        }
        // Sort by effective order (explicit @column(order:) else declaration index); stable.
        cols.sort(Comparator.comparingInt(c -> (Integer) c.get("_order")));
        cols.forEach(c -> c.remove("_order"));
        return cols;
    }

    /**
     * PRD 079/080 — flat-row columns for a {@code BlockStatBucket} root. One column per
     * {@code groupBy[].key} (kind {@code group}), the selected {@code keys{entity{…}}} leaf fields
     * (kind {@code entity}, with a dotted {@code path} under the group entity), one per
     * {@code aggregate[].key} (kind {@code value}, with {@code fn}), then {@code count}. The data
     * shape (keys/values/count) is unchanged; these columns tell the renderer how to flatten it.
     */
    private static List<Map<String, Object>> statsColumns(Field root)
    {
        List<Map<String, Object>> cols = new ArrayList<>();
        List<Map<String, String>> entityFields = entityFieldPaths(root);
        boolean entityAttached = false;
        int idx = 0;
        for (ObjectValue g : objectListArg(root, "groupBy"))
        {
            String key = objStr(g, "key");
            if (key == null) continue;
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("alias", key);
            Integer groupOrder = objInt(g, "order");
            applyPresentation(c, objStr(g, "header") != null ? objStr(g, "header") : key,
                    groupOrder, false, false, null, idx++);
            c.put("kind", "group");
            boolean entityDim = hasField(g, "allocatables") || Boolean.TRUE.equals(objBool(g, "reservation"));
            String dimType = hasField(g, "allocatables") ? "Allocatable"
                    : Boolean.TRUE.equals(objBool(g, "reservation")) ? "Reservation"
                    : hasField(g, "date") ? "Date"
                    : hasField(g, "expr") ? "String" : null;
            if (dimType != null) c.put("type", dimType);
            cols.add(c);
            // The keys{entity{…}} selection is shared across keys; attach to the first entity group.
            if (entityDim && !entityAttached)
            {
                for (Map<String, String> ef : entityFields)
                {
                    Map<String, Object> ec = new LinkedHashMap<>();
                    ec.put("alias", ef.get("name"));
                    String efOrdStr = ef.get("order");
                    Integer efOrder = efOrdStr != null ? Integer.parseInt(efOrdStr) : null;
                    applyPresentation(ec,
                            ef.getOrDefault("header", ef.get("name")),
                            efOrder, "true".equals(ef.get("hidden")), false, null, idx++);
                    ec.put("kind", "entity");
                    ec.put("group", key);
                    ec.put("path", ef.get("path"));
                    cols.add(ec);
                }
                entityAttached = true;
            }
        }
        for (ObjectValue a : objectListArg(root, "aggregate"))
        {
            String key = objStr(a, "key");
            if (key == null) continue;
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("alias", key);
            Integer aggOrder = objInt(a, "order");
            applyPresentation(c, objStr(a, "header") != null ? objStr(a, "header") : key,
                    aggOrder, false, false, null, idx++);
            c.put("kind", "value");
            String fn = objEnum(a, "fn");
            if (fn != null) c.put("fn", fn);
            cols.add(c);
        }
        // `count` only if actually selected — it's redundant with a COUNT aggregate, so callers
        // often drop it; emitting a column without backing data would be a contract lie.
        if (childField(root.getSelectionSet(), "count") != null)
        {
            Map<String, Object> cnt = new LinkedHashMap<>();
            cnt.put("alias", "count");
            cnt.put("kind", "count");
            cnt.put("type", "Int");
            cnt.put("_order", idx);
            cols.add(cnt);
        }
        cols.sort(Comparator.comparingInt(c -> (Integer) c.get("_order")));
        cols.forEach(c -> c.remove("_order"));
        return cols;
    }

    /** Leaf field paths selected under {@code keys{ entity { … } }} — e.g. {@code AnzahlPlaetzeInsgesamt},
     * {@code Gebaeude.Gebaeudename}. {@code classification} + inline fragments are transparent (no path
     * segment); reference objects add a segment; {@code __typename} is skipped. */
    private static List<Map<String, String>> entityFieldPaths(Field root)
    {
        List<Map<String, String>> out = new ArrayList<>();
        Field keys = childField(root.getSelectionSet(), "keys");
        if (keys == null) return out;
        Field entity = childField(keys.getSelectionSet(), "entity");
        if (entity == null || entity.getSelectionSet() == null) return out;
        walkEntity(entity.getSelectionSet(), "", out);
        return out;
    }

    private static void walkEntity(SelectionSet sel, String prefix, List<Map<String, String>> out)
    {
        if (sel == null) return;
        for (Selection<?> s : sel.getSelections())
        {
            if (s instanceof InlineFragment frag)
            {
                walkEntity(frag.getSelectionSet(), prefix, out);  // ... on X — transparent
            }
            else if (s instanceof Field f)
            {
                String name = f.getName();
                if ("__typename".equals(name)) continue;
                if (f.getSelectionSet() == null)
                {
                    String path = prefix.isEmpty() ? name : prefix + "." + name;
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("name", name);
                    m.put("path", path);
                    if (findDirective(f.getDirectives(), "hidden") != null) m.put("hidden", "true");
                    Directive col = findDirective(f.getDirectives(), "column");
                    if (col != null)
                    {
                        String hdr = stringArg(col, "header");
                        if (hdr != null) m.put("header", hdr);
                        Integer ord = intArg(col, "order");
                        if (ord != null) m.put("order", String.valueOf(ord));
                    }
                    out.add(m);
                }
                else
                {
                    // `classification` is structural — keep prefix; real refs (Gebaeude) extend it.
                    String seg = "classification".equals(name) ? prefix
                            : (prefix.isEmpty() ? name : prefix + "." + name);
                    walkEntity(f.getSelectionSet(), seg, out);
                }
            }
        }
    }

    private static Field childField(SelectionSet sel, String name)
    {
        if (sel == null) return null;
        for (Selection<?> s : sel.getSelections())
        {
            if (s instanceof Field f && name.equals(f.getName())) return f;
            if (s instanceof InlineFragment frag)
            {
                Field nested = childField(frag.getSelectionSet(), name);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static List<ObjectValue> objectListArg(Field f, String argName)
    {
        List<ObjectValue> out = new ArrayList<>();
        for (Argument a : f.getArguments())
        {
            if (!argName.equals(a.getName())) continue;
            if (a.getValue() instanceof ArrayValue arr)
            {
                for (Value<?> v : arr.getValues()) if (v instanceof ObjectValue ov) out.add(ov);
            }
        }
        return out;
    }

    private static Value<?> objField(ObjectValue o, String name)
    {
        for (ObjectField of : o.getObjectFields()) if (name.equals(of.getName())) return of.getValue();
        return null;
    }

    private static boolean hasField(ObjectValue o, String name) { return objField(o, name) != null; }

    private static String objStr(ObjectValue o, String name)
    {
        return objField(o, name) instanceof StringValue sv ? sv.getValue() : null;
    }

    private static String objEnum(ObjectValue o, String name)
    {
        return objField(o, name) instanceof EnumValue ev ? ev.getName() : null;
    }

    private static Boolean objBool(ObjectValue o, String name)
    {
        return objField(o, name) instanceof graphql.language.BooleanValue bv ? bv.isValue() : null;
    }

    private static Integer objInt(ObjectValue o, String name)
    {
        return objField(o, name) instanceof IntValue iv ? iv.getValue().intValue() : null;
    }

    /** Resolve the object/interface type behind a root field so its sub-fields' types are known. */
    private static GraphQLFieldsContainer resolveContainer(GraphQLObjectType queryType, String rootField)
    {
        if (queryType == null) return null;
        GraphQLFieldDefinition fd = queryType.getFieldDefinition(rootField);
        if (fd == null) return null;
        GraphQLType t = GraphQLTypeUtil.unwrapAll(fd.getType());
        return (t instanceof GraphQLFieldsContainer fc) ? fc : null;
    }

    /**
     * Single source of truth for all presentation attributes that a column descriptor
     * can carry. Both the {@code @column}-directive path (field selections) and the
     * {@code header}/{@code order} input-field path ({@code BlockGroupKey} /
     * {@code BlockAggregate}) funnel through here so a new attribute only needs to be
     * added in one place.
     *
     * @param c      the column map being built (alias already set by caller)
     * @param header explicit header, or null → caller falls back to alias
     * @param order  explicit column order, or null
     * @param hidden true → add {@code hidden:true}
     * @param group  true → add {@code group:true} (flat-view grouping marker)
     * @param format opaque date-format token, or null
     * @param idx    declaration index used as {@code _order} when order is null
     */
    private static void applyPresentation(Map<String, Object> c,
            String header, Integer order, boolean hidden, boolean group, String format, int idx)
    {
        if (header != null) c.put("header", header);
        if (order != null) c.put("order", order);
        if (hidden) c.put("hidden", true);
        if (group) c.put("group", true);
        if (format != null) c.put("format", format);
        c.put("_order", order != null ? order : idx);
    }

    private static Map<String, Object> columnDescriptor(Field col, GraphQLFieldsContainer container, int idx)
    {
        Map<String, Object> c = new LinkedHashMap<>();
        String alias = col.getAlias() != null ? col.getAlias() : col.getName();
        c.put("alias", alias);
        Directive column = findDirective(col.getDirectives(), "column");
        boolean hidden = findDirective(col.getDirectives(), "hidden") != null;
        applyPresentation(c,
                column != null ? stringArg(column, "header") : alias,
                column != null ? intArg(column, "order") : null,
                hidden,
                column != null && Boolean.TRUE.equals(boolArg(column, "group")),
                column != null ? stringArg(column, "format") : null,
                idx);
        if (!c.containsKey("header")) c.put("header", alias); // alias fallback
        String type = resolveType(container, col.getName());
        if (type != null) c.put("type", type);
        Directive join = findDirective(col.getDirectives(), "join");
        if (join != null)
        {
            String sep = stringArg(join, "separator");
            if (sep != null) c.put("join", sep);
        }
        Directive flatten = findDirective(col.getDirectives(), "flatten");
        if (flatten != null)
        {
            String leaf = stringArg(flatten, "field");
            if (leaf == null) leaf = singleSubFieldKey(col);
            c.put("flatten", leaf != null ? leaf : Boolean.TRUE);
        }
        return c;
    }

    /** Unwrapped type name of a sub-field, e.g. {@code String}, {@code LocalDateTime}, {@code Allocatable}. */
    private static String resolveType(GraphQLFieldsContainer container, String fieldName)
    {
        if (container == null) return null;
        GraphQLFieldDefinition fd = container.getFieldDefinition(fieldName);
        if (fd == null) return null;
        GraphQLType t = GraphQLTypeUtil.unwrapAll(fd.getType());
        return (t instanceof GraphQLNamedType nt) ? nt.getName() : null;
    }

    /** The response key (alias else name) of a column's single selected sub-field, or null. */
    private static String singleSubFieldKey(Field col)
    {
        if (col.getSelectionSet() == null) return null;
        Field only = null;
        for (Selection<?> s : col.getSelectionSet().getSelections())
        {
            if (s instanceof Field f)
            {
                if (only != null) return null;   // more than one → ambiguous
                only = f;
            }
        }
        if (only == null) return null;
        return only.getAlias() != null ? only.getAlias() : only.getName();
    }

    private static Directive findDirective(List<Directive> ds, String name)
    {
        if (ds == null) return null;
        for (Directive d : ds) if (name.equals(d.getName())) return d;
        return null;
    }

    private static String stringArg(Directive d, String name)
    {
        Argument a = argByName(d, name);
        return (a != null && a.getValue() instanceof StringValue sv) ? sv.getValue() : null;
    }

    private static <E extends Enum<E>> List<E> typedEnumListArg(Directive d, String name, Class<E> type)
    {
        Argument a = argByName(d, name);
        if (a == null || !(a.getValue() instanceof ArrayValue arr)) return List.of();
        List<E> out = new ArrayList<>();
        for (Value<?> v : arr.getValues())
        {
            if (v instanceof EnumValue ev)
            {
                try { out.add(Enum.valueOf(type, ev.getName())); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        return out;
    }

    private static Integer intArg(Directive d, String name)
    {
        Argument a = argByName(d, name);
        return (a != null && a.getValue() instanceof IntValue iv) ? iv.getValue().intValue() : null;
    }

    private static Boolean boolArg(Directive d, String name)
    {
        Argument a = argByName(d, name);
        return (a != null && a.getValue() instanceof graphql.language.BooleanValue bv) ? bv.isValue() : null;
    }

    private static Argument argByName(Directive d, String name)
    {
        for (Argument a : d.getArguments()) if (name.equals(a.getName())) return a;
        return null;
    }
}
