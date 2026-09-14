package org.rapla.server.spring.graphql;

import org.junit.jupiter.api.Test;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.server.spring.web.IsolatedDefaultDatasetTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The headline case of {@code rapla.views.builtin-listed}: an EMPTY list = custom views only. */
@SpringBootTest(classes = RaplaSpringBootApplication.class, properties = "rapla.views.builtin-listed=")
class BuiltinViewListingEmptyTest extends IsolatedDefaultDatasetTest
{
    @Autowired ViewCatalogService views;

    @Test
    void anEmptyAllowlistListsNoBuiltin()
    {
        long listed = views.listViewsForCaller(null, false).stream().filter(ViewEntry::builtin).count();
        assertEquals(0, listed);
    }
}
