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
package org.rapla.client.menu.swing;

import io.reactivex.functions.Consumer;
import org.jetbrains.annotations.NotNull;
import org.rapla.client.PopupContext;
import org.rapla.client.RaplaWidget;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.menu.IdentifiableMenuEntry;
import org.rapla.client.menu.MenuInterface;
import org.rapla.client.menu.MenuItemFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.toolkit.*;
import org.rapla.components.i18n.I18nIcon;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;

@Singleton @DefaultImplementation(of = MenuItemFactory.class, context = InjectionContext.swing) public class MenuItemFactorySwingImpl
        implements MenuItemFactory
{

    private final DialogUiFactoryInterface dialogUiFactory;

    @Inject public MenuItemFactorySwingImpl(DialogUiFactoryInterface dialogUiFactory)
    {
        this.dialogUiFactory = dialogUiFactory;
    }


    @Override
    public IdentifiableMenuEntry createMenuItem(String text, I18nIcon icon, Consumer<PopupContext> action) {
        RaplaMenuItem item = new RaplaMenuItem(createId(text));
        if ( icon != null) {
            final ImageIcon imageIcon = RaplaImages.getIcon(icon);
            item.setIcon(imageIcon);
        }
        item.setEnabled( action != null);
        item.setText( text );
        item.addActionListener((evt)->
        {
            Component parent =  item.getComponentPopupMenu();
            if (parent == null )
            {
                parent = item;
                do {
                    parent = parent.getParent();
                }
                while ( parent != null && !(parent instanceof RaplaPopupMenu));


            }
            PopupContext popupContext = null;
            if ( parent != null )
            {
                if ( parent instanceof RaplaPopupMenu) {
                    popupContext = ((RaplaPopupMenu)parent).getPopupContext();
                }
            }
            if (popupContext == null) {
                popupContext = dialogUiFactory.createPopupContext(() -> item);
            }

            try {
                action.accept( popupContext);
            } catch (Exception ex) {
                dialogUiFactory.showException(ex, null);
            }
        }
        );
        return item;
    }

    long idCounter =  10000;
    @NotNull
    public String createId(String text) {
        return text + "_" + idCounter++;
    }

    @Override
    public MenuInterface createMenu(String text, I18nIcon icon, String id) {
        final RaplaMenu raplaPopupMenu = new RaplaMenu(id);
        raplaPopupMenu.setText( text);
        if ( icon != null)
        {
            raplaPopupMenu.setIcon(RaplaImages.getIcon(icon));
        }
        return raplaPopupMenu;
    }

    @Override
    public RaplaWidget createSeparator(String seperator)
    {
        return new RaplaSeparator("sep2");
    }



}









