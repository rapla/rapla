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
package org.rapla.gui.toolkit;

import java.awt.Component;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

public class RaplaPopupMenu extends JPopupMenu implements MenuInterface {
    private static final long serialVersionUID = 1L;
    
    public RaplaPopupMenu() {
        super();
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

    public void insertAfterId(Component component,String id) {
        if ( id == null) {
            add ( component );
        } else {
            int index = getIndexOfEntryWithId( id ) ;
            insert( component, index +1);
        }
    }

    public void insertBeforeId(JComponent component,String id) {
        int index = getIndexOfEntryWithId( id );
        insert( component, index);
    }

    public void remove( JMenuItem item )
    {
    	super.remove( item );
    }


}


