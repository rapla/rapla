package org.rapla.test.util;

import org.jetbrains.annotations.NotNull;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.domain.permission.impl.RaplaDefaultPermissionImpl;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;

import java.util.LinkedHashSet;

public class DefaultPermissionControllerSupport
{
    
    public static PermissionController getController(StorageOperator operator)
    {
        final LinkedHashSet<PermissionExtension> permissionExtensions = getPermissionExtensions();
        return new PermissionController(permissionExtensions, operator);
    }

    @NotNull public static LinkedHashSet<PermissionExtension> getPermissionExtensions()
    {
        final LinkedHashSet<PermissionExtension> permissionExtensions = new LinkedHashSet<PermissionExtension>();
        permissionExtensions.add(new RaplaDefaultPermissionImpl());
        return permissionExtensions;
    }

}
