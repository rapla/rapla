/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas, Bettina Lademann                |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.client.menu;
import io.reactivex.functions.Consumer;
import jsinterop.annotations.JsType;
import org.rapla.client.PopupContext;
import org.rapla.framework.RaplaException;

@JsType
public interface MenuFactory  {
    MenuInterface addCalendarSelectionMenu(MenuInterface menu, SelectionMenuContext context) throws RaplaException;
    MenuInterface addCopyCutListMenu(MenuInterface editMenu, SelectionMenuContext menuContext, String afterId,
            Consumer<PopupContext> cutListener, Consumer<PopupContext> copyListener) throws RaplaException;

    MenuInterface addNewMenu(MenuInterface menu, SelectionMenuContext context, String afterId) throws RaplaException;
    MenuInterface addObjectMenu(MenuInterface menu, SelectionMenuContext context, String afterId) throws RaplaException;
    MenuInterface addEventMenu(MenuInterface editMenu, SelectionMenuContext menuContext, Consumer<PopupContext> cutListener, Consumer<PopupContext> copyListener) throws RaplaException;
    MenuInterface addReservationMenu(MenuInterface menu, SelectionMenuContext context, String afterId) throws
      RaplaException;
    int addReservationWizards(MenuInterface menu, SelectionMenuContext context, String afterId) throws RaplaException;
    MenuItemFactory getItemFactory();

    void executeCalenderAction(AllocatableReservationMenuContext menuContext);
}









