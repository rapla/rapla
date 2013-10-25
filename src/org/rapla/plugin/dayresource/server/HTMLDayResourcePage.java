package org.rapla.plugin.dayresource.server;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.rapla.components.calendarview.Block;
import org.rapla.components.calendarview.CalendarView;
import org.rapla.components.calendarview.html.AbstractHTMLView;
import org.rapla.components.calendarview.html.HTMLWeekView;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.abstractcalendar.AbstractRaplaBlock;
import org.rapla.plugin.abstractcalendar.GroupAllocatablesStrategy;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.plugin.weekview.server.HTMLDayViewPage;

public class HTMLDayResourcePage extends HTMLDayViewPage {

	public HTMLDayResourcePage(RaplaContext context, CalendarModel calendarModel)
	{
		super(context, calendarModel);
	}
	
    protected AbstractHTMLView createCalendarView() {
        HTMLWeekView weekView = new HTMLWeekView(){
            
            
        	@Override
        	protected String createColumnHeader(int i)
        	{
            	try 
            	{
					Allocatable allocatable = getSortedAllocatables().get(i);
					return  allocatable.getName( getLocale());
				} 
            	catch (RaplaException e) {
					return "";
				}
        	}
            
            @Override
            protected int getColumnCount() {
            	try {
        		  Allocatable[] selectedAllocatables =model.getSelectedAllocatables();
        		  return selectedAllocatables.length;
          	  	} catch (RaplaException e) {
          	  		return 0;
          	  	}
            }
            
            public void rebuild() {
                setWeeknumber(getRaplaLocale().formatDateShort(getStartDate()));
        		super.rebuild();
        	}
    		
        };
        return weekView;
    }
	
	 
	
	private int getIndex(final List<Allocatable> allocatables,
			Block block) {
		AbstractRaplaBlock b = (AbstractRaplaBlock)block;
		Allocatable a = b.getGroupAllocatable();
		int index = a != null ? allocatables.indexOf( a ) : -1;
		return index;
	}
	
	

	
	protected RaplaBuilder createBuilder() throws RaplaException {
        RaplaBuilder builder = super.createBuilder();

        final List<Allocatable> allocatables = getSortedAllocatables();
        builder.setSplitByAllocatables( true );
        builder.selectAllocatables(allocatables);
        GroupAllocatablesStrategy strategy = new GroupAllocatablesStrategy( getRaplaLocale().getLocale() )
        {
        	@Override
        	protected Map<Block, Integer> getBlockMap(CalendarView wv,
        			List<Block> blocks) 
        	{
        		if (allocatables != null)
        		{
        			Map<Block,Integer> map = new LinkedHashMap<Block, Integer>(); 
        			for (Block block:blocks)
        			{
        				int index = getIndex(allocatables, block);
        				
        				if ( index >= 0 )
        				{
        					map.put( block, index );
        				}
        		     }
        		     return map;		
        		}
        		else 
        		{
        			return super.getBlockMap(wv, blocks);
        		}
        	}

			
        };
       
        
        strategy.setResolveConflictsEnabled( true );
        builder.setBuildStrategy( strategy );

        return builder;
    }
	



}
