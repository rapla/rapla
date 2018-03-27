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

import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.Allocatable;
import org.rapla.plugin.abstractcalendar.RaplaBlock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Tries to put reservations that allocate the same Ressources in the same column.*/
public class GroupStartTimesStrategy extends AbstractGroupStrategy {
	List<Allocatable> allocatables;
	
	List<Integer> startTimes = new ArrayList<>();
	

	{
		for ( int i=0;i<=23;i++)
		{
			startTimes.add(i*60);
		}
	}
	
	@Override
	protected Map<Block, Integer> getBlockMap(BlockContainer blockContainer,
			List<Block> blocks, Date startDate)
	{
		if (allocatables != null)
		{
			Map<Block,Integer> map = new LinkedHashMap<>();
			for (Block block:blocks)
			{
				RaplaBlock b = (RaplaBlock)block;
				b.getReservation().getAllocatablesFor(b.getAppointment()).forEach( a->
				{
					int index = allocatables.indexOf( a );
					if ( index >= 0 )
					{
						map.put( block, index );
					}
				});
		     }
		     return map;		
		}
		else 
		{
			return super.getBlockMap(blockContainer, blocks, startDate);
		}
	}
	
	@Override
    protected List<List<Block>> getSortedSlots(List<Block> list) {
    	TreeMap<Integer,List<Block>> groups = new TreeMap<>();
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
                col = new ArrayList<>();
                groups.put( rowNumber, col );
            }
            col.add(block);
        }
        List<List<Block>> slots = new ArrayList<>();
        for (int row =0 ;row<startTimes.size();row++)
        {
        	List<Block> oneRow =  groups.get( row  );
			if ( oneRow == null)
			{
				oneRow = new ArrayList<>();
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
		 List<List<Block>> singleGroup = new ArrayList<>();
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
