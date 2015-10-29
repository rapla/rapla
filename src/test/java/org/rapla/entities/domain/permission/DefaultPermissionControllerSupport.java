package org.rapla.entities.domain.permission;

import java.util.LinkedHashSet;

import org.rapla.entities.domain.permission.impl.RaplaDefaultPermissionImpl;

public class DefaultPermissionControllerSupport
{
    
    public static PermissionController getController()
    {
        final LinkedHashSet<PermissionExtension> permissionExtensions = new LinkedHashSet<PermissionExtension>();
        permissionExtensions.add(new RaplaDefaultPermissionImpl());
        return getController(permissionExtensions);
    }

    public static PermissionController getController(final LinkedHashSet<PermissionExtension> permissionExtensions)
    {
        return new PermissionController(permissionExtensions);
    }

}
