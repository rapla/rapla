package org.rapla.storage.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.RaplaResources;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.framework.RaplaException;
import org.rapla.test.util.FacadeTestSupport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PRD 120 Phase 2 — one store-time check over both kinds (D3/D4): walking up over normalised parents (a belongsTo
 * value is a parent of the holder, a package value is a child of it) from every stored resource must never return to
 * it. Mixed cycles were accepted while belongsTo and packages were checked separately.
 */
class ResourceCycleCheckTest extends FacadeTestSupport
{
    private static final String ROOM_A66_1 = "rdd6b473-7c77-4344-a73d-1f27008341cb";
    private static final String DOZ_GRUPPE = "r9b69d90-46a0-41bb-94fa-82079b424c03";

    private final RaplaResources i18n = new RaplaResources(new ServerBundleManager());
    private DynamicType nodeType;
    private User admin;

    @BeforeEach
    void nodeTypeWithBothKinds() throws Exception
    {
        admin = facade.getUsers()[0];
        for (User u : facade.getUsers())
        {
            if (u.isAdmin()) admin = u;
        }
        DynamicType type = facade.newDynamicType(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
        type.setKey("prd120node");
        type.getName().setName("en", "prd120node");
        type.addAttribute(reference("parent", false, true, false));
        type.addAttribute(reference("children", true, false, true));
        facade.store(type);
        nodeType = facade.getDynamicType("prd120node");
    }

    // D3: "A belongsTo B" and "B packages A" both make B the parent of A (one edge); a mixed cycle needs an edge back
    @Test
    void aMixedCycleInOneStoreIsRejected() throws Exception
    {
        Allocatable a = node("A");
        Allocatable b = node("B");
        Allocatable c = node("C");
        belongsTo(a, b);
        belongsTo(b, c);
        packages(a, c);
        assertCycle(() -> facade.storeObjects(new Allocatable[] { a, b, c }));
    }

    @Test
    void aMixedCycleClosedByTheSecondStoreIsRejected() throws Exception
    {
        Allocatable a = node("A");
        Allocatable b = node("B");
        Allocatable c = node("C");
        belongsTo(a, b);
        belongsTo(b, c);
        facade.storeObjects(new Allocatable[] { a, b, c });

        Allocatable editA = facade.edit(stored(a));
        packages(editA, stored(c));
        assertCycle(() -> facade.store(editA));
    }

    @Test
    void belongingToAndPackagingTheSameResourceIsRejected() throws Exception
    {
        Allocatable a = node("A");
        Allocatable b = node("B");
        belongsTo(a, b);
        packages(a, b);
        assertCycle(() -> facade.storeObjects(new Allocatable[] { a, b }));
    }

    @Test
    void theSameEdgeInBothKindsStores() throws Exception
    {
        Allocatable room = node("room");
        Allocatable building = node("building");
        belongsTo(room, building);
        packages(building, room);
        assertDoesNotThrow(() -> facade.storeObjects(new Allocatable[] { room, building }));
    }

    @Test
    void aCourseInTwoGroupsStores() throws Exception
    {
        Allocatable course = node("course");
        Allocatable group1 = node("group1");
        Allocatable group2 = node("group2");
        Allocatable faculty = node("faculty");
        packages(group1, course);
        packages(group2, course);
        belongsTo(group1, faculty);
        belongsTo(group2, faculty);
        assertDoesNotThrow(() -> facade.storeObjects(new Allocatable[] { course, group1, group2, faculty }));
    }

    @Test
    void pureCyclesOfOneKindAreRejected() throws Exception
    {
        Allocatable a = node("A");
        Allocatable b = node("B");
        Allocatable c = node("C");
        belongsTo(a, b);
        belongsTo(b, c);
        facade.storeObjects(new Allocatable[] { a, b, c });
        Allocatable editC = facade.edit(stored(c));
        belongsTo(editC, stored(a));
        assertCycle(() -> facade.store(editC));

        Allocatable x = node("X");
        Allocatable y = node("Y");
        packages(x, y);
        packages(y, x);
        assertCycle(() -> facade.storeObjects(new Allocatable[] { x, y }));
    }

    @Test
    void selfReferencesKeepTheirMessages() throws Exception
    {
        Allocatable a = node("selfA");
        facade.store(a);
        Allocatable editA = facade.edit(stored(a));
        belongsTo(editA, stored(a));
        RaplaException belongs = assertThrows(RaplaException.class, () -> facade.store(editA));
        assertEquals(i18n.format("error.belongsToCantReferToSelf", stored(a).getName(i18n.getLocale())), belongs.getMessage());

        Allocatable editA2 = facade.edit(stored(a));
        packages(editA2, stored(a));
        RaplaException packs = assertThrows(RaplaException.class, () -> facade.store(editA2));
        assertEquals(i18n.format("error.packageCantReferToSelf", stored(a).getName(i18n.getLocale())), packs.getMessage());
    }

    @Test
    void fixtureHierarchiesStillStore() throws Exception
    {
        assertDoesNotThrow(() -> facade.store(facade.edit(operator.resolve(ROOM_A66_1, Allocatable.class))));
        assertDoesNotThrow(() -> facade.store(facade.edit(operator.resolve(DOZ_GRUPPE, Allocatable.class))));
    }

    /** D6: a cycle that came in without a store event neither loops the walk nor blocks storing an unrelated resource. */
    @Test
    void aPreExistingCycleDoesNotBlockAnUnrelatedStore() throws Exception
    {
        Allocatable[] cycle = injectCycle("legacy");

        Allocatable c = node("unrelated");
        belongsTo(c, stored(cycle[0]));
        assertDoesNotThrow(() -> facade.store(c));
    }

    /** PRD 120 Phase 3 (idIn): the upward walk follows both kinds and several parents per node. */
    @Test
    void selfOrAncestorFollowsBothKinds() throws Exception
    {
        Allocatable course = node("course");
        Allocatable group = node("group");
        Allocatable faculty = node("faculty");
        Allocatable other = node("other");
        packages(group, course);
        belongsTo(group, faculty);
        facade.storeObjects(new Allocatable[] { course, group, faculty, other });

        assertEquals(true, operator.isSelfOrAncestorIn(stored(course), List.of(course.getId())));
        assertEquals(true, operator.isSelfOrAncestorIn(stored(course), List.of(group.getId())));
        assertEquals(true, operator.isSelfOrAncestorIn(stored(course), List.of(faculty.getId())));
        assertEquals(false, operator.isSelfOrAncestorIn(stored(course), List.of(other.getId())));
        assertEquals(false, operator.isSelfOrAncestorIn(stored(faculty), List.of(course.getId())));
    }

    /** D6: a pre-existing cycle must terminate the walk (no counter as the guard). */
    @Test
    void selfOrAncestorTerminatesOnAPreExistingCycle() throws Exception
    {
        Allocatable[] cycle = injectCycle("loop");
        Allocatable other = node("loopOther");
        facade.store(other);

        assertEquals(false, operator.isSelfOrAncestorIn(stored(cycle[0]), List.of(other.getId())));
        assertEquals(true, operator.isSelfOrAncestorIn(stored(cycle[0]), List.of(cycle[2].getId())));
    }

    /**
     * Puts a belongsTo cycle a → b → c → a into the cache without a store event (D6: data that came in by load or
     * import). Three nodes, because the graph keeps one connection type per neighbour pair and cannot hold a 2-cycle.
     */
    private Allocatable[] injectCycle(String prefix) throws Exception
    {
        Allocatable a = node(prefix + "A");
        Allocatable b = node(prefix + "B");
        Allocatable c = node(prefix + "C");
        belongsTo(a, b);
        belongsTo(b, c);
        facade.storeObjects(new Allocatable[] { a, b, c });
        Allocatable closing = facade.edit(stored(c));
        belongsTo(closing, stored(a));
        operator.cache.put(closing);
        return new Allocatable[] { a, b, c };
    }

    private void assertCycle(org.junit.jupiter.api.function.Executable store)
    {
        RaplaException e = assertThrows(RaplaException.class, store);
        assertEquals(true, e.getMessage().startsWith(i18n.format("error.belongsToCycle", "").trim()), e.getMessage());
    }

    private Attribute reference(String key, boolean multi, boolean belongsTo, boolean packages) throws Exception
    {
        Attribute attribute = facade.newAttribute(AttributeType.ALLOCATABLE);
        attribute.setKey(key);
        attribute.getName().setName("en", key);
        attribute.setConstraint(ConstraintIds.KEY_MULTI_SELECT, multi);
        attribute.setConstraint(ConstraintIds.KEY_BELONGS_TO, belongsTo);
        attribute.setConstraint(ConstraintIds.KEY_PACKAGE, packages);
        return attribute;
    }

    private Allocatable node(String name) throws Exception
    {
        Allocatable a = facade.newAllocatable(nodeType.newClassification(), admin);
        a.getClassification().setValue("name", name);
        return a;
    }

    private Allocatable stored(Allocatable a) throws Exception
    {
        return operator.resolve(a.getId(), Allocatable.class);
    }

    private void belongsTo(Allocatable holder, Allocatable parent)
    {
        holder.getClassification().setValues(nodeType.getAttribute("parent"), List.of(parent));
    }

    private void packages(Allocatable holder, Allocatable child)
    {
        List<Object> values = new java.util.ArrayList<>(holder.getClassification().getValues(nodeType.getAttribute("children")));
        values.add(child);
        holder.getClassification().setValues(nodeType.getAttribute("children"), values);
    }
}
