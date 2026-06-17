package org.rapla.storage.impl.server;

import org.junit.jupiter.api.Test;
import org.rapla.components.util.Tools;
import org.rapla.entities.Category;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.framework.RaplaException;
import org.rapla.test.util.FacadeTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 058 Phase 2 — tier-2 round-trip for {@link GraphqlKeyMigration}.
 *
 * <p>Uses {@code testdefault.xml} which already contains non-spec category
 * keys under the {@code department} subtree ({@code channel-6},
 * {@code elementary-springfield}, {@code springfield-powerplant},
 * {@code testdepartment}). After {@code migrateGraphqlKeysIfNeeded()} runs,
 * these should be renamed to {@code channel_6}, {@code elementary_springfield},
 * etc. — the {@code user-groups} subtree is left untouched (it's rapla-internal
 * and rapla's own {@link org.rapla.entities.domain.Permission} constants
 * reference its keys by string).
 */
class GraphqlKeyMigrationTest extends FacadeTestSupport
{
    @Test
    void migrationRenamesNonSpecCategoryKeysUnderDepartment() throws Exception
    {
        // Pre-condition: fixture has hyphen-bearing categories under department.
        Category department = operator.getSuperCategory().getCategory("department");
        assertNotNull(department, "fixture must contain 'department' root category");
        assertNotNull(department.getCategory("channel-6"),
                "fixture must contain non-spec 'channel-6' under department");

        // Run migration
        operator.migrateGraphqlKeysIfNeeded();

        // 'channel-6' renamed to 'channel_6' (hyphen → underscore)
        department = operator.getSuperCategory().getCategory("department");
        assertNull(department.getCategory("channel-6"), "old hyphen key must be gone");
        assertNotNull(department.getCategory("channel_6"),
                "spec-compliant key 'channel_6' must exist after migration");

        // Other hyphen keys also renamed
        assertNotNull(department.getCategory("elementary_springfield"));
        assertNotNull(department.getCategory("springfield_powerplant"));
    }

    @Test
    void migrationSkipsUserGroupsSubtree() throws Exception
    {
        operator.migrateGraphqlKeysIfNeeded();

        Category userGroups = operator.getSuperCategory().getCategory("user-groups");
        assertNotNull(userGroups, "user-groups subtree must survive intact");
        // Permission groups with hyphens stay as-is — rapla.entities.Permission
        // constants hardcode these (modify-preferences, create-events, …).
        assertNotNull(userGroups.getCategory("modify-preferences"));
        assertNotNull(userGroups.getCategory("read-events-from-others"));
        assertNotNull(userGroups.getCategory("create-events"));
        assertNotNull(userGroups.getCategory("my-group"));
        // user-groups itself must stay (rapla-internal key)
        assertEquals("user-groups", userGroups.getKey());
    }

    @Test
    void cacheIsSpecCompliantAfterMigration() throws Exception
    {
        operator.migrateGraphqlKeysIfNeeded();

        // Walk every non-internal DT, its Attributes, and non-user-groups
        // Categories — must be spec-compliant. Rapla-internal types
        // (rapla:period, rapla:template, …) use `rapla:*` keys that
        // are exempt from the spec (production code references them
        // by string and they never reach the GraphQL SDL).
        for (DynamicType dt : operator.getDynamicTypes())
        {
            String classKind = dt.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
            if (DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RAPLATYPE.equals(classKind)) continue;
            // rapla-internal DTs use `rapla:*` keys (e.g. rapla:anonymousEvent) that
            // production's GraphqlKeyMigration.isRaplaInternal exempts by key prefix,
            // not by classification kind — mirror that here.
            if (dt.getKey() != null && dt.getKey().startsWith("rapla:")) continue;
            assertTrue(Tools.isSpecCompliant(dt.getKey()), "DT key non-spec: " + dt.getKey());
            for (Attribute a : dt.getAttributes())
            {
                assertTrue(Tools.isSpecCompliant(a.getKey()),
                        "Attr non-spec: " + dt.getKey() + "." + a.getKey());
            }
        }
        // Non-user-groups categories
        walkAndAssertSpec(operator.getSuperCategory(), operator.getSuperCategory());
    }

    private void walkAndAssertSpec(Category superCategory, Category parent)
    {
        for (Category c : parent.getCategories())
        {
            if ("user-groups".equals(c.getKey()) && parent == superCategory) continue;
            assertTrue(Tools.isSpecCompliant(c.getKey()),
                    "Category non-spec: " + c.getKey());
            walkAndAssertSpec(superCategory, c);
        }
    }

    @Test
    void migrationIsIdempotent() throws Exception
    {
        operator.migrateGraphqlKeysIfNeeded();
        // Second run must be a fast no-op via marker — no exceptions, no renames.
        Category before = operator.getSuperCategory().getCategory("department").getCategory("channel_6");
        assertNotNull(before);
        operator.migrateGraphqlKeysIfNeeded();
        Category after = operator.getSuperCategory().getCategory("department").getCategory("channel_6");
        assertNotNull(after);
        assertEquals(before.getId(), after.getId(), "second run must not re-create the category");
    }

    @Test
    void markerWrittenAfterMigration() throws Exception
    {
        operator.migrateGraphqlKeysIfNeeded();
        // The marker preference must now be set on the system preferences.
        assertTrue(GraphqlKeyMigration.markerSet(operator),
                "marker preference must be present after migration");
    }

    @Test
    void nameformatAnnotationFollowsAttributeRenameViaSetKey() throws Exception
    {
        // Edit a DT, rename an attribute, store. Rapla's AttributeImpl.setKey
        // → parent.keyChanged → updateFormatString chain should rewrite the
        // nameformat annotation automatically — no manual rewrite needed.
        // This is the property the now-deleted GraphqlKeyMigration regex
        // walker was trying to enforce; here we prove the existing mechanism
        // already provides it.
        org.rapla.entities.dynamictype.DynamicType room = operator.getDynamicType("room");
        assertNotNull(room, "fixture must contain 'room'");
        assertEquals("{name}", room.getAnnotation(org.rapla.entities.dynamictype.DynamicTypeAnnotations.KEY_NAME_FORMAT));

        org.rapla.entities.dynamictype.DynamicType editable =
                (org.rapla.entities.dynamictype.DynamicType) operator.editObject((org.rapla.entities.Entity) room, null);
        editable.getAttribute("name").setKey("name_renamed");
        operator.storeAndRemove(java.util.Collections.singletonList((org.rapla.entities.Entity) editable),
                java.util.Collections.emptyList(), null);

        org.rapla.entities.dynamictype.DynamicType refreshed = operator.getDynamicType("room");
        assertEquals("{name_renamed}", refreshed.getAnnotation(org.rapla.entities.dynamictype.DynamicTypeAnnotations.KEY_NAME_FORMAT),
                "nameformat annotation must auto-update via AttributeFunction.getRepresentation");
    }

}
