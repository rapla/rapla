package org.rapla.storage;

import org.junit.jupiter.api.Test;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.test.util.FacadeTestSupport;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 120 Phase 1 — direct parents and children of a resource, read from the graph LocalCache already keeps (D2),
 * with direction normalised (D3): a belongsTo value is a parent of the holder, a packages value is a child of it.
 */
class ResourceNeighboursTest extends FacadeTestSupport
{
    // testdefault.xml: Room A66.1 belongsTo Room A66 (resource1.a1); DozGruppe packages Burns Monty (resource2.a1)
    private static final String ROOM_A66 = "c24ce517-4697-4e52-9917-ec000c84563c";
    private static final String ROOM_A66_1 = "rdd6b473-7c77-4344-a73d-1f27008341cb";
    private static final String DOZ_GRUPPE = "r9b69d90-46a0-41bb-94fa-82079b424c03";
    private static final String MONTY = "f92e9a11-c342-4413-a924-81eee17ccf92";

    @Test
    void aBelongsToValueIsTheParentOfItsHolder() throws Exception
    {
        assertEquals(List.of(ROOM_A66), ids(operator.getParents(resource(ROOM_A66_1))));
        assertTrue(ids(operator.getChildren(resource(ROOM_A66))).contains(ROOM_A66_1));
    }

    @Test
    void aPackagesValueIsAChildOfItsHolder() throws Exception
    {
        assertEquals(List.of(MONTY), ids(operator.getChildren(resource(DOZ_GRUPPE))));
        assertTrue(ids(operator.getParents(resource(MONTY))).contains(DOZ_GRUPPE));
    }

    @Test
    void neighboursFollowAnEditedValue() throws Exception
    {
        Allocatable part = facade.edit(resource(ROOM_A66_1));
        Attribute belongsTo = ((DynamicTypeImpl) part.getClassification().getType()).getBelongsToAttribute();
        part.getClassification().setValues(belongsTo, List.of());
        facade.store(part);

        assertTrue(operator.getParents(resource(ROOM_A66_1)).isEmpty());
        assertFalse(ids(operator.getChildren(resource(ROOM_A66))).contains(ROOM_A66_1));
    }

    @Test
    void aResourceWithoutEdgesHasNoNeighbours() throws Exception
    {
        assertTrue(operator.getParents(resource(ROOM_A66)).isEmpty());
        assertTrue(operator.getChildren(resource(ROOM_A66_1)).isEmpty());
    }

    private Allocatable resource(String id) throws Exception
    {
        return operator.resolve(id, Allocatable.class);
    }

    private static List<String> ids(Collection<Allocatable> allocatables)
    {
        return allocatables.stream().map(Allocatable::getId).collect(Collectors.toList());
    }
}
