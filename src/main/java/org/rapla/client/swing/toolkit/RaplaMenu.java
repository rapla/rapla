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

import org.rapla.client.RaplaWidget;
import org.rapla.client.menu.IdentifiableMenuEntry;
import org.rapla.client.menu.MenuInterface;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.Component;

public class RaplaMenu extends JMenu implements IdentifiableMenuEntry, MenuInterface {
    private static final long serialVersionUID = 1L;

    String id;

    public RaplaMenu(String id) {
        super(id);
        this.id = id;
    }

    public void setTitle(String title) {}

    public String getId() {
        return id;
    }

    public int getIndexOfEntryWithId(String id) {
        int size = getMenuComponentCount();
        for ( int i=0;i< size;i++)
        {
            Component component = getMenuComponent( i );
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

    public void removeAllBetween(String startId, String endId) {
        int startIndex = getIndexOfEntryWithId( startId );
        int endIndex = getIndexOfEntryWithId( endId);
        if ( startIndex < 0 || endIndex < 0 )
            return;

        for ( int i= startIndex + 1; i< endIndex ;i++)
        {
            remove( startIndex + 1);
        }

    }

    public boolean hasId(String id) {
        return getIndexOfEntryWithId( id )>=0;
    }

    @Override
    public void insertAfterId(RaplaWidget widget, String id) {
        Component component = (Component) widget.getComponent();
        final JPopupMenu popupMenu = getPopupMenu();
        if ( id == null) {
            popupMenu.add( component );
        } else {
            int index = getIndexOfEntryWithId( id ) ;
            popupMenu.insert( component, index + 1);
        }
    }

    @Override
    public void insertBeforeId(RaplaWidget component,String id) {
        int index = getIndexOfEntryWithId( id );
        final JPopupMenu popupMenu = getPopupMenu();
        popupMenu.insert( (Component)component.getComponent(), index);
    }

	@Override
	public JMenuItem getComponent() {
		return this;
	}


    @Override
    public void addMenuItem(IdentifiableMenuEntry item) {
        //final JMenuItem item = new JMenuItem(new ActionWrapper(menuItem));
        //mapping.put(menuItem, item);
        super.add((Component)item.getComponent());
        int maxItems = 20;
        if (getMenuComponentCount() == maxItems)
        {
            int millisToScroll = 40;
            MenuScroller.setScrollerFor((JMenu) getComponent(), maxItems, millisToScroll);
        }
    }

}


