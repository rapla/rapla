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

import java.awt.Component;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

import org.rapla.client.swing.RaplaAction;

public class RaplaMenu extends JMenu implements IdentifiableMenuEntry, MenuInterface {
    private static final long serialVersionUID = 1L;
    
    private final Map<RaplaAction, JMenuItem> mapping = new HashMap<RaplaAction, JMenuItem>();
    String id;

    public RaplaMenu(String id) {
        super(id);
        this.id = id;
    }

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

    public void insertAfterId(Component component,String id) {
        if ( id == null) {
            getPopupMenu().add( component );
        } else {
            int index = getIndexOfEntryWithId( id ) ;
            getPopupMenu().insert( component, index + 1);
        }
    }

    public void insertBeforeId(JComponent component,String id) {
        int index = getIndexOfEntryWithId( id );
        getPopupMenu().insert( component, index);
    }

	@Override
	public JMenuItem getMenuElement() {
		return this;
	}
	
	@Override
	public void add(RaplaAction item)
	{
	    final JMenuItem menuItem = new JMenuItem(new ActionWrapper(item));
	    mapping.put(item, menuItem);
        super.add(menuItem);
	}
	
	@Override
	public void remove(RaplaAction item)
	{
	    final JMenuItem menuItem = mapping.get(item);
	    if(menuItem != null){
	        super.remove(menuItem);
	    }
	}
}


