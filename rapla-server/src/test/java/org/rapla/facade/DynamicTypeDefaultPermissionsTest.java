package org.rapla.facade;

import org.junit.jupiter.api.Test;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.test.util.FacadeTestSupport;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WP P1s — golden master for the permission rows a new DynamicType starts with (Swing {@code FacadeImpl.newDynamicType}).
 * The GraphQL create path seeds the same rows through the same helper, so this pins the one implementation.
 */
class DynamicTypeDefaultPermissionsTest extends FacadeTestSupport
{
    private List<String> rows(String classificationType) throws Exception
    {
        DynamicType type = facade.newDynamicType(classificationType);
        List<String> out = new ArrayList<>();
        type.getPermissionList().forEach(p -> out.add(p.getAccessLevel().name() + ":"
                + (p.getGroup() != null ? p.getGroup().getKey() : p.getUser() != null ? p.getUser().getUsername() : "everyone")));
        return out;
    }

    @Test
    void resourceTypeDefaults() throws Exception
    {
        assertEquals(List.of("READ_TYPE:everyone", "ALLOCATE_CONFLICTS:everyone", "CREATE:registerer"),
                rows(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE));
    }

    @Test
    void personTypeDefaults() throws Exception
    {
        assertEquals(List.of("READ_TYPE:everyone", "ALLOCATE_CONFLICTS:everyone", "CREATE:registerer"),
                rows(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON));
    }

    @Test
    void eventTypeDefaults() throws Exception
    {
        assertEquals(List.of("READ_TYPE:everyone", "READ:read-events-from-others", "CREATE:create-events"),
                rows(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION));
    }
}
