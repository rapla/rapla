package org.rapla.server.spring.graphql;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.server.spring.web.IsolatedDefaultDatasetTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code rapla.views.builtin-listed} — an allowlist of BUILTIN view keys: when set, a builtin is
 * listed iff its key is in the list (its own {@code @view(listed:)} no longer decides), so a
 * deployment that wants only its custom views is not surprised by a builtin added in an upgrade.
 * Unlisted builtins stay resolvable by name and visible to the authoring surface.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class,
        properties = "rapla.views.builtin-listed=rapla_kalender,rapla_wochenprogramm")
class BuiltinViewListingTest extends IsolatedDefaultDatasetTest
{
    @Autowired ViewCatalogService views;

    @Test
    void theAllowlistDecidesWhichBuiltinsAreListed()
    {
        List<String> listed = views.listViewsForCaller(null, false).stream()
                .filter(ViewEntry::builtin).map(ViewEntry::name).toList();
        assertEquals(List.of("rapla_kalender", "rapla_wochenprogramm"), listed);
    }

    @Test
    void theEffectiveListedStateFollowsTheAllowlist()
    {
        for (ViewEntry b : ViewCatalogService.BUILTIN_VIEWS)
        {
            boolean expected = b.name().equals("rapla_kalender") || b.name().equals("rapla_wochenprogramm");
            assertEquals(expected, views.isListed(b), b.name());
        }
    }

    @Test
    void theAuthoringSurfaceStillSeesEveryBuiltin()
    {
        long all = views.listViewsForCaller(null, true).stream().filter(ViewEntry::builtin).count();
        assertEquals(ViewCatalogService.BUILTIN_VIEWS.size(), all);
    }
}
