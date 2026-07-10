package org.rapla.server.spring.document;

import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.TypeName;
import graphql.parser.Parser;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.rapla.server.spring.graphql.HotSwappableGraphQlSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * PRD 097 Phase 4 — project a stored view's GraphQL query onto the data tree a Mustache template
 * addresses. One source for the editor's three data-aware features: the available-fields pane,
 * field completion, and unknown-field warnings.
 *
 * <p>Two rules make the projection <i>template-truthful</i> rather than schema-truthful:
 * <ul>
 *   <li>The node's key is the <b>response key</b> — an alias wins over the field name, because
 *       {@code termine: blocks} is written {@code {{#termine}}} in the template.</li>
 *   <li>A list is a <b>section</b>, an object is a dotted path. That is the only distinction
 *       Mustache's logic-less syntax makes, so it is the only one the shape carries.</li>
 * </ul>
 *
 * <p>Directives ({@code @column}, {@code @hidden}, {@code @join}) are presentation metadata for the
 * SPA table; they do not remove a field from the GraphQL data map, so they do not change the shape.
 */
@Component
public class ResultShapeService
{
    /** One node of the data tree. {@code fields} is empty for scalars. */
    public record ShapeNode(String key, String type, boolean list, List<ShapeNode> fields) { }

    private final Supplier<GraphQLSchema> schema;

    @Autowired
    public ResultShapeService(HotSwappableGraphQlSource graphQlSource)
    {
        this(graphQlSource::schema);
    }

    ResultShapeService(GraphQLSchema schema)
    {
        this(() -> schema);
    }

    private ResultShapeService(Supplier<GraphQLSchema> schema)
    {
        this.schema = schema;
    }

    /**
     * The shape of the query's single operation, as a synthetic root node whose {@code fields} are
     * the query's root fields. Empty when the query does not parse (the editor then shows the
     * compiler's own error instead of a stale tree).
     */
    public Optional<ShapeNode> shapeOf(String queryText)
    {
        Document document;
        try
        {
            document = Parser.parse(queryText);
        }
        catch (Exception e)
        {
            return Optional.empty();
        }

        Map<String, FragmentDefinition> fragments = new HashMap<>();
        OperationDefinition operation = null;
        for (Definition<?> def : document.getDefinitions())
        {
            if (def instanceof FragmentDefinition fd) fragments.put(fd.getName(), fd);
            else if (def instanceof OperationDefinition od && operation == null) operation = od;
        }
        if (operation == null) return Optional.empty();

        GraphQLSchema live = schema.get();
        List<ShapeNode> roots = children(operation.getSelectionSet(), live.getQueryType(), fragments);
        return Optional.of(new ShapeNode("", live.getQueryType().getName(), false, roots));
    }

    private List<ShapeNode> children(SelectionSet selectionSet, GraphQLFieldsContainer parent,
            Map<String, FragmentDefinition> fragments)
    {
        List<ShapeNode> out = new ArrayList<>();
        if (selectionSet == null || parent == null) return out;
        for (Selection<?> selection : selectionSet.getSelections())
        {
            if (selection instanceof Field field)
            {
                node(field, parent, fragments).ifPresent(out::add);
            }
            else if (selection instanceof InlineFragment inline)
            {
                out.addAll(children(inline.getSelectionSet(),
                        condition(inline.getTypeCondition(), parent), fragments));
            }
            else if (selection instanceof FragmentSpread spread)
            {
                FragmentDefinition fd = fragments.get(spread.getName());
                if (fd != null)
                {
                    out.addAll(children(fd.getSelectionSet(), condition(fd.getTypeCondition(), parent), fragments));
                }
            }
        }
        return out;
    }

    /**
     * The container a fragment's fields resolve against: its type condition, not the enclosing type.
     * {@code classification { ... on PersonClassification { surname } }} selects fields that exist on
     * the concrete type only — resolving them against the {@code Classification} interface finds
     * nothing. Templates flatten the fragment away (Mustache sees plain {@code {{surname}}}), so the
     * shape does too. An unresolvable condition falls back to the parent rather than dropping the
     * whole branch.
     */
    private GraphQLFieldsContainer condition(TypeName typeCondition, GraphQLFieldsContainer parent)
    {
        if (typeCondition == null) return parent;
        GraphQLType type = schema.get().getType(typeCondition.getName());
        return type instanceof GraphQLFieldsContainer container ? container : parent;
    }

    /** A field the live schema does not know is dropped — the tree stops there rather than guessing. */
    private Optional<ShapeNode> node(Field field, GraphQLFieldsContainer parent,
            Map<String, FragmentDefinition> fragments)
    {
        GraphQLFieldDefinition definition = parent.getFieldDefinition(field.getName());
        if (definition == null) return Optional.empty();

        GraphQLType unwrapped = GraphQLTypeUtil.unwrapNonNull(definition.getType());
        boolean list = unwrapped instanceof GraphQLList;
        GraphQLType inner = GraphQLTypeUtil.unwrapAll(definition.getType());

        String key = field.getAlias() != null ? field.getAlias() : field.getName();
        String typeName = inner instanceof GraphQLNamedType named ? named.getName() : "Unknown";
        List<ShapeNode> fields = inner instanceof GraphQLFieldsContainer container
                ? children(field.getSelectionSet(), container, fragments)
                : List.of();
        return Optional.of(new ShapeNode(key, typeName, list, fields));
    }
}
