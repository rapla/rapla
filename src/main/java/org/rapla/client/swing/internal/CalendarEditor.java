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

package org.rapla.client.swing.internal;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.rapla.client.extensionpoints.PublishExtensionFactory;
import org.rapla.client.swing.EditController;
import org.rapla.client.swing.InfoFactory;
import org.rapla.client.swing.MenuFactory;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.TreeFactory;
import org.rapla.client.swing.extensionpoints.SwingViewFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.action.SaveableToggleAction;
import org.rapla.client.swing.internal.common.InternMenus;
import org.rapla.client.swing.internal.common.MultiCalendarView;
import org.rapla.client.swing.toolkit.DialogUI;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.client.swing.toolkit.RaplaMenu;
import org.rapla.client.swing.toolkit.RaplaMenuItem;
import org.rapla.client.swing.toolkit.RaplaWidget;
import org.rapla.components.calendar.DateRenderer;
import org.rapla.entities.Entity;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ModificationEvent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.storage.UpdateResult;

final public class CalendarEditor extends RaplaGUIComponent implements RaplaWidget {
	public static final TypedComponentRole<Boolean> SHOW_CONFLICTS_CONFIG_ENTRY = new TypedComponentRole<Boolean>("org.rapla.showConflicts");
	public static final TypedComponentRole<Boolean> SHOW_SELECTION_CONFIG_ENTRY = new TypedComponentRole<Boolean>("org.rapla.showSelection");
	public static final String SHOW_SELECTION_MENU_ENTRY = "show_resource_selection";
	public static final String SHOW_CONFLICTS_MENU_ENTRY = "show_conflicts";

    RaplaMenuItem ownReservationsMenu;

	JSplitPane content = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    final private ResourceSelection resourceSelection;
    final private SavedCalendarView savedViews;
    final private ConflictSelection conflictsView;
    final public MultiCalendarView calendarContainer;
    
    final JToolBar minimized;
    final JToolBar templatePanel;
    final JPanel left;
    boolean listenersDisabled = false;
    private final RaplaImages raplaImages;
    @Inject
    public CalendarEditor(RaplaContext context,CalendarSelectionModel model,final Set<SwingViewFactory> factoryList,Set<PublishExtensionFactory> extensionFactories, TreeFactory treeFactory, MenuFactory menuFactory, EditController editController, InfoFactory<Component, DialogUI> infoFactory, RaplaImages raplaImages, DateRenderer dateRenderer) throws RaplaException {
        super(context);
        this.raplaImages = raplaImages;

        calendarContainer = new MultiCalendarView(context, model, this, treeFactory, raplaImages, dateRenderer, factoryList);
        calendarContainer.addValueChangeListener(new ChangeListener()
        {

			public void stateChanged(ChangeEvent e) {
				if ( listenersDisabled)
				{
					return;
				}
				try {
					resourceSelection.updateMenu();
				} catch (RaplaException e1) {
					getLogger().error(e1.getMessage(), e1);
				}
			}
        	
        });
        resourceSelection = new ResourceSelection(context, calendarContainer, model, treeFactory, menuFactory, editController, infoFactory, raplaImages, dateRenderer);
        final ChangeListener treeListener = new ChangeListener() {
	          public void stateChanged(ChangeEvent e) {
	        	  if ( listenersDisabled)
	        	  {
	        		  return;
	        	  }
	        	  conflictsView.clearSelection();
	          }
	      };

	      
        final RaplaMenu viewMenu = getService( InternMenus.VIEW_MENU_ROLE);
        ownReservationsMenu = new RaplaMenuItem("only_own_reservations");
        ownReservationsMenu.setText( getString("only_own_reservations"));
        ownReservationsMenu = new RaplaMenuItem("only_own_reservations");
        ownReservationsMenu.addActionListener(new ActionListener() {
			
			public void actionPerformed(ActionEvent e) {
		    	boolean isSelected = model.isOnlyCurrentUserSelected();
		    	// switch selection options
		    	model.setOption( CalendarModel.ONLY_MY_EVENTS, isSelected ? "false":"true");
		    	updateOwnReservationsSelected();
		    	try {
	               	Entity preferences = getQuery().getPreferences();
	   		    	UpdateResult modificationEvt = new UpdateResult( getUser());
	   		    	modificationEvt.addOperation( new UpdateResult.Change(preferences, preferences));
	   				resourceSelection.dataChanged(modificationEvt);
	   				calendarContainer.update(modificationEvt);
	   				conflictsView.dataChanged( modificationEvt);
		    	} catch (Exception ex) {
                   showException(ex, getComponent());
		    	}

			}
		});
        ownReservationsMenu.setText( getString("only_own_reservations"));
        ownReservationsMenu.setIcon( raplaImages.getIconFromKey("icon.unchecked"));
        updateOwnReservationsSelected();


        viewMenu.insertBeforeId( ownReservationsMenu, "show_tips" );

        resourceSelection.getTreeSelection().addChangeListener( treeListener);
        conflictsView = new ConflictSelection(context, calendarContainer, model, treeFactory);
        left = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridheight = 1;
        c.gridx = 1;
        c.gridy = 1;
        c.weightx = 0;
        c.weighty = 0;
        c.anchor = GridBagConstraints.EAST;
        final JButton max  = new JButton();
        final JButton tree  = new JButton();
        tree.setEnabled( false );
        minimized = new JToolBar(JToolBar.VERTICAL);
        minimized.setFloatable( false);
        minimized.add( max);
        minimized.add( tree);
        
        
        max.setIcon( UIManager.getDefaults().getIcon("InternalFrame.maximizeIcon"));
        tree.setIcon( raplaImages.getIconFromKey("icon.tree"));
        JButton min = new RaplaButton(RaplaButton.SMALL);
        ActionListener minmaxAction = new ActionListener() 
        {
			public void actionPerformed(ActionEvent e) 
			{
				savedViews.closeFilter();
				int index = viewMenu.getIndexOfEntryWithId(SHOW_SELECTION_MENU_ENTRY);
				JMenuItem component = (JMenuItem)viewMenu.getMenuComponent( index);
				component.getAction().actionPerformed(e);
				final boolean newSelected = !component.isSelected();
				component.setSelected(newSelected);
			    javax.swing.ToolTipManager.sharedInstance().setEnabled(newSelected);
			    component.setIcon(newSelected ? raplaImages.getIconFromKey("icon.checked"):raplaImages.getIconFromKey("icon.unchecked"));
			}
		};
		min.addActionListener( minmaxAction);
		max.addActionListener( minmaxAction);
        tree.addActionListener( minmaxAction);
        
        templatePanel = new JToolBar(JToolBar.VERTICAL);
        templatePanel.setFloatable( false);
        final JButton exitTemplateEdit  = new JButton();
        //exitTemplateEdit.setIcon(raplaImages.getIconFromKey("icon.close"));
        exitTemplateEdit.setText(getString("close-template"));
        templatePanel.add( exitTemplateEdit);
        exitTemplateEdit.addActionListener( new ActionListener() {
			
			public void actionPerformed(ActionEvent e) {
				getModification().setTemplate( null );
				
			}
		});
        
		Icon icon = UIManager.getDefaults().getIcon("InternalFrame.minimizeIcon");
        min.setIcon( icon) ;
        //left.add(min, c);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
         c.gridy = 1;
        JPanel jp = new JPanel();
        jp.setLayout( new BorderLayout());
        
        savedViews = new SavedCalendarView(context, calendarContainer, resourceSelection, model, extensionFactories, infoFactory, raplaImages);
        jp.add( savedViews.getComponent(), BorderLayout.CENTER );
        templatePanel.setVisible( false);
        jp.add( templatePanel, BorderLayout.WEST );
        JToolBar mintb =new JToolBar();
        mintb.setFloatable( false);
       // mintb.add( min);
        min.setAlignmentY( JButton.TOP);
        jp.add( min, BorderLayout.EAST);
        left.add(jp, c);
        c.fill = GridBagConstraints.BOTH;
        c.gridy = 2;
        c.weightx = 1;
        c.weighty = 2.5;
        left.add(resourceSelection.getComponent(), c);
        c.weighty = 1.0;
        c.gridy = 3;
        left.add(conflictsView.getComponent(), c);
        c.weighty = 0.0;
        c.fill = GridBagConstraints.NONE;
        c.gridy = 4;
        c.anchor = GridBagConstraints.WEST;
        left.add(conflictsView.getSummaryComponent(), c);
        content.setRightComponent(calendarContainer.getComponent());
        updateViews();
    }
    
    
	public void updateOwnReservationsSelected() 
	{
		final CalendarSelectionModel model = resourceSelection.getModel();
		boolean isSelected = model.isOnlyCurrentUserSelected();
		ownReservationsMenu.setIcon(isSelected ? raplaImages.getIconFromKey("icon.checked") : raplaImages.getIconFromKey("icon.unchecked"));
		ownReservationsMenu.setSelected(isSelected);
	}

    public void dataChanged(ModificationEvent evt) throws RaplaException {
    	listenersDisabled = true;
        try
        {
	    	resourceSelection.dataChanged(evt);
	        conflictsView.dataChanged(evt);
	        calendarContainer.update(evt);
	        savedViews.update();
	        updateViews();
	        // this is done in calendarContainer update
 	        //updateOwnReservationsSelected();
        }
        finally
        {
        	listenersDisabled = false;
        }
    }

    private void updateViews() throws RaplaException {
    	
    	boolean showConflicts = getClientFacade().getPreferences().getEntryAsBoolean( SHOW_CONFLICTS_CONFIG_ENTRY, true);
        boolean showSelection = getClientFacade().getPreferences().getEntryAsBoolean( SHOW_SELECTION_CONFIG_ENTRY, true);
      
        conflictsView.getComponent().setVisible( showConflicts);
        conflictsView.getSummaryComponent().setVisible( !showConflicts );
        
        boolean templateMode = getModification().getTemplate() != null;
        if ( templateMode)
        {
            conflictsView.getComponent().setVisible(false);
            conflictsView.getSummaryComponent().setVisible( false);
        }
//        if ( templateMode)
//        {
//        	if ( content.getLeftComponent() != templatePanel)
//        	{
//        		content.setLeftComponent( templatePanel);
//				content.setDividerSize(0);
//        	}
//        }
//        else 
//        {
	  		if (  showSelection )
			{
	  	        savedViews.getComponent().setVisible( !templateMode  );
	  	        templatePanel.setVisible( templateMode);
	  	        //resourceSelection.getFilterButton().setVisible( !templateMode );
	  		    if ( content.getLeftComponent() != left )
				{
					content.setLeftComponent( left);
					content.setDividerSize( 5);
					content.setDividerLocation(285); 
				}
			}
			else if ( content.getLeftComponent() != minimized)
			{
		    	content.setLeftComponent( minimized);
				content.setDividerSize(0);
			}
//        }
    }


	public void start()  {
        calendarContainer.getSelectedCalendar().scrollToStart();
    }

    public JComponent getComponent() {
        return content;
    }

}
