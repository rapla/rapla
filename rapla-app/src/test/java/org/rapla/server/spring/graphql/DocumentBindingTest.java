package org.rapla.server.spring.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * PRD 111 D2 — the binding is DERIVED from the view, never declared: the {@code @param} whose
 * {@code into} path feeds the {@code id} argument of a by-id root field decides both the URL
 * parameter and the entity kind. Tier-1: pure text in, binding out.
 */
class DocumentBindingTest
{
    /** The real Siegen Leihschein view (docs/leihschein/leihschein-view.graphql), trimmed. */
    private static final String LEIHSCHEIN = """
            query Leihschein($reservationId: ID!) @view(title: "Leihschein")
              @param(name: "reservationId", into: "reservationId", required: true)
            {
              reservation(id: $reservationId) {
                name
                appointments { start end }
              }
            }
            """;

    @Test
    void derivesParamAndKindFromTheByIdRoot()
    {
        DocumentBinding.Binding binding = DocumentBinding.derive(LEIHSCHEIN);
        assertEquals("reservationId", binding.param());
        assertEquals(DocumentBinding.Kind.RESERVATION, binding.kind());
    }

    @Test
    void allocatableRootBindsToAllocatable()
    {
        DocumentBinding.Binding binding = DocumentBinding.derive("""
                query geraetebogen($id: ID!) @view(title: "Gerätebogen")
                  @param(name: "id", into: "id", required: true)
                { allocatable(id: $id) { name } }
                """);
        assertEquals("id", binding.param());
        assertEquals(DocumentBinding.Kind.ALLOCATABLE, binding.kind());
    }

    /** A param feeding a LIST path is not a context binding — PRD 111 D2 (validation error). */
    @Test
    void listValuedParamDoesNotBind()
    {
        assertNull(DocumentBinding.derive("""
                query kalender($filter: ReservationFilter!) @view(title: "Kalender")
                  @param(name: "resource", into: "filter.allocatableIdsIn", required: true)
                { appointmentBlocks(filter: $filter) { name } }
                """));
    }

    /**
     * The sharp case: a BY-ID root is present, but the declared param feeds a list path, so it is
     * not the variable the id argument consumes. Without the `into == variable` rule this would
     * bind `resource` to RESERVATION and hang a loan slip on the wrong parameter.
     */
    @Test
    void byIdRootWithAListParamStillDoesNotBind()
    {
        assertNull(DocumentBinding.derive("""
                query mixed($reservationId: ID!, $filter: ReservationFilter!) @view(title: "Mixed")
                  @param(name: "resource", into: "filter.allocatableIdsIn")
                {
                  reservation(id: $reservationId) { name }
                  strips(filter: $filter) { index }
                }
                """));
    }

    @Test
    void viewWithoutParamDoesNotBind()
    {
        assertNull(DocumentBinding.derive("query x { reservations(filter: {from: \"a\", to: \"b\"}) { id } }"));
    }

    /** A declared param that no by-id root consumes binds to nothing (fail closed). */
    @Test
    void paramNotFeedingAnIdArgumentDoesNotBind()
    {
        assertNull(DocumentBinding.derive("""
                query x($name: String!) @view(title: "X")
                  @param(name: "name", into: "name")
                { reservations(filter: {from: "a", to: "b", nameContains: $name}) { id } }
                """));
    }

    @Test
    void unparseableQueryDoesNotBind()
    {
        assertNull(DocumentBinding.derive("this is not graphql {{{"));
        assertNull(DocumentBinding.derive(null));
    }
}
