
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

package org.rapla.client.swing.internal.common;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.SwingCalendarView;
import org.rapla.client.swing.VisibleTimeInterval;
import org.rapla.client.swing.extensionpoints.SwingViewFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.CalendarEditor;
import org.rapla.client.swing.internal.FilterEditButton;
import org.rapla.client.swing.internal.FilterEditButton.FilterEditButtonFactory;
import org.rapla.client.swing.internal.RaplaMenuBarContainer;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.internal.edit.ClassifiableFilterEdit;
import org.rapla.client.swing.internal.edit.fields.BooleanField.BooleanFieldFactory;
import org.rapla.client.swing.internal.edit.fields.DateField.DateFieldFactory;
import org.rapla.client.swing.toolkit.IdentifiableMenuEntry;
import org.rapla.client.swing.toolkit.RaplaMenu;
import org.rapla.client.swing.toolkit.RaplaMenuItem;
import org.rapla.client.swing.toolkit.RaplaWidget;
import org.rapla.components.calendar.RaplaArrowButton;
import org.rapla.components.layout.TableLayout;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class MultiCalendarView extends RaplaGUIComponent
    implements
    RaplaWidget, Disposable, ChangeListener
{
    
    private final JPanel page = new JPanel();
    private final JPanel header = new JPanel();
    Map<String,RaplaMenuItem> viewMenuItems = new HashMap<String,RaplaMenuItem>();
    JComboBox viewChooser;
    List<ChangeListener> listeners = new ArrayList<ChangeListener>();
    
    // Default view, when no plugin defined
	String ERROR_NO_VIEW_DEFINED = "No views enabled. Please add a plugin in the menu admin/settings/plugins";
    private SwingCalendarView defaultView = new SwingCalendarView() {
        JLabel noViewDefined = new JLabel(ERROR_NO_VIEW_DEFINED);
        JPanel test =new JPanel();
        {
        	test.add( noViewDefined);
        }
        public JComponent getDateSelection()
        {
            return null;
        }

        public void scrollToStart()
        {
        }

        public JComponent getComponent()
        {
            return test;
        }

        public void update( ) throws RaplaException
        {
        }

    };
    
    private SwingCalendarView currentView = defaultView;
    String currentViewId;


    private final CalendarSelectionModel model;
    final Set<SwingViewFactory> factoryList;
    /** renderer for weekdays in month-view */
    boolean editable = true;
    boolean listenersEnabled = true;
    FilterEditButton filter;
    CalendarEditor calendarEditor;
    private final RaplaImages raplaImages;
    private final DialogUiFactoryInterface dialogUiFactory;
    
    public MultiCalendarView(final RaplaMenuBarContainer menuBar,ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CalendarSelectionModel model,
            CalendarEditor calendarEditor, RaplaImages raplaImages, DateFieldFactory dateFieldFactory, DialogUiFactoryInterface dialogUiFactory,
            BooleanFieldFactory booleanFieldFactory, final Set<SwingViewFactory> factoryList, FilterEditButtonFactory filterEditButtonFactory)
                    throws RaplaException
    {
        this(menuBar,facade, i18n, raplaLocale, logger, model, raplaImages, dateFieldFactory, dialogUiFactory, booleanFieldFactory, factoryList,
                filterEditButtonFactory, true);
        this.calendarEditor = calendarEditor;
    }

    public MultiCalendarView(final RaplaMenuBarContainer menuBar,ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CalendarSelectionModel model,
            RaplaImages raplaImages, DateFieldFactory dateFieldFactory, DialogUiFactoryInterface dialogUiFactory, BooleanFieldFactory booleanFieldFactory,
            final Set<SwingViewFactory> factoryList, FilterEditButtonFactory filterEditButtonFactory, boolean editable) throws RaplaException
    {
        super(facade, i18n, raplaLocale, logger);
        this.raplaImages = raplaImages;
        this.dialogUiFactory = dialogUiFactory;
        this.factoryList = factoryList;
        this.editable = editable;
        this.model = model;
        String[] ids = getIds();
        {
	         SwingViewFactory factory = findFactory( model.getViewId());
             if ( factory == null)
             {
                 if ( ids.length != 0 ) {
	                 String firstId = ids[0];
	                 model.setViewId( firstId );
	                 factory = findFactory( firstId );
	             }
	         }
         }
        RaplaMenu view = menuBar.getViewMenu();
        if ( !view.hasId( "views") ) 
        {
            addMenu( model, ids, view );
        }

        addTypeChooser( ids );
        header.setLayout(new BorderLayout());
        header.add( viewChooser, BorderLayout.CENTER);
        final ChangeListener listener = this;
        filter = filterEditButtonFactory.create(model,false, listener);
        final JPanel filterContainer = new JPanel();
        filterContainer.setLayout( new BorderLayout());
        filterContainer.add(filter.getButton(), BorderLayout.WEST);
        header.add( filterContainer, BorderLayout.SOUTH);
        page.setBackground( Color.white );
        page.setLayout(new TableLayout( new double[][]{
                {TableLayout.PREFERRED, TableLayout.FILL}
                ,{TableLayout.PREFERRED, TableLayout.FILL}}));
        update(null);
    }
	
    public void dispose() {
        
    }


	@SuppressWarnings("unchecked")
	private void addTypeChooser( String[] ids )
    {
		viewChooser = new JComboBox( ids);
        viewChooser.setVisible( viewChooser.getModel().getSize() > 0);
        viewChooser.setMaximumRowCount(ids.length);
        viewChooser.setSelectedItem( getModel().getViewId() );
        viewChooser.addActionListener( new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
            	if ( !listenersEnabled )
            		return;
                String viewId = (String) ((JComboBox)evt.getSource()).getSelectedItem();
                try {
                    selectView( viewId );
                } catch (RaplaException ex) {
                    dialogUiFactory.showException(ex, new SwingPopupContext(page, null));
                }
            }
        }
        );
        viewChooser.setRenderer( new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;
            
            public Component getListCellRendererComponent(JList arg0, Object selectedItem, int index, boolean arg3, boolean arg4) {
                super.getListCellRendererComponent( arg0, selectedItem, index, arg3, arg4);
                if ( selectedItem == null) {
                    setIcon( null );
                } else {
                    SwingViewFactory factory = findFactory( (String)selectedItem);
                    setText( factory.getName() );
                    setIcon( factory.getIcon());
                }
                return this;
            }
        });
    }
    
	public void addValueChangeListener(ChangeListener changeListener) {
		listeners .add( changeListener);
	}

	public void removeValueChangeListener(ChangeListener changeListener) {
		listeners .remove( changeListener);
	}
    public RaplaArrowButton getFilterButton() 
    {
    	return filter.getButton();
    }

    public void stateChanged(ChangeEvent e) {
        try {
            ClassifiableFilterEdit filterUI = filter.getFilterUI();
            if ( filterUI != null)
            {
            	final ClassificationFilter[] filters = filterUI.getFilters();
            	model.setReservationFilter( filters );
            	update(null);
            }
        } catch (Exception ex) {
            dialogUiFactory.showException(ex, new SwingPopupContext(getComponent(), null));
        }
    }
    private void addMenu( CalendarSelectionModel model, String[] ids, RaplaMenu view )
    {
        RaplaMenu viewMenu = new RaplaMenu("views");
        viewMenu.setText(getString("show_as"));
        view.insertBeforeId( viewMenu, "show_tips");
        ButtonGroup group = new ButtonGroup();
        for (int i=0;i<ids.length;i++)
        {
            String id = ids[i];
            RaplaMenuItem viewItem = new RaplaMenuItem( id);
            if ( id.equals( model.getViewId()))
            {
                viewItem.setIcon( raplaImages.getIconFromKey("icon.radio"));
             }  
            else
             {  
                 viewItem.setIcon( raplaImages.getIconFromKey("icon.empty"));
             }
        	 group.add( viewItem );
             SwingViewFactory factory = findFactory( id );
             viewItem.setText( factory.getName() );
             viewMenu.add( viewItem );
             viewItem.setSelected( id.equals( getModel().getViewId()));
             viewMenuItems.put( id, viewItem );
             viewItem.addActionListener( new ActionListener() {
   
        		public void actionPerformed(ActionEvent evt) {
                	if ( !listenersEnabled )
                		return;
                    String viewId = ((IdentifiableMenuEntry)evt.getSource()).getId();
                    try {
                        selectView( viewId );
                    } catch (RaplaException ex) {
                        dialogUiFactory.showException(ex, new SwingPopupContext(page, null));
                    }
        		}
   
             });
         }
    }

    private SwingViewFactory findFactory(String id) {
        for (Iterator<SwingViewFactory> it = factoryList.iterator();it.hasNext();) {
            SwingViewFactory factory =  it.next();
            if ( factory.getViewId().equals( id ) ) {
                return factory;
            }
        }
        return null;
    }

    private void selectView(String viewId) throws RaplaException {
    	listenersEnabled = false;
        try {
        	getModel().setViewId( viewId );
        	update(null);
        	getSelectedCalendar().scrollToStart();
        	if ( viewMenuItems.size() > 0) {
            	for ( Iterator<RaplaMenuItem> it = viewMenuItems.values().iterator();it.hasNext();) 
                {
                    RaplaMenuItem item =  it.next();
                    item.setIcon( raplaImages.getIconFromKey("icon.empty"));
                }
                RaplaMenuItem item = viewMenuItems.get( viewId );
                item.setIcon( raplaImages.getIconFromKey("icon.radio"));
            }
        	for(ChangeListener listener:listeners)
        	{
        		listener.stateChanged( new ChangeEvent( this));
        	}
        	viewChooser.setSelectedItem( viewId );
        } finally {
        	listenersEnabled = true;
        }
    }

    private String[] getIds() {
        List<SwingViewFactory> sortedList = new ArrayList<SwingViewFactory>(factoryList);
        Collections.sort( sortedList, new Comparator<SwingViewFactory>() {
            public int compare( SwingViewFactory arg0, SwingViewFactory arg1 )
            {
                SwingViewFactory f1 = arg0;
                SwingViewFactory f2 = arg1;
                return f1.getMenuSortKey().compareTo( f2.getMenuSortKey() );
            }
        });
        List<String> list = new ArrayList<String>();
        for (Iterator<SwingViewFactory> it = sortedList.iterator();it.hasNext();) {
            SwingViewFactory factory =  it.next();
            if(factory.isEnabled())
            {
                list.add(factory.getViewId());
            }
        }
        return list.toArray( RaplaObject.EMPTY_STRING_ARRAY);
    }

    public CalendarSelectionModel getModel() {
        return model;
    }

    public void update(ModificationEvent evt) throws RaplaException {
        try
        {
        	// don't show filter button in template mode
        	//filter.getButton().setVisible( getModification().getTemplate() == null);
            listenersEnabled = false;
            String viewId = model.getViewId();
            SwingViewFactory factory = findFactory( viewId );
            if ( factory == null ) 
            {
            	getLogger().error("View with id " + viewId + " not found. Selecting first view.");
            	if( factoryList.size() == 0)
            	{
                	getLogger().error(ERROR_NO_VIEW_DEFINED);
            		viewId =null;
            	}
            	else
            	{
            		factory = factoryList.iterator().next();
            		viewId = factory.getViewId();
            	}
            }

            if ( factory != null)
            {
            	viewChooser.setSelectedItem( viewId );
            }
            else
            {
            	viewId = "ERROR_VIEW";
            }
            
            if ( currentViewId == null || !currentViewId.equals( viewId) ) {
                Collection<TimeInterval> emptySet = Collections.emptySet();
                model.setMarkedIntervals(emptySet, false);
            	if ( factory != null)
            	{
            		currentView = factory.createSwingView( model, editable, false);
            	    currentViewId = viewId; 	}
            	else
            	{
            		currentView = defaultView;
            		currentViewId = "ERROR_VIEW";
            	}
            	currentView.update();
            	page.removeAll();
            	page.add( header, "0,0,f,f");
                JComponent dateSelection = currentView.getDateSelection();
				if ( dateSelection != null)
                    page.add( dateSelection, "1,0,f,f" );
                JComponent component = (JComponent) currentView.getComponent();
				page.add( component, "0,1,1,1,f,f" );
				component.setBorder( BorderFactory.createEtchedBorder());
				page.setVisible(false);
				page.invalidate();
				page.setVisible(  true);
           
            } else {
                boolean update = true;
             	if  ( currentView instanceof VisibleTimeInterval)
             	{
             		TimeInterval visibleTimeInterval = ((VisibleTimeInterval) currentView).getVisibleTimeInterval();
             		if  ( evt != null && !evt.isModified() && visibleTimeInterval != null)
             		{
             			TimeInterval invalidateInterval = evt.getInvalidateInterval();
             			if ( invalidateInterval != null && !invalidateInterval.overlaps( visibleTimeInterval))
             			{
             				update = false;
             			}
             		}
             	}
             	if ( update )
             	{
             		currentView.update( );
             	}
            }
            if ( calendarEditor != null)
            {
            	calendarEditor.updateOwnReservationsSelected();
            }
        }
        finally
        {
            listenersEnabled = true;
        }
    }


	public SwingCalendarView getSelectedCalendar() {
        return currentView;
    }

    public JComponent getComponent() {
        return page;
    }

    @Singleton
    public static class MultiCalendarViewFactory
    {

        private final ClientFacade facade;
        private final RaplaResources i18n;
        private final RaplaLocale raplaLocale;
        private final Logger logger;
        private final CalendarSelectionModel model;
        private final RaplaImages raplaImages;
        private final DateFieldFactory dateFieldFactory;
        private final DialogUiFactoryInterface dialogUiFactory;
        private final Set<SwingViewFactory> factoryList;
        private final BooleanFieldFactory booleanFieldFactory;
        private final FilterEditButtonFactory filterEditButtonFactory;
        private final RaplaMenuBarContainer menuBar;

        @Inject
        public MultiCalendarViewFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CalendarSelectionModel model,
                RaplaImages raplaImages, DateFieldFactory dateFieldFactory, DialogUiFactoryInterface dialogUiFactory, final Set<SwingViewFactory> factoryList,
                BooleanFieldFactory booleanFieldFactory, FilterEditButtonFactory filterEditButtonFactory, RaplaMenuBarContainer menuBar)
        {
            this.facade = facade;
            this.i18n = i18n;
            this.raplaLocale = raplaLocale;
            this.logger = logger;
            this.model = model;
            this.raplaImages = raplaImages;
            this.dateFieldFactory = dateFieldFactory;
            this.dialogUiFactory = dialogUiFactory;
            this.factoryList = factoryList;
            this.booleanFieldFactory = booleanFieldFactory;
            this.filterEditButtonFactory = filterEditButtonFactory;
            this.menuBar = menuBar;
        }

        public MultiCalendarView create(boolean editable)
        {
            return new MultiCalendarView(menuBar,facade, i18n, raplaLocale, logger, model, raplaImages, dateFieldFactory, dialogUiFactory, booleanFieldFactory,
                    factoryList, filterEditButtonFactory, editable);
        }

        public MultiCalendarView create(CalendarEditor calendarEditor)
        {
            return new MultiCalendarView(menuBar,facade, i18n, raplaLocale, logger, model, calendarEditor, raplaImages, dateFieldFactory, dialogUiFactory,
                    booleanFieldFactory, factoryList, filterEditButtonFactory);
        }
    }

}
