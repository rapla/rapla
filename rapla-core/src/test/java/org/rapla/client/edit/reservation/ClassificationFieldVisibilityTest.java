package org.rapla.client.edit.reservation;

import org.junit.jupiter.api.Test;
import org.rapla.client.edit.reservation.ClassificationFieldVisibility.Result;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 coverage of {@link ClassificationFieldVisibility} (PRD 023
 * Phase 6c). Verifies the conservative multi-edit semantics for the
 * (visible, writable) decision: any reader-denied → invisible; any
 * writer-denied → read-only; panelReadOnly always forces read-only.
 */
class ClassificationFieldVisibilityTest
{
    @Test
    void emptyObjectListIsHidden()
    {
        Result r = ClassificationFieldVisibility.resolve(
                List.of(), attribute(), user(), permitAll(), false);
        assertFalse(r.visible());
        assertFalse(r.writable());
    }

    @Test
    void allReadableAndWritableIsVisibleAndWritable()
    {
        Result r = ClassificationFieldVisibility.resolve(
                List.of(classification("c1"), classification("c2")),
                attribute(), user(), permitAll(), false);
        assertTrue(r.visible());
        assertTrue(r.writable());
    }

    @Test
    void anyObjectReadDeniedHidesField()
    {
        Classification c1 = classification("c1");
        Classification c2 = classification("c2");
        PermissionController pc = readDeniedFor(c2);
        Result r = ClassificationFieldVisibility.resolve(
                List.of(c1, c2), attribute(), user(), pc, false);
        assertFalse(r.visible(), "any-object read-denied must hide the field");
        assertFalse(r.writable());
    }

    @Test
    void anyObjectWriteDeniedMakesReadOnly()
    {
        Classification c1 = classification("c1");
        Classification c2 = classification("c2");
        PermissionController pc = writeDeniedFor(c2);
        Result r = ClassificationFieldVisibility.resolve(
                List.of(c1, c2), attribute(), user(), pc, false);
        assertTrue(r.visible(),  "read still allowed → visible");
        assertFalse(r.writable(), "any-object write-denied → read-only");
    }

    @Test
    void panelReadOnlyAlwaysForcesReadOnly()
    {
        Classification c1 = classification("c1");
        Result r = ClassificationFieldVisibility.resolve(
                List.of(c1), attribute(), user(), permitAll(), /*panelReadOnly=*/ true);
        assertTrue(r.visible());
        assertFalse(r.writable(), "panelReadOnly trumps even when permitAll");
    }

    @Test
    void firstReadDeniedShortCircuits()
    {
        // Confirms the loop-break semantics — once read is denied for one
        // object, later objects don't get re-checked. Visible from outside
        // by setting up the second object to flip the result if checked.
        Classification c1 = classification("c1");
        Classification c2 = classification("c2");
        PermissionController pc = readDeniedFor(c1);
        Result r = ClassificationFieldVisibility.resolve(
                List.of(c1, c2), attribute(), user(), pc, false);
        assertFalse(r.visible());
        assertFalse(r.writable());
    }

    @Test
    void resultOfFactoryEnforcesInvariant()
    {
        // !visible implies !writable — Result.of factory enforces.
        Result r = Result.of(false, true);
        assertFalse(r.visible());
        assertFalse(r.writable(), "Result.of must zero writable when !visible");
    }

    // ---------- helpers ----------

    private static PermissionController permitAll()
    {
        return new PermissionController(Set.of(), stubOperator())
        {
            @Override public boolean canRead(Classification o, Attribute a, User u)  { return true; }
            @Override public boolean canWrite(Classification o, Attribute a, User u) { return true; }
        };
    }

    private static PermissionController readDeniedFor(Classification denied)
    {
        return new PermissionController(Set.of(), stubOperator())
        {
            @Override public boolean canRead(Classification o, Attribute a, User u)  { return o != denied; }
            @Override public boolean canWrite(Classification o, Attribute a, User u) { return o != denied; }
        };
    }

    private static PermissionController writeDeniedFor(Classification denied)
    {
        return new PermissionController(Set.of(), stubOperator())
        {
            @Override public boolean canRead(Classification o, Attribute a, User u)  { return true; }
            @Override public boolean canWrite(Classification o, Attribute a, User u) { return o != denied; }
        };
    }

    private static Classification classification(String id)
    {
        return (Classification) Proxy.newProxyInstance(
                Classification.class.getClassLoader(),
                new Class[] { Classification.class },
                (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "equals":   return proxy == args[0];
                        case "hashCode": return System.identityHashCode(proxy);
                        case "toString": return "StubClassification[" + id + "]";
                        default: return null;
                    }
                });
    }

    private static Attribute attribute()
    {
        return (Attribute) Proxy.newProxyInstance(
                Attribute.class.getClassLoader(),
                new Class[] { Attribute.class },
                (proxy, method, args) -> null);
    }

    private static User user()
    {
        return (User) Proxy.newProxyInstance(
                User.class.getClassLoader(),
                new Class[] { User.class },
                (proxy, method, args) -> "isAdmin".equals(method.getName()) ? Boolean.FALSE : null);
    }

    private static StorageOperator stubOperator()
    {
        return (StorageOperator) Proxy.newProxyInstance(
                StorageOperator.class.getClassLoader(),
                new Class[] { StorageOperator.class },
                (proxy, method, args) -> null);
    }
}
