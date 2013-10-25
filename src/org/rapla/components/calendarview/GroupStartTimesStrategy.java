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
package org.rapla.components.calendarview;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.Allocatable;
import org.rapla.plugin.abstractcalendar.AbstractRaplaBlock;

/** Tries to put reservations that allocate the same Ressources in the same column.*/
public class GroupStartTimesStrategy extends AbstractGroupStrategy {
	List<Allocatable> allocatables;
	
	List<Integer> startTimes = new ArrayList<Integer>();
	

	{
		for ( int i=0;i<=23;i++)
		{
			startTimes.add(i*60);
		}
	}
	
	@Override
	protected Map<Block, Integer> getBlockMap(CalendarView wv,
			List<Block> blocks) 
	{
		if (allocatables != null)
		{
			Map<Block,Integer> map = new LinkedHashMap<Block, Integer>(); 
			for (Block block:blocks)
			{
				AbstractRaplaBlock b = (AbstractRaplaBlock)block;
				for (Allocatable a:b.getReservation().getAllocatablesFor(b.getAppointment()))
				{
					int index = allocatables.indexOf( a );
					if ( index >= 0 )
					{
						map.put( block, index );
					}
				}
		     }
		     return map;		
		}
		else 
		{
			return super.getBlockMap(wv, blocks);
		}
	}
	
	@Override
    protected List<List<Block>> getSortedSlots(List<Block> list) {
    	TreeMap<Integer,List<Block>> groups = new TreeMap<Integer,List<Block>>();
        for (Iterator<Block> it = list.iterator();it.hasNext();) {
            Block block = it.next();
            long startTime = block.getStart().getTime();
            int minuteOfDay = DateTools.getMinuteOfDay(startTime);
            int rowNumber = -1 ;
            for ( Integer start: startTimes)
            {
            	if ( start <= minuteOfDay)
            	{
            		rowNumber++;
            	}
            	else
            	{
            		break;
            	}
            }
            if ( rowNumber <0)
            {
            	rowNumber = 0;
            }
            
            List<Block> col = groups.get( rowNumber );
            if (col == null) {
                col = new ArrayList<Block>();
                groups.put( rowNumber, col );
            }
            col.add(block);
        }
        List<List<Block>> slots = new ArrayList<List<Block>>();
        for (int row =0 ;row<startTimes.size();row++)
        {
        	List<Block> oneRow =  groups.get( row  );
			if ( oneRow == null)
			{
				oneRow = new ArrayList<Block>();
			}
			else
			{
				Collections.sort( oneRow, blockComparator);
			}
        	slots.add(oneRow);
        }
        return slots;
    }

	@Override
	protected Collection<List<Block>> group(List<Block> blockList) {
		 List<List<Block>> singleGroup = new ArrayList<List<Block>>();
		 singleGroup.add(blockList);
		 return singleGroup;
	}

	public List<Allocatable> getAllocatables() {
		return allocatables;
	}

	public void setAllocatables(List<Allocatable> allocatables) {
		this.allocatables = allocatables;
	}
	
	public List<Integer> getStartTimes() {
		return startTimes;
	}

	public void setStartTimes(List<Integer> startTimes) {
		this.startTimes = startTimes;
	}


}
