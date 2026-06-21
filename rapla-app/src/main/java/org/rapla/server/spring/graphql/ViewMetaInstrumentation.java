package org.rapla.server.spring.graphql;

import graphql.ExecutionResult;
import graphql.GraphQLContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.language.IntValue;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.StringValue;
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
        meta.put("columns", columnsFrom(op.getSelectionSet(),
                parameters.getExecutionContext().getGraphQLSchema()));

        parameters.getExecutionContext().getGraphQLContext().put(CTX_KEY, meta);
        return SimpleInstrumentationContext.noOp();
    }

    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(
            ExecutionResult executionResult, InstrumentationExecutionParameters parameters,
            InstrumentationState state)
    {
        GraphQLContext ctx = parameters.getGraphQLContext();
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

    /** Resolve the object/interface type behind a root field so its sub-fields' types are known. */
    private static GraphQLFieldsContainer resolveContainer(GraphQLObjectType queryType, String rootField)
    {
        if (queryType == null) return null;
        GraphQLFieldDefinition fd = queryType.getFieldDefinition(rootField);
        if (fd == null) return null;
        GraphQLType t = GraphQLTypeUtil.unwrapAll(fd.getType());
        return (t instanceof GraphQLFieldsContainer fc) ? fc : null;
    }

    private static Map<String, Object> columnDescriptor(Field col, GraphQLFieldsContainer container, int idx)
    {
        Map<String, Object> c = new LinkedHashMap<>();
        String alias = col.getAlias() != null ? col.getAlias() : col.getName();
        c.put("alias", alias);
        Directive column = findDirective(col.getDirectives(), "column");
        String header = column != null ? stringArg(column, "header") : null;
        c.put("header", header != null ? header : alias);
        String type = resolveType(container, col.getName());
        if (type != null) c.put("type", type);
        Integer order = column != null ? intArg(column, "order") : null;
        if (order != null) c.put("order", order);
        if (findDirective(col.getDirectives(), "hidden") != null) c.put("hidden", true);
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
        // transient sort key — removed during sort, never emitted
        c.put("_order", order != null ? order : idx);
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

    private static Integer intArg(Directive d, String name)
    {
        Argument a = argByName(d, name);
        return (a != null && a.getValue() instanceof IntValue iv) ? iv.getValue().intValue() : null;
    }

    private static Argument argByName(Directive d, String name)
    {
        for (Argument a : d.getArguments()) if (name.equals(a.getName())) return a;
        return null;
    }
}
