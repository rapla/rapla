/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Gereon Fassbender, Christopher Kohlhaas               |
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
package org.rapla.plugin.abstractcalendar;

import org.rapla.components.calendarview.AbstractGroupStrategy;
import org.rapla.components.calendarview.Block;
import org.rapla.entities.NamedComparator;
import org.rapla.entities.domain.Allocatable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
/** Tries to put reservations that allocate the same Resources in the same column.*/
public class GroupAllocatablesStrategy extends AbstractGroupStrategy {
    Comparator<Allocatable> allocatableComparator;
    Collection<Allocatable> allocatables = new ArrayList<>();
    
    public GroupAllocatablesStrategy(Locale locale) {
    	allocatableComparator = new NamedComparator<>(locale);
    }

    public void setAllocatables( Collection<Allocatable> allocatables) {
    	this.allocatables = allocatables;
    }
    
    protected Collection<List<Block>> group(List<Block> list) {
    	
    	HashMap<Allocatable,List<Block>> groups = new HashMap<>();
        for (Iterator<Allocatable> it = allocatables.iterator();it.hasNext(); ) {
            groups.put( it.next(), new ArrayList<>() );
        }
        List<Block> noAllocatablesGroup = null;
        for (Iterator<Block> it = list.iterator();it.hasNext();) {
            RaplaBlock block = (RaplaBlock) it.next();
            Allocatable allocatable = block.getContext().getGroupAllocatable();
            if (allocatable ==  null) {
                if (noAllocatablesGroup == null)
                    noAllocatablesGroup = new ArrayList<>();
                noAllocatablesGroup.add(block);
                continue;
            }
            List<Block> col =  groups.get( allocatable );
            if (col == null) {
                col = new ArrayList<>();
                groups.put( allocatable, col );
            }
            col.add(block);
        }
        ArrayList<Allocatable> sortedAllocatables = new ArrayList<>(groups.keySet());
        Collections.sort(sortedAllocatables, allocatableComparator);
        
        ArrayList<List<Block>> sortedList = new ArrayList<>();
    
        for (Iterator<Allocatable> it = sortedAllocatables.iterator();it.hasNext();) {
        	sortedList.add( groups.get(it.next()) );
        }

        if (noAllocatablesGroup != null)
            sortedList.add(noAllocatablesGroup);

        return sortedList;
    }


}
