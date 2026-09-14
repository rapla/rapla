package org.rapla.storage.xml;

import org.junit.jupiter.api.Test;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.test.util.FacadeTestSupport;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-2 backfill (PRD 017 Phase 4 #12) for the XML reader/writer round-trip.
 * Loads {@code testdefault.xml} via {@link FacadeTestSupport}, snapshots
 * facade state, calls {@code operator.saveData()} to write the cache back to
 * disk, disconnects and reconnects (forcing a fresh read), then asserts the
 * snapshot still matches.
 *
 * <p>Targets the kind of encoder/decoder drift PRD 010 / PRD 011 introduced
 * and PRD 016 cleaned up: a wire-format field that gets serialized but not
 * deserialized (or vice versa) survives a round-trip in tests that just
 * exercise the read path. This test catches the asymmetric cases.
 */
class XmlRoundTripTest extends FacadeTestSupport
{
    private static final Locale LOCALE = Locale.ENGLISH;

    private void roundTrip() throws Exception
    {
        operator.saveData();
        operator.disconnect();
        operator.connect();
    }

    @Test
    void categoryCountAndKeysSurviveRoundTrip() throws Exception
    {
        Map<String, String> before = topLevelCategoryKeys();

        roundTrip();

        Map<String, String> after = topLevelCategoryKeys();
        assertEquals(before, after, "top-level category keys + ids must be identical after round-trip");
    }

    @Test
    void dynamicTypeCountSurvivesRoundTrip() throws Exception
    {
        DynamicType[] before = facade.getDynamicTypes(null);

        roundTrip();

        DynamicType[] after = facade.getDynamicTypes(null);
        assertEquals(before.length, after.length, "dynamic type count must match after round-trip");

        // Sort by key so order doesn't matter; assertEquals on the sorted key lists.
        assertEquals(sortedKeys(before), sortedKeys(after),
                "dynamic type keys must be identical after round-trip");
    }

    @Test
    void allocatableNamesSurviveRoundTrip() throws Exception
    {
        // The exact bug shape PRD 011 introduced: names empty after deserialize.
        // Snapshot every allocatable's name in english, round-trip, assert intact.
        Map<String, String> before = allocatableNames();
        assertTrue(before.size() > 0, "fixture should expose at least one allocatable");

        roundTrip();

        Map<String, String> after = allocatableNames();
        assertEquals(before, after, "allocatable names (and IDs) must survive round-trip");

        for (Map.Entry<String, String> entry : after.entrySet())
        {
            assertNotNull(entry.getValue(), "name null for allocatable " + entry.getKey());
            assertTrue(!entry.getValue().isEmpty(),
                    "name empty for allocatable " + entry.getKey() + " — wire-format bug shape");
        }
    }

    @Test
    void userCountAndLoginsSurviveRoundTrip() throws Exception
    {
        Map<String, String> before = userLogins();

        roundTrip();

        Map<String, String> after = userLogins();
        assertEquals(before, after, "user logins must survive round-trip");
    }

    @Test
    void userAuthenticationSourceSurvivesRoundTrip() throws Exception
    {
        // PRD 050: stamp the external-auth marker on a user, round-trip,
        // verify it's intact. Catches the same encoder/decoder drift this
        // test class targets for other entity fields — the marker drives
        // self-change blocks, so silent loss would re-open the shadow-
        // password footgun the PRD was designed to close.
        User homer = findUser("homer");
        assertNotNull(homer, "fixture expected to expose user 'homer'");
        org.rapla.entities.User editHomer = facade.edit(homer);
        editHomer.setAuthenticationSource("keycloak:realm-vrz");
        facade.store(editHomer);

        roundTrip();

        User after = findUser("homer");
        assertEquals("keycloak:realm-vrz", after.getAuthenticationSource(),
                "authentication-source must survive XML round-trip (PRD 050)");

        // Negative control — local-only user stays null.
        User monty = findUser("monty");
        assertNotNull(monty, "fixture expected to expose user 'monty'");
        assertEquals(null, monty.getAuthenticationSource(),
                "untouched user must keep authentication-source null after round-trip");
    }

    private User findUser(String username) throws Exception
    {
        for (User u : facade.getUsers())
        {
            if (username.equals(u.getUsername())) return u;
        }
        return null;
    }

    @Test
    void allocatableClassificationTypeKeySurvivesRoundTrip() throws Exception
    {
        // The classification → DynamicType resolver wiring is what failed in
        // HeadlessClientNameResolutionIntegrationTest; this test pins the
        // server-side equivalent at tier-2 cost.
        Map<String, String> before = allocatableClassificationTypeKeys();

        roundTrip();

        Map<String, String> after = allocatableClassificationTypeKeys();
        assertEquals(before, after,
                "each allocatable's classification.getType().getKey() must survive round-trip");
    }

    @Test
    void doubleRoundTripIsIdempotent() throws Exception
    {
        // Load → save → reload → save → reload. After two cycles, all snapshots
        // must still match. A single-pass test could miss a transient field
        // that's reconstructed differently on first load vs subsequent loads.
        Map<String, String> categoriesA = topLevelCategoryKeys();
        Map<String, String> namesA = allocatableNames();

        roundTrip();
        roundTrip();

        assertEquals(categoriesA, topLevelCategoryKeys(),
                "categories must match after two round-trips");
        assertEquals(namesA, allocatableNames(),
                "allocatable names must match after two round-trips");
    }

    // --- snapshot helpers --------------------------------------------------

    private Map<String, String> topLevelCategoryKeys()
    {
        Category[] children = facade.getSuperCategory().getCategories();
        Map<String, String> out = new TreeMap<>();
        for (Category c : children) out.put(c.getId(), c.getKey());
        return out;
    }

    private Map<String, String> sortedKeys(DynamicType[] types)
    {
        Map<String, String> out = new TreeMap<>();
        for (DynamicType t : types) out.put(t.getId(), t.getKey());
        return out;
    }

    private Map<String, String> allocatableNames() throws Exception
    {
        Allocatable[] all = facade.getAllocatables();
        Map<String, String> out = new LinkedHashMap<>();
        for (Allocatable a : all) out.put(a.getId(), a.getName(LOCALE));
        return out;
    }

    private Map<String, String> userLogins() throws Exception
    {
        User[] users = facade.getUsers();
        Map<String, String> out = new TreeMap<>();
        for (User u : users) out.put(u.getId(), u.getUsername());
        return out;
    }

    private Map<String, String> allocatableClassificationTypeKeys() throws Exception
    {
        Allocatable[] all = facade.getAllocatables();
        Map<String, String> out = new LinkedHashMap<>();
        for (Allocatable a : all)
        {
            DynamicType type = a.getClassification().getType();
            out.put(a.getId(), type == null ? null : type.getKey());
        }
        return out;
    }
}
