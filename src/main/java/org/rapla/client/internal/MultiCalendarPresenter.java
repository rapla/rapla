
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

package org.rapla.client.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.internal.MultiCalendarView.Presenter;
import org.rapla.client.swing.SwingCalendarView;
import org.rapla.client.swing.VisibleTimeInterval;
import org.rapla.client.swing.extensionpoints.SwingViewFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.FilterEditButton.FilterEditButtonFactory;
import org.rapla.client.swing.toolkit.RaplaMenuItem;
import org.rapla.client.RaplaWidget;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;


public class MultiCalendarPresenter implements Presenter
{
    private static final String ERROR_NO_VIEW_DEFINED = "No views enabled. Please add a plugin in the menu admin/settings/plugins";
    
    private final Map<String,RaplaMenuItem> viewMenuItems = new HashMap<String,RaplaMenuItem>();
    private final CalendarSelectionModel model;
    private final Set<SwingViewFactory> factoryList;
    private final RaplaImages raplaImages;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final MultiCalendarView view;
    private final Logger logger;

    // Default view, when no plugin defined
    private String currentViewId;


    /** renderer for weekdays in month-view */
    boolean editable = true;
    boolean listenersEnabled = true;
    private SwingCalendarView currentView;
    private PresenterChangeCallback callback;
    
    @Inject
    public MultiCalendarPresenter(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CalendarSelectionModel model,
            RaplaImages raplaImages, DialogUiFactoryInterface dialogUiFactory, final Set<SwingViewFactory> factoryList,
            FilterEditButtonFactory filterEditButtonFactory, MultiCalendarView view) throws RaplaInitializationException
    {
        this.logger = logger;
        this.raplaImages = raplaImages;
        this.dialogUiFactory = dialogUiFactory;
        this.factoryList = factoryList;
        this.model = model;
        this.view = view;
        this.view.setPresenter(this);
        // Key name map
        final LinkedHashMap<String,String> ids = getIds();
        {
	         SwingViewFactory factory = findFactory( model.getViewId());
             if ( factory == null)
             {
                 if ( !ids.isEmpty() ) {
	                 String firstId = ids.keySet().iterator().next();
	                 model.setViewId( firstId );
	                 factory = findFactory( firstId );
	             }
	         }
             if(factory == null)
             {
                 view.setNoViewText(ERROR_NO_VIEW_DEFINED);
             }
             else
             {
                 try
                {
                    view.setCalendarView(factory.createSwingView(model, true, false));
                }
                catch (RaplaException e)
                {
                    throw new RaplaInitializationException(e);
                }
             }
         }
        view.setSelectedViewId(model.getViewId());
        view.setFilterModel(model);
        this.view.setSelectableViews(ids);
    }
    
    public void setCallback(PresenterChangeCallback callback)
    {
        this.callback = callback;
    }
    
    @Override
    public void onViewSelectionChange(PopupContext context)
    {
        if (!listenersEnabled)
            return;
        final String viewId = view.getSelectedViewId();
        try
        {
            selectView(viewId);
        }
        catch (RaplaException ex)
        {
            dialogUiFactory.showException(ex, context);
        }
    }
    
    @Override
    public void onFilterChange()
    {
        final ClassificationFilter[] filters = view.getFilters();
        model.setReservationFilter( filters );
        update();
    }

    public void init(boolean editable) throws RaplaException
    {
        this.editable = editable;
        update(null);
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
        	if ( viewMenuItems.size() > 0) {
            	for ( Iterator<RaplaMenuItem> it = viewMenuItems.values().iterator();it.hasNext();) 
                {
                    RaplaMenuItem item =  it.next();
                    item.setIcon( raplaImages.getIconFromKey("icon.empty"));
                }
                RaplaMenuItem item = viewMenuItems.get( viewId );
                item.setIcon( raplaImages.getIconFromKey("icon.radio"));
            }
        	callback.onChange();
        	view.setSelectedViewId(viewId);
        } finally {
        	listenersEnabled = true;
        }
    }

    private LinkedHashMap<String, String> getIds() {
        List<SwingViewFactory> sortedList = new ArrayList<SwingViewFactory>(factoryList);
        Collections.sort( sortedList, new Comparator<SwingViewFactory>() {
            public int compare( SwingViewFactory arg0, SwingViewFactory arg1 )
            {
                SwingViewFactory f1 = arg0;
                SwingViewFactory f2 = arg1;
                return f1.getMenuSortKey().compareTo( f2.getMenuSortKey() );
            }
        });
        final LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Iterator<SwingViewFactory> it = sortedList.iterator();it.hasNext();) {
            SwingViewFactory factory =  it.next();
            if(factory.isEnabled())
            {
                result.put(factory.getViewId(), factory.getName());
            }
        }
        return result;
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
            	logger.error("View with id " + viewId + " not found. Selecting first view.");
            	if( factoryList.size() == 0)
            	{
                	logger.error(ERROR_NO_VIEW_DEFINED);
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
                view.setSelectedViewId(viewId);
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
            	    currentViewId = viewId; 	
            	    view.setCalendarView(currentView);
            	    currentView.update();
            	}
            	else
            	{
            	    currentView = null;
            		view.setNoViewText(ERROR_NO_VIEW_DEFINED);
            		currentViewId = "ERROR_VIEW";
            	}
            } else {
                boolean update = true;
             	if  ( currentView != null && currentView instanceof VisibleTimeInterval)
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
        }
        finally
        {
            listenersEnabled = true;
        }
    }

    public void update()
    {
        if (currentView != null)
        {
            currentView.update();
        }
    }

    public void closeFilterButton()
    {
        view.closeFilterButton();
    }

    public RaplaWidget provideContent()
    {
        return view;
    }

    public void scrollToStart()
    {
        if(currentView != null)
        {
            currentView.scrollToStart();
        }
    }




}
