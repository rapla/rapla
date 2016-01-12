package org.rapla.client.swing.internal.common;

import org.rapla.client.swing.toolkit.RaplaMenu;
import org.rapla.framework.TypedComponentRole;

public interface InternMenus
{
    TypedComponentRole<RaplaMenu> FILE_MENU_ROLE = new TypedComponentRole<RaplaMenu>("org.rapla.client.swing.gui.SystemMenu");
    TypedComponentRole<RaplaMenu> EXTRA_MENU_ROLE = new TypedComponentRole<RaplaMenu>("org.rapla.client.swing.gui.ExtraMenu");
    TypedComponentRole<RaplaMenu> VIEW_MENU_ROLE =new TypedComponentRole<RaplaMenu>("org.rapla.client.swing.gui.ViewMenu");
    TypedComponentRole<RaplaMenu> EXPORT_MENU_ROLE = new TypedComponentRole<RaplaMenu>("org.rapla.client.swing.gui.ExportMenu");
    TypedComponentRole<RaplaMenu> ADMIN_MENU_ROLE =new TypedComponentRole<RaplaMenu>("org.rapla.client.swing.gui.AdminMenu");
    TypedComponentRole<RaplaMenu> EDIT_MENU_ROLE = new TypedComponentRole<RaplaMenu>("org.rapla.client.swing.gui.EditMenu");
    TypedComponentRole<RaplaMenu> IMPORT_MENU_ROLE =new TypedComponentRole<RaplaMenu>("org.rapla.client.swing.gui.ImportMenu");
    TypedComponentRole<RaplaMenu> NEW_MENU_ROLE =new TypedComponentRole<RaplaMenu>("org.rapla.client.swing.gui.NewMenu");
    TypedComponentRole<RaplaMenu> CALENDAR_SETTINGS = new TypedComponentRole<RaplaMenu>("org.rapla.client.swing.gui.CalendarSettings");
    

}
