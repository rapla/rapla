package org.rapla.storage.impl.server;

import org.junit.jupiter.api.Test;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.test.util.FacadeTestSupport;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the type-prefix letters of server-generated entity ids
 * (LocalAbstractCachableOperator.replaceFirst). Since 2026-07-06 the letters
 * for User and Allocatable are hex characters (b / f) so that every
 * server-generated id is a grammatically valid UUID — legacy stores keep
 * their r… / u… ids (lookup is by full opaque string, the letter is not
 * load-bearing). See docs/architecture/domain-model.md "Id format and
 * assignment".
 */
class CreateIdPrefixTest extends FacadeTestSupport
{
    private char prefixOf(Class<? extends org.rapla.entities.Entity> type) throws Exception
    {
        return operator.createIdentifier(type, 1).get(0).getId().charAt(0);
    }

    @Test
    void prefixLettersPerType() throws Exception
    {
        assertEquals('e', prefixOf(Reservation.class), "reservation = event");
        assertEquals('a', prefixOf(Appointment.class));
        assertEquals('c', prefixOf(Category.class));
        assertEquals('d', prefixOf(DynamicType.class));
        assertEquals('b', prefixOf(User.class), "user = Benutzer (hex-valid, was u)");
        assertEquals('f', prefixOf(Allocatable.class), "allocatable = facility (hex-valid, was r)");
    }

    @Test
    void generatedIdsAreStructurallyValidUuids() throws Exception
    {
        for (Class<? extends org.rapla.entities.Entity> type : java.util.List.of(
                Reservation.class, Appointment.class, User.class, Allocatable.class,
                Category.class, DynamicType.class))
        {
            String id = operator.createIdentifier(type, 1).get(0).getId();
            assertDoesNotThrow(() -> UUID.fromString(id),
                    "server-generated id must parse as UUID: " + id + " (" + type.getSimpleName() + ")");
        }
    }
}
