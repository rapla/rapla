package org.rapla.client.swing.internal.common;

import org.rapla.client.swing.toolkit.RaplaMenu;
import org.rapla.client.swing.toolkit.RaplaMenubar;
import org.rapla.framework.TypedComponentRole;

public interface InternMenus
{
    public static final TypedComponentRole<RaplaMenu> FILE_MENU_ROLE = new TypedComponentRole<RaplaMenu>("org.rapla.client.swing.gui.SystemMenu");
    public static final TypedComponentRole<RaplaMenu> EXTRA_MENU_ROLE = new TypedComponentRole<RaplaMenu>("org.rapla.client.swing.gui.ExtraMenu");
    public static final TypedComponentRole<RaplaMenu> VIEW_MENU_ROLE =new TypedComponentRole<RaplaMenu>("org.rapla.client.swing.gui.ViewMenu");
    public static final TypedComponentRole<RaplaMenu> EXPORT_MENU_ROLE = new TypedComponentRole<RaplaMenu>("org.rapla.client.swing.gui.ExportMenu");
    public static final TypedComponentRole<RaplaMenu> ADMIN_MENU_ROLE =new TypedComponentRole<RaplaMenu>("org.rapla.client.swing.gui.AdminMenu");
    public static final TypedComponentRole<RaplaMenu> EDIT_MENU_ROLE = new TypedComponentRole<RaplaMenu>("org.rapla.client.swing.gui.EditMenu");
    public static final TypedComponentRole<RaplaMenu> IMPORT_MENU_ROLE =new TypedComponentRole<RaplaMenu>("org.rapla.client.swing.gui.ImportMenu");
    public static final TypedComponentRole<RaplaMenu> NEW_MENU_ROLE =new TypedComponentRole<RaplaMenu>("org.rapla.client.swing.gui.NewMenu");
    public static final TypedComponentRole<RaplaMenu> CALENDAR_SETTINGS = new TypedComponentRole<RaplaMenu>("org.rapla.client.swing.gui.CalendarSettings");
    

}
