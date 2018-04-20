/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
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
package org.rapla.client.swing.toolkit;

import org.rapla.client.PopupContext;
import org.rapla.client.RaplaWidget;
import org.rapla.client.menu.IdentifiableMenuEntry;
import org.rapla.client.menu.MenuInterface;

import javax.swing.JPopupMenu;
import java.awt.Component;

public class RaplaPopupMenu extends JPopupMenu implements MenuInterface {
    private static final long serialVersionUID = 1L;

    private final String id;
    PopupContext popupContext;

    public RaplaPopupMenu(PopupContext popupContext) {
        super();
        this.popupContext = popupContext;
        this.id = "popup";
    }

    public void setTitle(String title) {}

    public PopupContext getPopupContext() {
        return popupContext;
    }

    private int getIndexOfEntryWithId(String id) {
        int size = getComponentCount();
        for ( int i=0;i< size;i++)
        {
            Component component = getComponent( i );
            if ( component instanceof IdentifiableMenuEntry) {
                IdentifiableMenuEntry comp = (IdentifiableMenuEntry) component;
                if ( id != null && id.equals( comp.getId() ) )
                {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public void removeAllBetween(String startId, String endId) {
        int startIndex = getIndexOfEntryWithId( startId );
        int endIndex = getIndexOfEntryWithId( endId);
        if ( startIndex < 0 || endIndex < 0 )
            return;

        for ( int i= startIndex + 1; i< endIndex ;i++)
        {
            remove( startIndex );
        }

    }

    @Override
    public void insertAfterId(RaplaWidget menu, String id) {
        Component component = (Component) menu.getComponent();
        if ( id == null) {
            add ( component );
        } else {
            int index = getIndexOfEntryWithId( id ) ;
            insert( component, index +1);
        }
    }

    @Override
    public void insertBeforeId(RaplaWidget component,String id) {
        int index = getIndexOfEntryWithId( id );
        final Component component1 = (Component)component.getComponent();
        insert(component1, index);
    }


    @Override
    public void addMenuItem(IdentifiableMenuEntry item) {
        //final JMenuItem item = new JMenuItem(new ActionWrapper(menuItem));
        //mapping.put(menuItem, item);
        super.add((Component)item.getComponent());
    }


    @Override
    public String getId() {
        return id;
    }
}


