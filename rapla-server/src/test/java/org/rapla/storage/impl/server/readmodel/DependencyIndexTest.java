package org.rapla.storage.impl.server.readmodel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.storage.impl.AbstractCachableOperator;
import org.rapla.test.util.FacadeTestSupport;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-2 equivalence tests for {@link DependencyIndex} against the authoritative
 * {@code AbstractCachableOperator.getDependent(...)} graph walk, plus tier-1
 * pure-logic tests of the traversal direction and maintenance hooks.
 *
 * <p>The fixture's belongs-to attributes point at categories (not allocatables),
 * so to exercise a real allocatable→allocatable dependency this test creates a
 * {@code room} (target) and a {@code resource1} ("Teilraum") whose {@code a1}
 * belongs-to attribute references that room, stores both, then asserts the index
 * returns byte-identical dependent id-sets to {@code operator.getDependent}.
 */
class DependencyIndexTest extends FacadeTestSupport
{
    private User actingUser;
    private AbstractCachableOperator op;

    @BeforeEach
    void setUp() throws Exception
    {
        for (User u : facade.getUsers())
        {
            if (u.isAdmin()) { actingUser = u; break; }
        }
        assertNotNull(actingUser, "fixture must include an admin user");
        op = (AbstractCachableOperator) operator;
    }

    /** Build an index over every allocatable currently in the store. */
    private DependencyIndex buildIndexFromStore() throws Exception
    {
        DependencyIndex index = new DependencyIndex();
        for (Allocatable a : facade.getAllocatables())
        {
            index.put(a);
        }
        return index;
    }

    private static Set<String> idsOf(Collection<Allocatable> allocatables)
    {
        Set<String> ids = new LinkedHashSet<>();
        for (Allocatable a : allocatables)
        {
            ids.add(a.getReference().getId());
        }
        return ids;
    }

    /** Assert the index's expand(id) equals operator.getDependent(singleton) for every allocatable. */
    private void assertEquivalentForAll(DependencyIndex index) throws Exception
    {
        for (Allocatable a : facade.getAllocatables())
        {
            String id = a.getReference().getId();
            Collection<Allocatable> expected = op.getDependent(Collections.singletonList(a));
            Set<String> expectedIds = idsOf(expected);
            Set<String> actualIds = index.expand(Collections.singletonList(id));
            assertEquals(expectedIds, actualIds,
                    "DependencyIndex.expand must equal operator.getDependent for allocatable " + id);
        }
    }

    @Test
    @DisplayName("expand equals operator.getDependent for every fixture allocatable")
    void equivalenceOnFixture() throws Exception
    {
        DependencyIndex index = buildIndexFromStore();
        assertEquivalentForAll(index);
    }

    @Test
    @DisplayName("expand equals operator.getDependent with a real belongs-to allocatable relation")
    void equivalenceWithBelongsToRelation() throws Exception
    {
        // Target: a plain room.
        DynamicType roomType = facade.getDynamicType("room");
        assertNotNull(roomType, "fixture must define a 'room' type");
        Classification roomClass = roomType.newClassification();
        if (roomClass.getType().getAttribute("name") != null)
        {
            roomClass.setValue("name", "DEP-TARGET-ROOM");
        }
        Allocatable room = facade.newAllocatable(roomClass, actingUser);

        // Dependent: a Teilraum (resource1) whose a1 belongs-to attribute references the room.
        DynamicType teilraumType = facade.getDynamicType("resource1");
        assertNotNull(teilraumType, "fixture must define a 'resource1' (Teilraum) type");
        Attribute belongsTo = teilraumType.getAttribute("a1");
        assertNotNull(belongsTo, "resource1 must have the a1 belongs-to attribute");
        Classification subClass = teilraumType.newClassification();
        if (subClass.getType().getAttribute("name") != null)
        {
            subClass.setValue("name", "DEP-PART-OF-ROOM");
        }
        subClass.setValueForAttribute(belongsTo, room);
        Allocatable teilraum = facade.newAllocatable(subClass, actingUser);

        facade.storeObjects(new Allocatable[] { room, teilraum });

        // Sanity: the relation actually exists — the room's dependents must include the Teilraum.
        Collection<Allocatable> roomDependents = op.getDependent(Collections.singletonList(room));
        Set<String> roomDependentIds = idsOf(roomDependents);
        assertTrue(roomDependentIds.contains(teilraum.getReference().getId()),
                "operator.getDependent(room) must include the dependent Teilraum (relation precondition)");
        assertTrue(roomDependentIds.size() >= 2,
                "room must expand to at least itself + the Teilraum");

        // Equivalence across the whole store, including the new related pair.
        DependencyIndex index = buildIndexFromStore();
        assertEquivalentForAll(index);

        // And specifically the two new ids resolve identically.
        String roomId = room.getReference().getId();
        assertEquals(idsOf(op.getDependent(Collections.singletonList(room))),
                index.expand(Collections.singletonList(roomId)),
                "index.expand(room) must equal operator.getDependent(room)");
    }

    @Test
    @DisplayName("multi-id expand equals operator.getDependent over a collection")
    void equivalenceForMultiInput() throws Exception
    {
        Allocatable[] all = facade.getAllocatables();
        assertTrue(all.length >= 2, "fixture must have >= 2 allocatables");
        Collection<Allocatable> input = java.util.Arrays.asList(all[0], all[1]);
        Set<String> inputIds = idsOf(input);

        DependencyIndex index = buildIndexFromStore();
        Set<String> expected = idsOf(op.getDependent(input));
        Set<String> actual = index.expand(inputIds);
        assertEquals(expected, actual, "multi-id expand must equal operator.getDependent");
    }

    // ---------- tier-1 pure-logic: traversal direction (mirrors fillDependent) ----------

    @Test
    @DisplayName("after remove, the index drops the node and its back-edges (maintenance hook)")
    void removeDropsNodeAndBackEdges() throws Exception
    {
        DynamicType roomType = facade.getDynamicType("room");
        Classification roomClass = roomType.newClassification();
        Allocatable room = facade.newAllocatable(roomClass, actingUser);

        DynamicType teilraumType = facade.getDynamicType("resource1");
        Attribute belongsTo = teilraumType.getAttribute("a1");
        Classification subClass = teilraumType.newClassification();
        subClass.setValueForAttribute(belongsTo, room);
        Allocatable teilraum = facade.newAllocatable(subClass, actingUser);

        DependencyIndex index = new DependencyIndex();
        index.put(room);
        index.put(teilraum);

        String roomId = room.getReference().getId();
        String teilraumId = teilraum.getReference().getId();

        // Before remove: room expands to include the Teilraum.
        assertTrue(index.expand(roomId).contains(teilraumId),
                "before remove, room must expand to the dependent Teilraum");

        // Remove the Teilraum (the dependent). Its back-edge on the room must vanish.
        index.remove(new ReferenceInfo<>(teilraumId, Allocatable.class));

        Set<String> afterRemove = index.expand(roomId);
        assertEquals(Collections.singleton(roomId), afterRemove,
                "after removing the dependent, room expands only to itself");
    }

    @Test
    @DisplayName("unknown id expands to itself (fillDependent fallback)")
    void unknownIdExpandsToItself()
    {
        DependencyIndex index = new DependencyIndex();
        Set<String> result = index.expand("does-not-exist");
        assertEquals(Collections.singleton("does-not-exist"), result,
                "an id with no node must expand to exactly itself");
    }
}
