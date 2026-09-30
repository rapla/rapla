package org.rapla.entities.dynamictype.internal;

import org.junit.jupiter.api.Test;
import org.rapla.entities.Category;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.server.internal.ResourceTreeRules;
import org.rapla.test.util.FacadeTestSupport;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * PRD 119 D11 (user rulings 2026-09-15: minimum, own rule, no Swing parity; coordinator: {@code groupPaths}) — one
 * single-level path per value of the type's categorization attribute ({@code categorization=true}), named by the
 * value; no value → no path; a value the caller cannot read is left out. Fixture: the {@code room} type's category
 * attribute {@code belongsto}, marked as categorization by the test.
 */
public class ResourceTreeRulesTest extends FacadeTestSupport
{
    private static final String ROOM_A66 = "c24ce517-4697-4e52-9917-ec000c84563c";
    private static final String ROOM_A66_1 = "rdd6b473-7c77-4344-a73d-1f27008341cb";
    private static final String DOZ_GRUPPE = "r9b69d90-46a0-41bb-94fa-82079b424c03";
    private static final String MONTY = "f92e9a11-c342-4413-a924-81eee17ccf92";

    private Allocatable roomA66() throws Exception
    {
        return operator.resolve(ROOM_A66, Allocatable.class);
    }

    private Category department(String key)
    {
        return operator.getSuperCategory().getCategory("department").getCategory(key);
    }

    private String name(String departmentKey)
    {
        return department(departmentKey).getName(Locale.ENGLISH);
    }

    private Attribute categorize(boolean multiSelect) throws Exception
    {
        DynamicType room = facade.edit(operator.getDynamicType("room"));
        Attribute attribute = room.getAttribute("belongsto");
        attribute.setAnnotation(AttributeAnnotations.KEY_CATEGORIZATION, "true");
        if (multiSelect)
        {
            attribute.setConstraint(ConstraintIds.KEY_MULTI_SELECT, true);
        }
        facade.store(room);
        return operator.getDynamicType("room").getAttribute("belongsto");
    }

    private void setValues(Attribute attribute, List<Category> values) throws Exception
    {
        Allocatable room = facade.edit(roomA66());
        room.getClassification().setValues(attribute, values);
        facade.store(room);
    }

    private List<List<String>> groupPaths() throws Exception
    {
        return ResourceTreeRules.groupPaths(roomA66().getClassification(), Locale.ENGLISH, value -> true);
    }

    @Test
    public void withoutACategorizationAttributeThereIsNoPath() throws Exception
    {
        assertEquals(List.of(), groupPaths());
    }

    @Test
    public void theCategorizationValueIsASingleLevelPath() throws Exception
    {
        categorize(false);
        assertEquals(List.of(List.of(name("springfield-powerplant"))), groupPaths());
    }

    @Test
    public void everyValueOfAMultiValuedCategorizationIsItsOwnPath() throws Exception
    {
        Attribute attribute = categorize(true);
        setValues(attribute, List.of(department("springfield-powerplant"), department("channel-6")));
        assertEquals(List.of(List.of(name("springfield-powerplant")), List.of(name("channel-6"))), groupPaths());
    }

    @Test
    public void aResourceWithoutAValueHasNoPath() throws Exception
    {
        Attribute attribute = categorize(false);
        setValues(attribute, List.of());
        assertEquals(List.of(), groupPaths());
    }

    /** Review S2 — a value without any name gives no empty group label. */
    @Test
    public void aValueWithoutANameIsLeftOut() throws Exception
    {
        Attribute attribute = categorize(true);
        Category nameless = facade.edit(department("springfield-powerplant"));
        for (String language : List.copyOf(nameless.getName().getAvailableLanguages()))
        {
            nameless.getName().setName(language, "");
        }
        nameless.setKey("springfield_powerplant");
        facade.store(nameless);
        setValues(attribute, List.of(department("springfield_powerplant"), department("channel-6")));
        assertEquals(List.of(List.of(name("channel-6"))), groupPaths());
    }

    @Test
    public void aValueTheCallerCannotReadIsLeftOut() throws Exception
    {
        Attribute attribute = categorize(true);
        Category hidden = department("springfield-powerplant");
        setValues(attribute, List.of(hidden, department("channel-6")));
        assertEquals(List.of(List.of(name("channel-6"))),
                ResourceTreeRules.groupPaths(roomA66().getClassification(), Locale.ENGLISH, value -> !value.equals(hidden)));
    }

    /** PRD 119 S2 — the parent via the type's belongsTo attribute (fixture: resource1.a1, Room A66.1 → Room A66). */
    @Test
    public void belongsToIsTheParentResourceId() throws Exception
    {
        Allocatable part = operator.resolve(ROOM_A66_1, Allocatable.class);
        assertEquals(ROOM_A66, ResourceTreeRules.belongsTo(part.getClassification(), value -> true));
    }

    @Test
    public void withoutABelongsToAttributeThereIsNoParent() throws Exception
    {
        assertNull(ResourceTreeRules.belongsTo(roomA66().getClassification(), value -> true));
    }

    @Test
    public void aParentTheCallerCannotReadIsNoParent() throws Exception
    {
        Allocatable part = operator.resolve(ROOM_A66_1, Allocatable.class);
        assertNull(ResourceTreeRules.belongsTo(part.getClassification(), value -> false));
    }

    /** PRD 119 S2 — the reverse direction: the resources the type's packages attribute lists (fixture: resource2.a1, DozGruppe → Burns Monty). */
    @Test
    public void packageIdsAreThePackagedResources() throws Exception
    {
        Allocatable group = operator.resolve(DOZ_GRUPPE, Allocatable.class);
        assertEquals(List.of(MONTY), ResourceTreeRules.packageIds(group.getClassification(), value -> true));
    }

    @Test
    public void withoutAPackagesAttributeThereAreNoPackageIds() throws Exception
    {
        assertEquals(List.of(), ResourceTreeRules.packageIds(roomA66().getClassification(), value -> true));
    }

    @Test
    public void aPackagedResourceTheCallerCannotReadIsLeftOut() throws Exception
    {
        Allocatable group = operator.resolve(DOZ_GRUPPE, Allocatable.class);
        assertEquals(List.of(), ResourceTreeRules.packageIds(group.getClassification(), value -> false));
    }
}
