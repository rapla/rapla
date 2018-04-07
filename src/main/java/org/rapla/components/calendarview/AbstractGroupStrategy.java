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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Arranges blocks into groups, and tries to place one group into one slot.
    The subclass must overide the group method to perform the grouping on a given
    list of blocks.
*/
public abstract class AbstractGroupStrategy implements BuildStrategy {
    boolean m_sortSlotsBySize;
    public static long MILLISECONDS_PER_DAY = 24 * 3600 * 1000;
    private boolean m_fixedSlots;
    private boolean m_conflictResolving;
    private int offsetMinutes = 0 * 60;

    protected Comparator<Block> blockComparator = new BlockComparator();

	protected Comparator<List<Block>> slotComparator = (s1, s2) -> {
        if (s1.size() == 0 || s2.size() ==0) {
            if (s1.size() == s2.size())
                return 0;
            else
                return s1.size() < s2.size() ? -1 : 1;
        }

        Block b1 = s1.get(0);
        Block b2 =  s2.get(0);
        return b1.getStart().compareTo(b2.getStart());
    };
        
    

    public void build(BlockContainer wv, List<Block> blocks, Date startDate)
    {
        LinkedHashMap<Integer,List<Block>> days = new LinkedHashMap<>();
        Map<Block, Integer> blockMap = getBlockMap(wv, blocks, startDate);
        for (Block b:blockMap.keySet()) {
        	Integer index = blockMap.get(b);
            List<Block> list = days.get(index);
            
            if (list == null)
            {
            	list = new ArrayList<>();
            	days.put(index, list);
            }
            list.add(b);
        }
        
        for (Integer day: days.keySet())
        {
        	List<Block> list = days.get(day);
        	Collections.sort(list, blockComparator);
            if (list == null)
        		continue;
        	insertDay( wv, day,list );
        }
    }

    public void setOffsetMinutes(int offsetMinutes)
    {
        this.offsetMinutes = offsetMinutes;
    }

    @Override
    public int getOffsetMinutes()
    {
        return offsetMinutes;
    }

    protected Map<Block,Integer> getBlockMap(BlockContainer blockContainer, List<Block> blocks, Date startDate) {
    	Map<Block,Integer> map = new LinkedHashMap<>();
    	for  (Block block:blocks) {
            final Date start = block.getStart();
            int intoDay = (int)DateTools.countDays(startDate, start);
            final int minuteOfDay = DateTools.getMinuteOfDay(start.getTime());
            if ( minuteOfDay <offsetMinutes)
            {
                intoDay --;
            }
			 map.put(block, intoDay);
	     }
	     return map;
	}

	protected void insertDay(BlockContainer wv, int column,List<Block> blockList) {
		Iterator<List<Block>> it = getSortedSlots(blockList).iterator();
        int slotCount= 0;
        while (it.hasNext()) {
        	List<Block> slot =  it.next();
            if (slot == null) {
                continue;
            }
            for (Block bl:slot) {
                wv.addBlock(bl,column,slotCount);
            }
            slotCount ++;
        }
    }

    /** You can split the blockList into different groups.
     *  This method returns a collection of lists.
     *   Each list represents a group
      *  of blocks.       
      * @return a collection of List-objects
      * @see List
      * @see Collection
     */
    abstract protected Collection<List<Block>> group(List<Block> blockList);

    public boolean isSortSlotsBySize() {
        return m_sortSlotsBySize;
    }

    public void setSortSlotsBySize(boolean enable) {
        m_sortSlotsBySize = enable;
    }

    /** takes a block list and returns a sorted slotList */
    protected List<List<Block>> getSortedSlots(List<Block> blockList) {
        Collection<List<Block>> group = group(blockList);
		List<List<Block>> slots = new ArrayList<>(group);
        if ( isResolveConflictsEnabled()) {
        	resolveConflicts(slots);
        }
        if ( !isFixedSlotsEnabled() ) {
            mergeSlots(slots);
        }
        if (isSortSlotsBySize())
            Collections.sort(slots, slotComparator);
        return slots;
    }

    protected boolean isCollision(Block b1, Block b2) {
        final long start1 = b1.getStart().getTime();
        long minimumLength = DateTools.MILLISECONDS_PER_MINUTE * 5;
        final long end1 = Math.max(start1+ minimumLength,b1.getEnd().getTime());

        final long start2 = b2.getStart().getTime();
        final long end2 = Math.max(start2 + minimumLength,b2.getEnd().getTime());
        
        boolean result = start1 < end2 && start2 <end1 ;
        return result;
    }


    private void resolveConflicts(List<List<Block>> groups) {
        int pos = 0;
        while (pos < groups.size()) {
            List<Block> group =  groups.get(pos++ );
            List<Block> newSlot = null;
            int i = 0;
            while (i< group.size()) {
                Block element1 = group.get( i++ );
                int j = i;
                while (j< group.size()) {
                    Block element2 = group.get( j ++);
                    if ( isCollision( element1, element2 ) ) {
                        group.remove( element2 );
                        j --;
                        if (newSlot == null) {
                            newSlot = new ArrayList<>();
                            groups.add(pos, newSlot);
                        }
                        newSlot.add( element2);
                    }
                }
            }
        }
    }

    /** the lists must be sorted */
    private boolean canMerge(List<Block> slot1,List<Block> slot2) {
        int size1 = slot1.size();
        int size2 = slot2.size();
        int i = 0;
        int j = 0;
        while (i<size1 && j < size2) {
            Block b1 = slot1.get(i);
            Block b2 = slot2.get(j);
            if (isCollision( b1, b2))
                return false;
            if ( b1.getStart().before( b2.getStart() ))
                i ++;
            else
                j ++;
        }
        return true;
    }

    /** merge two slots */
    private void mergeSlots(List<List<Block>> slots) {
        // We use a (sub-optimal) greedy algorithm for merging slots
        int pos = 0;
        while (pos < slots.size()) {
            List<Block> slot1 =  slots.get(pos ++);
            for (int i= pos; i<slots.size(); i++) {
                List<Block> slot2 = slots.get(i);
                if (canMerge(slot1, slot2)) {
                    slot1.addAll(slot2);
                    Collections.sort(slot1, blockComparator);
                    slots.remove(slot2);
                    pos --;
                    break;
                }
            }
        }
    }

    public void setFixedSlotsEnabled( boolean enable) {
        m_fixedSlots = enable;
    }
    
    public boolean isFixedSlotsEnabled() {
        return m_fixedSlots;
    }


    /** enables or disables conflict resolving. If turned on and 2 blocks ocupy the same slot,
     * a new slot will be inserted dynamicly
     * @param enable
     */
    public void setResolveConflictsEnabled( boolean enable) {
        m_conflictResolving = enable;
    }
    
    public boolean isResolveConflictsEnabled() {
        return m_conflictResolving;
    }
    
    public Comparator<Block> getBlockComparator() {
		return blockComparator;
	}

	public void setBlockComparator(Comparator<Block> blockComparator) {
		this.blockComparator = blockComparator;
	}


}
