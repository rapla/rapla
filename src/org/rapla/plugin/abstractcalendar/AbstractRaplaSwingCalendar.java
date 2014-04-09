
/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.RepaintManager;

import org.rapla.components.calendar.DateChangeEvent;
import org.rapla.components.calendar.DateChangeListener;
import org.rapla.components.calendarview.CalendarView;
import org.rapla.components.calendarview.swing.AbstractSwingCalendar;
import org.rapla.components.calendarview.swing.ViewListener;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.NamedComparator;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.SwingCalendarView;
import org.rapla.gui.SwingViewFactory;
import org.rapla.gui.VisibleTimeInterval;


public abstract class AbstractRaplaSwingCalendar extends RaplaGUIComponent
    implements
    SwingCalendarView,
    DateChangeListener,
    MultiCalendarPrint,
    VisibleTimeInterval,
    Printable
{
    protected final CalendarModel model;
    protected final AbstractSwingCalendar view;
    protected DateChooserPanel dateChooser;
    JComponent container;
    JLabel titleView;
    int units = 1;
   
	public AbstractRaplaSwingCalendar(RaplaContext sm,CalendarModel model, boolean editable) throws RaplaException {
        super( sm);
        this.model = model;

        boolean printable = isPrintContext();
        view = createView( !printable);
        view.setEditable(editable);
        view.setLocale( getRaplaLocale().getLocale() );
        view.setTimeZone(getRaplaLocale().getTimeZone());
        if ( editable )
        {
            view.addCalendarViewListener( createListener() );
        }
      
        if ( !printable )
        {
            container = view.getComponent();
        }
        else
        {
            container = new JPanel();
            container.setLayout( new BorderLayout());
            container.setOpaque( false );
            view.getComponent().setOpaque( false);
            titleView = new JLabel();
            titleView.setFont(new Font("SansSerif", Font.BOLD, 14));
            titleView.setOpaque(false);
            titleView.setForeground(Color.black);
            //titleView.setHorizontalAlignment(JLabel.CENTER);
            titleView.setBorder(BorderFactory.createEmptyBorder(0,11,12,11));

            container.add( titleView, BorderLayout.NORTH);
            container.add( view.getComponent(), BorderLayout.CENTER);
        }

        dateChooser = new DateChooserPanel(getContext(), model);
        dateChooser.addDateChangeListener(this);
        dateChooser.setIncrementSize( getIncrementSize() );
        update();
    }

	protected boolean isPrintContext() {
		return getContext().has(SwingViewFactory.PRINT_CONTEXT) && getService( SwingViewFactory.PRINT_CONTEXT);
	}

    abstract protected AbstractSwingCalendar createView(boolean showScrollPane) throws RaplaException;
    abstract protected void configureView() throws RaplaException;
    abstract public int getIncrementSize();

    /**
     * @throws RaplaException  
     */
    protected ViewListener createListener() throws RaplaException {
        return new RaplaCalendarViewListener(getContext(), model, view.getComponent());
    }

    public JComponent getDateSelection()   {
        return dateChooser.getComponent();
    }

    public void dateChanged(DateChangeEvent evt) {
        try {
            // TODO why is that here
            //Date date = evt.getDate();
            // model.setSelectedDate( date );
            update();
        } catch (RaplaException ex) {
            showException(ex, view.getComponent());
        }
    }

    public void update(  ) throws RaplaException {
    	if ( titleView != null)
        {
            titleView.setText( model.getNonEmptyTitle());
        }
        dateChooser.update();
        if (!isPrintContext())
        {
        	int minBlockWidth = getCalendarOptions().getMinBlockWidth();
			view.setMinBlockWidth( minBlockWidth);
        }
        configureView( );
        Date startDate = getStartDate() ;
        Date endDate = getEndDate();
        ensureViewTimeframeIsInModel(startDate, endDate);
      

        view.rebuild( createBuilder() );
        
        if ( !view.isEditable())
        {
            Dimension size = view.getComponent().getPreferredSize();
            container.setBounds( 0,0, size.width, size.height + 40);
        }
    }

	protected Date getEndDate() {
		return view.getEndDate() ;
	}

	protected Date getStartDate() {
		return view.getStartDate();
	}
	
	public TimeInterval getVisibleTimeInterval()
	{
		return new TimeInterval( getStartDate(),getEndDate());
	}

    protected void ensureViewTimeframeIsInModel( Date startDate, Date endDate) {
        //      Update start- and enddate of the model
        Date modelStart = model.getStartDate();
        Date modelEnd = model.getEndDate();
        if ( modelStart == null || modelStart.after( startDate)) {
            model.setStartDate( startDate);
        }
        if ( modelEnd == null || modelEnd.before( endDate)) {
            model.setEndDate( endDate);
        }
    }

   
    protected RaplaBuilder createBuilder() throws RaplaException
    {
        RaplaBuilder builder = new SwingRaplaBuilder(getContext());
        Date startDate = getStartDate() ;
		Date endDate = getEndDate();
		builder.setFromModel( model, startDate, endDate );
		builder.setRepeatingVisible( view.isEditable());
		
        GroupAllocatablesStrategy strategy = new GroupAllocatablesStrategy( getRaplaLocale().getLocale() );
        boolean compactColumns = getCalendarOptions().isCompactColumns() ||  builder.getAllocatables().size() ==0 ;
        strategy.setFixedSlotsEnabled( !compactColumns);
        strategy.setResolveConflictsEnabled( true );
        builder.setBuildStrategy( strategy );
        return builder;
    }

    public JComponent getComponent()
    {
        return container;
    }

    public List<Allocatable> getSortedAllocatables() throws RaplaException
    {
        Allocatable[] selectedAllocatables = model.getSelectedAllocatables();
    	List<Allocatable> sortedAllocatables = new ArrayList<Allocatable>( Arrays.asList( selectedAllocatables));
        Collections.sort(sortedAllocatables, new NamedComparator<Allocatable>( getLocale() ));
        return sortedAllocatables;
    }
    
    public void scrollToStart()
    {
    }

    public CalendarView getCalendarView() {
        return view;
    }
    
    
    //DateTools.addDays(new Date(), 100);
    Date currentPrintDate;
    Map<Date,Integer> pageStartMap = new HashMap<Date,Integer>();
    Double scaleFactor = null;
    /**
     * @see java.awt.print.Printable#print(java.awt.Graphics, java.awt.print.PageFormat, int)
     */
    public int print(Graphics g, PageFormat format, int page) throws PrinterException {
    	
    	/*JFrame frame = new JFrame();
        frame.setSize(300,300);
        frame.getContentPane().add( container);
        frame.pack();
        frame.setVisible(false);*/
    	final Date startDate = model.getStartDate();
    	final Date endDate = model.getEndDate();
        final Date selectedDate = model.getSelectedDate();
        
        int pages = getUnits();
    	Date targetDate;

        {
        	Calendar cal = getRaplaLocale().createCalendar();
        	cal.setTime( selectedDate);
        	cal.add(getIncrementSize(), pages-1);
        	targetDate = cal.getTime();
        }

        if ( page <= 0 )
        {
        	currentPrintDate = selectedDate;
        	pageStartMap.clear();
        	scaleFactor = null;
        	pageStartMap.put(currentPrintDate,page);
        }
        while (true)
        {
        	int printOffset = (int)DateTools.countDays(selectedDate, currentPrintDate);
			model.setStartDate(DateTools.addDays(startDate, printOffset));
			model.setEndDate(DateTools.addDays(endDate, printOffset));
	    	model.setSelectedDate( DateTools.addDays(selectedDate, printOffset));
	    	try
	    	{
		    	Graphics2D g2 = (Graphics2D) g;
		    	
		    	try 
		    	{
		    		update();
				} 
				catch (RaplaException e) 
				{
					throw new PrinterException(e.getMessage());
				}
		   
		    	double preferedHeight = view.getComponent().getPreferredSize().getHeight();
		    	if ( scaleFactor == null)
		    	{
		    		scaleFactor =  Math.min(1, 1/ Math.min(2.5,preferedHeight / format.getImageableHeight()));
		    	}
		    	double newWidth =  format.getImageableWidth() / scaleFactor  ;
		    	double scaledPreferedHeigth =preferedHeight * scaleFactor;
		        
		    	Component component = container;
		        view.updateSize( (int)newWidth );
		        container.setBounds( 0,0, (int)newWidth, (int)preferedHeight);
		        try {
		    		update();
				} 
				catch (RaplaException e) 
				{
					throw new PrinterException(e.getMessage());
				}
		    	
		        Integer pageStart = pageStartMap.get( currentPrintDate);
		    	if ( pageStart == null)
		    	{
		    		return NO_SUCH_PAGE;
		    	}
		        int translatey = (int)((page-pageStart )* format.getImageableHeight());
		    	if ( translatey > scaledPreferedHeigth-20)
		    	{
		    		if ( targetDate != null && currentPrintDate.before( targetDate))
		    		{
		    			Calendar cal = getRaplaLocale().createCalendar();
		            	cal.setTime( currentPrintDate);
		            	cal.add(getIncrementSize(), 1);
		    			currentPrintDate = cal.getTime();
		    			pageStartMap.put(currentPrintDate,page);
		    			continue;
		    		}
		    		else
		    		{
		    	  		return NO_SUCH_PAGE;
		    		}
		    	}
		    	if ( translatey <0 && targetDate!= null)
		    	{
		    		Calendar cal = getRaplaLocale().createCalendar();
	            	cal.setTime( currentPrintDate);
	            	cal.add(getIncrementSize(), -1);
	    			currentPrintDate = cal.getTime();
		    		continue;
		    	}
		    	if ( targetDate != null && currentPrintDate.after( targetDate))
		    	{
		    		return NO_SUCH_PAGE;
		    	}
		    	
		    	g2.translate(format.getImageableX(), format.getImageableY() - translatey  );
		    	g2.clipRect(0, translatey , (int)(format.getImageableWidth() ), (int)(format.getImageableHeight()));
		    	g2.scale(scaleFactor, scaleFactor);
		
		    	RepaintManager rm = RepaintManager.currentManager(component);
		        boolean db= rm.isDoubleBufferingEnabled();
		        try {
		            rm.setDoubleBufferingEnabled(false);
		            component.printAll(g);
		            return Printable.PAGE_EXISTS;
		        } 
		        finally 
		        {
		            rm.setDoubleBufferingEnabled(db);
		        }
	    	}
	    	finally 
	    	{
	        	model.setStartDate(startDate);
	        	model.setEndDate(endDate);
	        	model.setSelectedDate( selectedDate);
	        }
        }
    }

    public String getCalendarUnit()
    {
    	int incrementSize = getIncrementSize();
    	if ( incrementSize == Calendar.DAY_OF_YEAR)
    	{
    		return getString("days");
    	}
    	else if ( incrementSize == Calendar.WEEK_OF_YEAR)
    	{
    		return getString("weeks");
    	}
    	else if ( incrementSize == Calendar.MONTH)
    	{
    		return getString("months");
    	}
    	else
    	{
    		return "";
    	}
    }
    
    public int getUnits() {
		return units;
	}

	public void setUnits(int units) {
		this.units = units;
	}


}
