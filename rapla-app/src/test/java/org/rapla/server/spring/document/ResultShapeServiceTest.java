package org.rapla.server.spring.document;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.rapla.server.spring.document.ResultShapeService.ShapeNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 097 Phase 4 — the result-shape projection feeds three editor features at once (fields pane,
 * completion, unknown-field warnings), so it must describe the data tree the way <b>Mustache</b>
 * sees it: response keys (aliases win), and list-vs-object (a list is a section, an object is a
 * dotted path).
 */
class ResultShapeServiceTest
{
    private static final String SDL = """
            type Query { blocks(limit: Int): [Block!]!  now: String  event(id: ID!): Event }
            type Block { start: String  name: String  reservation: Event  persons: [Person!]! }
            type Event { id: ID!  title: String }
            type Person { name: String  classification: Classification }
            interface Classification { id: ID! }
            type PersonClassification implements Classification { id: ID!  surname: String  firstname: String }
            type RoomClassification implements Classification { id: ID!  roomNumber: String }
            """;

    private final ResultShapeService shapes = new ResultShapeService(schema());

    private static GraphQLSchema schema()
    {
        // the shape projection never executes the query, so a no-op type resolver is enough
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type("Classification", builder -> builder.typeResolver(env -> null))
                .build();
        return new SchemaGenerator().makeExecutableSchema(new SchemaParser().parse(SDL), wiring);
    }

    private ShapeNode shapeOf(String query)
    {
        return shapes.shapeOf(query).orElseThrow();
    }

    @Test
    void rootFieldsBecomeTopLevelNodes()
    {
        ShapeNode root = shapeOf("query q { now }");
        assertEquals(List.of("now"), root.fields().stream().map(ShapeNode::key).toList());
        assertEquals("String", root.fields().get(0).type());
    }

    @Test
    void aListFieldIsMarkedAsASection()
    {
        ShapeNode blocks = shapeOf("query q { blocks { start } }").fields().get(0);
        assertTrue(blocks.list(), "a list field must be a Mustache section");
        assertEquals("Block", blocks.type());
        assertEquals(List.of("start"), blocks.fields().stream().map(ShapeNode::key).toList());
    }

    @Test
    void anObjectFieldIsNotASection()
    {
        ShapeNode reservation = shapeOf("query q { blocks { reservation { title } } }")
                .fields().get(0).fields().get(0);
        assertFalse(reservation.list());
        assertEquals(List.of("title"), reservation.fields().stream().map(ShapeNode::key).toList());
    }

    @Test
    void anAliasIsTheResponseKeyBecauseThatIsWhatTheTemplateWrites()
    {
        ShapeNode node = shapeOf("query q { termine: blocks { titel: name } }").fields().get(0);
        assertEquals("termine", node.key());
        assertEquals(List.of("titel"), node.fields().stream().map(ShapeNode::key).toList());
    }

    @Test
    void directivesOnFieldsDoNotChangeTheDataShape()
    {
        // @hidden marks a column as non-visible in the SPA table, but the field is still in the
        // GraphQL data map — so it is still addressable from a template.
        ShapeNode blocks = shapeOf("query q { blocks { start @column(header: \"Von\") name @hidden } }")
                .fields().get(0);
        assertEquals(List.of("start", "name"), blocks.fields().stream().map(ShapeNode::key).toList());
    }

    @Test
    void fragmentSpreadsAreInlinedIntoTheShape()
    {
        ShapeNode blocks = shapeOf("query q { blocks { ...f } } fragment f on Block { start name }")
                .fields().get(0);
        assertEquals(List.of("start", "name"), blocks.fields().stream().map(ShapeNode::key).toList());
    }

    /**
     * A typed classification is reached through an inline fragment on the {@code Classification}
     * interface — the real dhbw "Übersicht" view does exactly this to print firstname/surname. Its
     * fields must resolve against the fragment's type condition, not the interface.
     */
    @Test
    void inlineFragmentFieldsResolveAgainstTheirTypeCondition()
    {
        ShapeNode classification = shapeOf(
                "query q { blocks { persons { classification { ... on PersonClassification { surname firstname } } } } }")
                .fields().get(0).fields().get(0).fields().get(0);
        assertEquals(List.of("surname", "firstname"),
                classification.fields().stream().map(ShapeNode::key).toList());
    }

    @Test
    void fieldsOfTheInterfaceItselfStillResolve()
    {
        ShapeNode classification = shapeOf(
                "query q { blocks { persons { classification { id ... on RoomClassification { roomNumber } } } } }")
                .fields().get(0).fields().get(0).fields().get(0);
        assertEquals(List.of("id", "roomNumber"),
                classification.fields().stream().map(ShapeNode::key).toList());
    }

    @Test
    void anUnparseableQueryHasNoShape()
    {
        assertTrue(shapes.shapeOf("query q { blocks {").isEmpty());
    }

    @Test
    void aQueryReferencingAnUnknownFieldStopsDescendingThere()
    {
        ShapeNode root = shapeOf("query q { nosuchfield }");
        assertEquals(List.of(), root.fields());
    }
}
