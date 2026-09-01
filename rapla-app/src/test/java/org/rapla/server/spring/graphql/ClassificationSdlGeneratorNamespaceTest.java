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

import graphql.schema.idl.SchemaParser;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generated GraphQL names live in ONE global type namespace. Names derived from
 * admin keys must not collide with each other: a DynamicType key and a root-category
 * key both named {@code intern} produced {@code internWhere} twice and crashed the
 * boot with {@code SchemaProblem} (seen on a migrated Rapla 2 dataset, 2026-08-29). Enum-derived
 * names therefore carry their own suffix family ({@code Enum}, {@code EnumWhere},
 * {@code EnumListWhere}) disjoint from the DynamicType family ({@code Classification},
 * {@code Where}, {@code RefWhere}).
 */
class ClassificationSdlGeneratorNamespaceTest
{
    @Test
    void dynamicTypeKeyAndRootCategoryKeyMayBeEqual() throws Exception
    {
        EntityStore store = store();
        CategoryImpl superCategory = category(store, Category.SUPER_CATEGORY_REF.getId(), "super");
        CategoryImpl intern = category(store, "cat-intern", "intern");
        CategoryImpl leaf = category(store, "cat-leaf", "team");
        superCategory.addCategory(intern);
        intern.addCategory(leaf);

        DynamicTypeImpl dt = reservationType(store, "dt-intern", "intern");
        AttributeImpl name = categoryAttribute(store, "attr-name", "name", intern);
        dt.addAttribute(name);
        store.put(dt);

        String sdl = ClassificationSdlGenerator.generate(List.of(dt));

        assertDoesNotThrow(() -> new SchemaParser().parse(sdl), () -> "generated SDL must parse:\n" + sdl);
        assertTrue(sdl.contains("enum internEnum {"), () -> "enum gets the Enum suffix:\n" + sdl);
        assertTrue(sdl.contains("input internEnumWhere {"), () -> "enum where gets the EnumWhere suffix:\n" + sdl);
        assertTrue(sdl.contains("input internEnumListWhere {"), () -> "enum list where gets the EnumListWhere suffix:\n" + sdl);
        assertTrue(sdl.contains("input internWhere {"), () -> "type where keeps the Where suffix:\n" + sdl);
        assertTrue(sdl.contains("  name: internEnum"), () -> "classification field targets the suffixed enum:\n" + sdl);
        assertTrue(sdl.contains("  name: internEnumWhere"), () -> "where field targets the suffixed enum where:\n" + sdl);
    }

    /** Path join with {@code _} is ambiguous ({@code a/b} vs key {@code a_b}); the generator must degrade, not crash. */
    @Test
    void ambiguousEnumPathDoesNotBreakTheSchema() throws Exception
    {
        EntityStore store = store();
        CategoryImpl superCategory = category(store, Category.SUPER_CATEGORY_REF.getId(), "super");
        CategoryImpl a = category(store, "cat-a", "a");
        CategoryImpl b = category(store, "cat-b", "b");
        CategoryImpl bLeaf = category(store, "cat-b-leaf", "x");
        CategoryImpl ab = category(store, "cat-ab", "a_b");
        CategoryImpl abLeaf = category(store, "cat-ab-leaf", "y");
        superCategory.addCategory(a);
        superCategory.addCategory(ab);
        a.addCategory(b);
        b.addCategory(bLeaf);
        ab.addCategory(abLeaf);

        DynamicTypeImpl dt = reservationType(store, "dt-r", "r");
        dt.addAttribute(categoryAttribute(store, "attr-1", "first", b));
        dt.addAttribute(categoryAttribute(store, "attr-2", "second", ab));
        store.put(dt);

        String sdl = ClassificationSdlGenerator.generate(List.of(dt));

        assertDoesNotThrow(() -> new SchemaParser().parse(sdl), () -> "generated SDL must parse:\n" + sdl);
        assertEquals(1, sdl.split("enum a_bEnum \\{", -1).length - 1, () -> "exactly one enum for the ambiguous name:\n" + sdl);
        assertTrue(sdl.contains("  first: a_bEnum"), () -> "first root keeps the enum:\n" + sdl);
        assertTrue(sdl.contains("  second: Category"), () -> "colliding root falls back to Category:\n" + sdl);
    }

    private static EntityStore store()
    {
        return new EntityStore(new EntityResolver()
        {
            @Override public <T extends Entity> T tryResolve(String id, Class<T> entityClass) { return null; }
            @Override public DynamicType getDynamicType(String key) { return null; }
        });
    }

    private static DynamicTypeImpl reservationType(EntityStore store, String id, String key) throws Exception
    {
        DynamicTypeImpl dt = new DynamicTypeImpl();
        dt.setResolver(store);
        dt.setId(id);
        dt.setKey(key);
        dt.setAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        return dt;
    }

    private static AttributeImpl categoryAttribute(EntityStore store, String id, String key, Category root)
    {
        AttributeImpl attr = new AttributeImpl(AttributeType.CATEGORY);
        attr.setResolver(store);
        attr.setId(id);
        attr.setKey(key);
        attr.setConstraint(ConstraintIds.KEY_ROOT_CATEGORY, root);
        return attr;
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
