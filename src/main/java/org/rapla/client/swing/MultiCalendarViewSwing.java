
/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copyReservations of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/

package org.rapla.client.swing;

import org.rapla.client.internal.MultiCalendarView;
import org.rapla.client.swing.extensionpoints.SwingViewFactory;
import org.rapla.client.swing.internal.FilterEditButton;
import org.rapla.client.swing.internal.FilterEditButton.FilterEditButtonFactory;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.RaplaMenuItem;
import org.rapla.components.calendar.RaplaArrowButton;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.facade.ClassifiableFilter;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@DefaultImplementation(context = InjectionContext.swing, of = MultiCalendarView.class)
public class MultiCalendarViewSwing implements MultiCalendarView
{
    private final JPanel page = new JPanel();
    private final JPanel header = new JPanel();
    Map<String, RaplaMenuItem> viewMenuItems = new HashMap<>();
    private final JComboBox viewChooser;

    /** renderer for weekdays in month-view */
    private FilterEditButton filter;
    private LinkedHashMap<String, SwingViewFactory> viewIdToViewName;
    private final JPanel filterContainer = new JPanel();
    private final FilterEditButtonFactory filterEditButtonFactory;
    private boolean viewSelectionFromPromgramm = false;
    
    @Inject
    public MultiCalendarViewSwing(FilterEditButtonFactory filterEditButtonFactory) throws RaplaInitializationException
    {
        this.filterEditButtonFactory = filterEditButtonFactory;

        viewChooser = new JComboBox();
        viewChooser.addActionListener((ActionEvent evt) ->
        {
            if(!viewSelectionFromPromgramm)
            {
                final SwingPopupContext context = new SwingPopupContext(page, null);
                getPresenter().onViewSelectionChange(context);
            }
        });
        addTypeChooser();
        page.setSize(800,600);
        page.setPreferredSize( new Dimension(800,600));
        header.setLayout(new BorderLayout());
        header.add(viewChooser, BorderLayout.CENTER);
        filterContainer.setLayout(new BorderLayout());
        header.add(filterContainer, BorderLayout.SOUTH);
        page.setBackground(Color.white);
        page.setLayout(new TableLayout(new double[][] { { TableLayout.PREFERRED, TableLayout.FILL }, { TableLayout.PREFERRED, TableLayout.FILL } }));

    }
    
    private Presenter presenter;

    @Override
    public void setPresenter(Presenter presenter) {
        this.presenter = presenter;
    }
    
    protected Presenter getPresenter() {
        return presenter;
    }
    

    @Override
    public void setFilterModel(ClassifiableFilter model)
    {
        ChangeListener listener = (evt) ->
        {
            getPresenter().onFilterChange();
        };
        final FilterEditButton filterEditButton = filterEditButtonFactory.create(model, false, listener);
        filter = filterEditButton;
        filterContainer.add(filter.getButton(), BorderLayout.WEST);
    }

    @Override
    public void setSelectedViewId(String viewId)
    {
        viewSelectionFromPromgramm = true;
        try
        {
            viewChooser.setSelectedItem(viewId);
        }
        finally
        {
            viewSelectionFromPromgramm = false;
        }
    }

    @Override
    public void setSelectableViews(LinkedHashMap<String, SwingViewFactory> viewIdToViewName)
    {
        this.viewIdToViewName = viewIdToViewName;
        viewChooser.setMaximumRowCount(viewIdToViewName.size());
        viewChooser.setModel(new DefaultComboBoxModel<>(viewIdToViewName.keySet().toArray(new String[] {})));
        viewChooser.setVisible(viewChooser.getModel().getSize() > 0);
    }

    @SuppressWarnings({ "unchecked" })
    private void addTypeChooser()
    {
        viewChooser.setRenderer(new DefaultListCellRenderer()
        {
            private static final long serialVersionUID = 1L;

            public Component getListCellRendererComponent(JList arg0, Object selectedItem, int index, boolean arg3, boolean arg4)
            {
                super.getListCellRendererComponent(arg0, selectedItem, index, arg3, arg4);
                if (selectedItem == null)
                {
                    setIcon(null);
                }
                else
                {
                    final SwingViewFactory swingViewFactory = viewIdToViewName.get(selectedItem);
                    final String viewName = swingViewFactory.getName();
                    final Icon icon = swingViewFactory.getIcon();
                    setText(viewName);
                    setIcon(icon);
                }
                return this;
            }
        });
    }

    @Override
    public ClassificationFilter[] getFilters()
    {
        return filter.getFilterUI().getFilters();
    }

    public RaplaArrowButton getFilterButton()
    {
        return filter.getButton();
    }

    /*
    private void addMenuItem( CalendarSelectionModel model, String[] ids, RaplaMenu view )
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
    */

    @Override
    public void setCalendarView(SwingCalendarView calendarView)
    {
        page.removeAll();
        page.add(header, "0,0,f,f");
        JComponent dateSelection = calendarView.getDateSelection();
        if (dateSelection != null)
            page.add(dateSelection, "1,0,f,f");
        JComponent component = (JComponent) calendarView.getComponent();
        page.add(component, "0,1,1,1,f,f");
        component.setBorder(BorderFactory.createEtchedBorder());
        page.setVisible(false);
        page.invalidate();
        page.setVisible(true);
    }

    @Override
    public String getSelectedViewId()
    {
        return (String) viewChooser.getSelectedItem();
    }

    @Override
    public void closeFilterButton()
    {
        if (filter.getButton().isOpen())
        {
            filter.getButton().doClick();
        }
    }

    public JComponent getComponent()
    {
        return page;
    }

    @Override
    public void setNoViewText(String errorText)
    {
        page.removeAll();
        page.add(new JLabel(errorText));
        page.invalidate();
    }

}
