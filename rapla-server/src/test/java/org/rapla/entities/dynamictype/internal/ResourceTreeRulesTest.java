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

/**
 * PRD 119 D11 (user rulings 2026-09-15: minimum, own rule, no Swing parity; coordinator: {@code groupPaths}) — one
 * single-level path per value of the type's categorization attribute ({@code categorization=true}), named by the
 * value; no value → no path; a value the caller cannot read is left out. Fixture: the {@code room} type's category
 * attribute {@code belongsto}, marked as categorization by the test.
 */
public class ResourceTreeRulesTest extends FacadeTestSupport
{
    private static final String ROOM_A66 = "c24ce517-4697-4e52-9917-ec000c84563c";

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
}
