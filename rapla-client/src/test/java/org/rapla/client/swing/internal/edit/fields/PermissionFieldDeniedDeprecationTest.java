package org.rapla.client.swing.internal.edit.fields;

import org.junit.jupiter.api.Test;
import org.rapla.entities.domain.Permission;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DENIED is deprecated as a selectable access level (ADR 0003): it must not be
 * offered on new permission rows, but it must stay visible on rows that already
 * carry it — removing it silently would change a stored permission.
 */
class PermissionFieldDeniedDeprecationTest
{
    private static final List<Permission.AccessLevel> CONFIGURED = Arrays.asList(
            Permission.DENIED, Permission.READ, Permission.EDIT, Permission.ADMIN);

    @Test
    void deniedHiddenWhenEditedRowDoesNotUseIt()
    {
        List<Permission.AccessLevel> selectable = PermissionField.selectableLevels(CONFIGURED, Permission.READ);
        assertFalse(selectable.contains(Permission.DENIED), "DENIED must not be selectable on a non-DENIED row");
        assertEquals(Arrays.asList(Permission.READ, Permission.EDIT, Permission.ADMIN), selectable);
    }

    @Test
    void deniedKeptWhenEditedRowAlreadyUsesIt()
    {
        List<Permission.AccessLevel> selectable = PermissionField.selectableLevels(CONFIGURED, Permission.DENIED);
        assertTrue(selectable.contains(Permission.DENIED), "an existing DENIED row must keep DENIED visible");
        assertEquals(CONFIGURED, selectable);
    }

    @Test
    void deniedReInjectedForExistingRowEvenWhenNotConfigured()
    {
        // editor call sites no longer pass DENIED at all; an existing DENIED row must still render
        List<Permission.AccessLevel> configuredWithoutDenied = Arrays.asList(
                Permission.READ, Permission.EDIT, Permission.ADMIN);
        List<Permission.AccessLevel> selectable = PermissionField.selectableLevels(configuredWithoutDenied, Permission.DENIED);
        assertEquals(CONFIGURED, selectable);
    }

    @Test
    void firstNonDeniedIsTheNewRowDefault()
    {
        assertEquals(Permission.READ, PermissionField.firstSelectableLevel(CONFIGURED));
    }
}
