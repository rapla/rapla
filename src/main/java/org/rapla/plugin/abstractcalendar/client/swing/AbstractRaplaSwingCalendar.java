
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

package org.rapla.plugin.abstractcalendar.client.swing;

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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Provider;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.RepaintManager;

import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.ReservationController;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.ObjectMenuFactory;
import org.rapla.client.internal.RaplaClipboard;
import org.rapla.client.swing.InfoFactory;
import org.rapla.client.swing.MenuFactory;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.SwingCalendarView;
import org.rapla.client.swing.VisibleTimeInterval;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.components.calendar.DateChangeEvent;
import org.rapla.components.calendar.DateChangeListener;
import org.rapla.components.calendar.DateRenderer;
import org.rapla.components.calendarview.CalendarView;
import org.rapla.components.calendarview.swing.AbstractSwingCalendar;
import org.rapla.components.calendarview.swing.ViewListener;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.DateChooserPanel;
import org.rapla.plugin.abstractcalendar.GroupAllocatablesStrategy;
import org.rapla.plugin.abstractcalendar.MultiCalendarPrint;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.plugin.abstractcalendar.RaplaCalendarViewListener;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;
import org.rapla.server.PromiseSynchroniser;

public abstract class AbstractRaplaSwingCalendar extends RaplaGUIComponent
    implements
    SwingCalendarView,
    DateChangeListener, MultiCalendarPrint,
    VisibleTimeInterval,
    Printable
{
    protected final CalendarModel model;
    protected final AbstractSwingCalendar view;
    protected DateChooserPanel dateChooser;
    JComponent container;
    JLabel titleView;
    int units = 1;
    protected final Set<ObjectMenuFactory> objectMenuFactories;
    protected final MenuFactory menuFactory;
    protected final Provider<DateRenderer> dateRendererProvider;
    protected final CalendarSelectionModel calendarSelectionModel;
    protected final RaplaClipboard clipboard;
    protected final ReservationController reservationController;
    protected final InfoFactory infoFactory;
    protected final RaplaImages raplaImages;
    protected final DialogUiFactoryInterface dialogUiFactory;
    protected final AppointmentFormater appointmentFormater;
    protected final EditController editController;
    private final boolean printing;

    public AbstractRaplaSwingCalendar(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CalendarModel model, boolean editable,
            boolean printing, final Set<ObjectMenuFactory> objectMenuFactories, MenuFactory menuFactory, Provider<DateRenderer> dateRendererProvider,
            CalendarSelectionModel calendarSelectionModel, RaplaClipboard clipboard, ReservationController reservationController, InfoFactory infoFactory,
            RaplaImages raplaImages, DateRenderer dateRenderer, DialogUiFactoryInterface dialogUiFactory,
            IOInterface ioInterface, AppointmentFormater appointmentFormater, EditController editController) throws RaplaException
    {
        super(facade, i18n, raplaLocale, logger);
        this.model = model;
        this.printing = printing;
        this.objectMenuFactories = objectMenuFactories;
        this.menuFactory = menuFactory;
        this.dateRendererProvider = dateRendererProvider;
        this.calendarSelectionModel = calendarSelectionModel;
        this.clipboard = clipboard;
        this.reservationController = reservationController;
        this.infoFactory = infoFactory;
        this.raplaImages = raplaImages;
        this.dialogUiFactory = dialogUiFactory;
        this.appointmentFormater = appointmentFormater;
        this.editController = editController;

        boolean printable = isPrintContext();
        view = createView( !printable);
        view.setEditable(editable);
        view.setLocale( getRaplaLocale() );
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

        dateChooser = new DateChooserPanel(facade, i18n, raplaLocale, logger, model, dateRenderer, ioInterface);
        dateChooser.addDateChangeListener(this);
        dateChooser.setIncrementSize( getIncrementSize() );
    }

	protected boolean isPrintContext() {
        return printing;
	}

    abstract protected AbstractSwingCalendar createView(boolean showScrollPane) throws RaplaException;
    abstract protected void configureView() throws RaplaException;
    abstract public DateTools.IncrementSize getIncrementSize();

    /**
     * @throws RaplaException  
     */
    protected ViewListener createListener() throws RaplaException {
        return new RaplaCalendarViewListener(getClientFacade(), getI18n(), getRaplaLocale(), getLogger(), model, view.getComponent(), objectMenuFactories, menuFactory, calendarSelectionModel, clipboard, reservationController, infoFactory, raplaImages, dialogUiFactory, editController);
    }

    public JComponent getDateSelection()   {
        return dateChooser.getComponent();
    }

    public void dateChanged(DateChangeEvent evt) {
        try {
            // TODO think about asynchronous call
            final Promise<Void> update = update();
            PromiseSynchroniser.waitForWithRaplaException(update, 10000);
        } catch (RaplaException ex) {
            dialogUiFactory.showException(ex, new SwingPopupContext(view.getComponent(), null));
        }
    }

    public Promise<Void> update() {
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
        try
        {
            configureView( );
        }
        catch (RaplaException e)
        {
            return new ResolvedPromise<>(e);
        }
        Date startDate = getStartDate() ;
        Date endDate = getEndDate();
        ensureViewTimeframeIsInModel(startDate, endDate);
      

        final Promise<RaplaBuilder> builderPromise = createBuilder();
        final Promise<Void> voidPromise = builderPromise.thenAccept((builder) -> {
            view.rebuild( builder );
            if ( !view.isEditable())
            {
                Dimension size = view.getComponent().getPreferredSize();
                container.setBounds( 0,0, size.width, size.height + 40);
            }
        });
        return voidPromise;
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

   
    protected Promise<RaplaBuilder> createBuilder() 
    {
        RaplaBuilder builder = new SwingRaplaBuilder(getFacade(), getI18n(), getRaplaLocale(), getLogger(), appointmentFormater, raplaImages);
        Date startDate = getStartDate();
		Date endDate = getEndDate();
		final Promise<RaplaBuilder> builderPromise = builder.initFromModel( model, startDate, endDate );
		final Promise<RaplaBuilder> nextBuilderPromise = builderPromise.thenApply((initializedBuilder) -> {
		    initializedBuilder.setRepeatingVisible( view.isEditable());
		    GroupAllocatablesStrategy strategy = new GroupAllocatablesStrategy( getRaplaLocale().getLocale() );
		    boolean compactColumns = getCalendarOptions().isCompactColumns() ||  initializedBuilder.getAllocatables().size() ==0 ;
		    strategy.setFixedSlotsEnabled( !compactColumns);
		    strategy.setResolveConflictsEnabled( true );
		    initializedBuilder.setBuildStrategy( strategy );
		    return initializedBuilder;
		});
		return nextBuilderPromise;
    }

    public JComponent getComponent()
    {
        return container;
    }

    public List<Allocatable> getSortedAllocatables() throws RaplaException
    {
    	List<Allocatable> sortedAllocatables = model.getSelectedAllocatablesSorted();
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
        	targetDate = DateTools.add( selectedDate, getIncrementSize(), pages - 1);
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
		    		final Promise<Void> update = update();
		    		PromiseSynchroniser.waitForWithRaplaException(update, 10000);
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
                try
                {
                    final Promise<Void> update = update();
                    PromiseSynchroniser.waitForWithRaplaException(update, 10000);
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
                        currentPrintDate = DateTools.add( currentPrintDate, getIncrementSize(),  1);
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
                    currentPrintDate = DateTools.add( currentPrintDate, getIncrementSize(),  - 1);
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
    	DateTools.IncrementSize incrementSize = getIncrementSize();
        switch ( incrementSize)
        {
            case DAY_OF_YEAR:return getString("days");
            case WEEK_OF_YEAR:return getString("weeks");
            case MONTH:return getString("months");
            default: return "";
        }
    }
    
    public int getUnits() {
		return units;
	}

	public void setUnits(int units) {
		this.units = units;
	}


}
