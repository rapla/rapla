/*--------------------------------------------------------------------------*
 | Copyright (C) 2013 Christopher Kohlhaas, Bettina Lademann                |
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
package org.rapla.client.menu.gwt;

import io.reactivex.functions.Consumer;
import org.jetbrains.annotations.NotNull;
import org.rapla.client.PopupContext;
import org.rapla.client.RaplaWidget;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.menu.IdentifiableMenuEntry;
import org.rapla.client.menu.MenuInterface;
import org.rapla.client.menu.MenuItemFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.toolkit.RaplaMenu;
import org.rapla.client.swing.toolkit.RaplaMenuItem;
import org.rapla.client.swing.toolkit.RaplaPopupMenu;
import org.rapla.client.swing.toolkit.RaplaSeparator;
import org.rapla.components.i18n.I18nIcon;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.ImageIcon;
import java.awt.Component;

@Singleton @DefaultImplementation(of = MenuItemFactory.class, context = InjectionContext.gwt) public class MenuItemFactoryGwtImpl
        implements MenuItemFactory
{

    private final DialogUiFactoryInterface dialogUiFactory;

    @Inject public MenuItemFactoryGwtImpl(DialogUiFactoryInterface dialogUiFactory)
    {
        this.dialogUiFactory = dialogUiFactory;
    }


    @Override
    public IdentifiableMenuEntry createMenuItem(String text, I18nIcon icon, Consumer<PopupContext> action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MenuInterface createMenu(String text, I18nIcon icon) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RaplaWidget createSeparator(String seperator)
    {
        throw new UnsupportedOperationException();
    }



}









