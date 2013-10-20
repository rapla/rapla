package org.rapla.gui.internal.common;

import org.rapla.framework.TypedComponentRole;
import org.rapla.gui.toolkit.RaplaMenu;
import org.rapla.gui.toolkit.RaplaMenubar;

public interface InternMenus
{
    public static final TypedComponentRole<RaplaMenubar> MENU_BAR = new TypedComponentRole<RaplaMenubar>("org.rapla.gui.MenuBar");

    public static final TypedComponentRole<RaplaMenu> FILE_MENU_ROLE = new TypedComponentRole<RaplaMenu>("org.rapla.gui.SystemMenu");
    public static final TypedComponentRole<RaplaMenu> EXTRA_MENU_ROLE = new TypedComponentRole<RaplaMenu>("org.rapla.gui.ExtraMenu");
    public static final TypedComponentRole<RaplaMenu> VIEW_MENU_ROLE =new TypedComponentRole<RaplaMenu>("org.rapla.gui.ViewMenu");
    public static final TypedComponentRole<RaplaMenu> EXPORT_MENU_ROLE = new TypedComponentRole<RaplaMenu>("org.rapla.gui.ExportMenu");
    public static final TypedComponentRole<RaplaMenu> ADMIN_MENU_ROLE =new TypedComponentRole<RaplaMenu>("org.rapla.gui.AdminMenu");
    public static final TypedComponentRole<RaplaMenu> EDIT_MENU_ROLE = new TypedComponentRole<RaplaMenu>("org.rapla.gui.EditMenu");
    public static final TypedComponentRole<RaplaMenu> IMPORT_MENU_ROLE =new TypedComponentRole<RaplaMenu>("org.rapla.gui.ImportMenu");
    public static final TypedComponentRole<RaplaMenu> NEW_MENU_ROLE =new TypedComponentRole<RaplaMenu>("org.rapla.gui.NewMenu");
    public static final TypedComponentRole<RaplaMenu> CALENDAR_SETTINGS = new TypedComponentRole<RaplaMenu>("org.rapla.gui.CalendarSettings");
    

}
