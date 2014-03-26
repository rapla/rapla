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
import java.util.ArrayList;
import java.util.Iterator;

import javax.swing.JMenuBar;

public class RaplaMenubar extends JMenuBar {
    private static final long serialVersionUID = 1L;
    
    public RaplaMenubar() {
        super();
    }

    /** returns -1 if there is no */
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

    public void insertAfterId(String id,Component component) {
        int index = getIndexOfEntryWithId( id ) + 1;
        insert( component, index);
    }

    public void insertBeforeId(String id,Component component) {
        int index = getIndexOfEntryWithId( id );
        insert( component, index);
    }

    private void insert(Component component, int index) {
        int size = getComponentCount();

        ArrayList<Component> list = new ArrayList<Component>();

        // save the components begining with index 

        for (int i = index ; i < size; i++)
        {
            list.add( getComponent(index) );
        }
        // now remove all components begining with index
        for (int i = index ; i < size; i++)
        {
            remove(index);
        }
        // now add the new component
        add( component );

        // and the removed components
        for (Iterator<Component> it = list.iterator();it.hasNext();)
        {
            add( it.next() );
        }
    }

}


