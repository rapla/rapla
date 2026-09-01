package org.rapla.server.spring.graphql;

import org.junit.jupiter.api.Test;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.storage.impl.EntityStore;

import java.util.List;
import java.util.Locale;

import graphql.schema.idl.SchemaParser;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 058 exempts the rapla-internal {@code user-groups} Category subtree from key
 * migration (group keys stay legacy-validated: hyphens, umlauts). A CATEGORY attribute
 * whose root-category is {@code user-groups} therefore must never be turned into a
 * generated VALUE_LIST enum — the enum name would be built from the non-spec key
 * {@code user-groups} and the generator throws on boot. Found 2026-08-28 on a migrated
 * Rapla 2 dataset where an event type lets the booker pick a user group.
 */
class ClassificationSdlGeneratorUserGroupsTest
{
    @Test
    void categoryAttributeRootedAtUserGroupsFallsBackToIdNotEnum() throws Exception
    {
        EntityStore store = new EntityStore(new EntityResolver()
        {
            @Override public <T extends Entity> T tryResolve(String id, Class<T> entityClass) { return null; }
            @Override public DynamicType getDynamicType(String key) { return null; }
        });

        CategoryImpl superCategory = category(store, Category.SUPER_CATEGORY_REF.getId(), "super");
        CategoryImpl userGroups = category(store, "ug", "user-groups");
        CategoryImpl team = category(store, "team", "team-a");
        superCategory.addCategory(userGroups);
        userGroups.addCategory(team);

        DynamicTypeImpl dt = new DynamicTypeImpl();
        dt.setResolver(store);
        dt.setId("dt10");
        dt.setKey("reservation10");
        dt.setAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        AttributeImpl group = new AttributeImpl(AttributeType.CATEGORY);
        group.setResolver(store);
        group.setId("attr1");
        group.setKey("gruppe");
        group.setConstraint(ConstraintIds.KEY_ROOT_CATEGORY, userGroups);
        dt.addAttribute(group);
        store.put(dt);

        String sdl = ClassificationSdlGenerator.generate(List.of(dt));

        assertFalse(sdl.contains("enum user"), () -> "no enum may be derived from the user-groups subtree:\n" + sdl);
        assertTrue(sdl.contains("gruppe: ID"), () -> "group attribute must be exposed as ID reference:\n" + sdl);
    }

    /**
     * Enum-value descriptions carry the locale-resolved category name. A name with a
     * double quote (real-world: {@code Projektstelle "Hausverschönerung"}) must still
     * yield parseable SDL — GraphQL block strings know no {@code \"} escape, so the
     * closing {@code """"} broke the schema parser on boot (2026-08-28).
     */
    @Test
    void enumValueDescriptionWithQuotesYieldsParseableSdl() throws Exception
    {
        EntityStore store = new EntityStore(new EntityResolver()
        {
            @Override public <T extends Entity> T tryResolve(String id, Class<T> entityClass) { return null; }
            @Override public DynamicType getDynamicType(String key) { return null; }
        });
        CategoryImpl superCategory = category(store, Category.SUPER_CATEGORY_REF.getId(), "super");
        CategoryImpl bereich = category(store, "b", "bereich");
        CategoryImpl c23 = category(store, "c23", "c23");
        c23.getName().setName("de", "Projektstelle \"Hausverschönerung\"");
        superCategory.addCategory(bereich);
        bereich.addCategory(c23);

        DynamicTypeImpl dt = new DynamicTypeImpl();
        dt.setResolver(store);
        dt.setId("dtp");
        dt.setKey("personen");
        dt.setAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON);
        AttributeImpl attr = new AttributeImpl(AttributeType.CATEGORY);
        attr.setResolver(store);
        attr.setId("attr2");
        attr.setKey("bereich");
        attr.getName().setName("de", "Bereich \"intern\"\nzweite Zeile");
        attr.setConstraint(ConstraintIds.KEY_ROOT_CATEGORY, bereich);
        dt.addAttribute(attr);
        store.put(dt);

        String sdl = ClassificationSdlGenerator.generate(List.of(dt), Locale.GERMAN);

        assertTrue(sdl.contains("enum bereichEnum {"), () -> "expected a value-list enum:\n" + sdl);
        assertTrue(sdl.contains("@displayName(value: \"Bereich \\\"intern\\\"\\nzweite Zeile\")"), () -> "attribute display name must be escaped:\n" + sdl);
        assertDoesNotThrow(() -> new SchemaParser().parse(sdl), () -> "generated SDL must parse:\n" + sdl);
    }

    private static CategoryImpl category(EntityStore store, String id, String key)
    {
        CategoryImpl c = new CategoryImpl();
        c.setResolver(store);
        c.setId(id);
        c.setKey(key);
        store.put(c);
        return c;
    }
}
