package org.rapla.test.util;

import org.junit.jupiter.api.Test;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.DynamicType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates that {@link FacadeTestSupport} produces a connected facade backed
 * by {@code testdefault.xml}, and exercises a few read paths so the wiring
 * is end-to-end exercised, not just instantiated.
 *
 * <p>This is also the demo test referenced in PRD 017 Phase 1.3 / 1.4 — its
 * wall time vs. the comparable {@code @SpringBootTest} acceptance tests in
 * rapla-app is the speed delta the PRD measures.
 */
class FacadeTestSupportTest extends FacadeTestSupport
{
    @Test
    void facadeIsConnected()
    {
        assertNotNull(facade, "facade must be wired");
        assertTrue(operator.isConnected(), "operator must be connected after setup");
    }

    @Test
    void categoriesLoadedFromFixture() throws Exception
    {
        Category superCategory = facade.getSuperCategory();
        assertNotNull(superCategory);
        Category[] children = superCategory.getCategories();
        assertEquals(2, children.length,
                "testdefault.xml has two top-level categories: department + user-groups");
    }

    @Test
    void dynamicTypesLoadedFromFixture() throws Exception
    {
        DynamicType[] all = facade.getDynamicTypes(null);
        assertTrue(all.length >= 4,
                "testdefault.xml ships with at least the four built-in classification types, got " + all.length);
    }

    @Test
    void allocatablesLoadedFromFixture() throws Exception
    {
        Allocatable[] all = facade.getAllocatables();
        assertTrue(all.length >= 4,
                "testdefault.xml ships with several resources/persons, got " + all.length);
    }

    @Test
    void usersLoadedFromFixture() throws Exception
    {
        User[] users = facade.getUsers();
        assertEquals(2, users.length, "testdefault.xml ships with admin + homer");
    }

    @Test
    void freshFacadePerTest()
    {
        assertTrue(operator.isConnected(),
                "each @BeforeEach gives a freshly-connected operator over a fresh @TempDir copy");
    }
}
