/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
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
package org.rapla.client.swing.internal.edit.fields;

import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.TreeFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.edit.fields.DateField.DateFieldFactory;
import org.rapla.client.swing.internal.edit.fields.LongField.LongFieldFactory;
import org.rapla.components.calendar.DateRenderer;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.Category;
import org.rapla.entities.NamedComparator;
import org.rapla.entities.User;
import org.rapla.entities.domain.Permission;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

public class PermissionField extends AbstractEditField implements  ChangeListener, ActionListener {
    SetGetField<Category> groupSelect;
    ListField<User> userSelect;
    JLabel userLabel;
    
    JPanel panel = new JPanel();
    JPanel reservationPanel;
    Permission permission;

    JComboBox startSelection = new JComboBox();
    JComboBox endSelection = new JComboBox();
    DateField startDate;
    DateField endDate;
    LongField minAdvance;
    LongField maxAdvance;

    ListField<Permission.AccessLevel> accessField;

    Collection<Permission.AccessLevel> permissionLevels = Arrays.asList(Permission.AccessLevel.values());

    boolean eventType;
    
  
    @SuppressWarnings("unchecked")
	public PermissionField(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TreeFactory treeFactory, RaplaImages raplaImages, DialogUiFactoryInterface dialogUiFactory, DateFieldFactory dateFieldFactory, LongFieldFactory longFieldFactory) throws RaplaException {
        super(facade, i18n, raplaLocale, logger);

        panel.setBorder(BorderFactory.createEmptyBorder(5,8,5,8));

        double pre =TableLayout.PREFERRED;
        double fill =TableLayout.FILL;
        panel.setLayout( new TableLayout( new double[][]
            {{fill, 5},  // Columns
             {pre,5,pre,5,pre}} // Rows
                                          ));

        JPanel userPanel = new JPanel();
        panel.add( userPanel , "0,0,f,f" );
        userPanel.setLayout( new TableLayout( new double[][]
            {{pre, 10, fill, 5},  // Columns
             {pre,5,pre,5,pre}} // Rows
                                          ));

        userSelect = new UserListField( facade, i18n, raplaLocale, logger );
        userLabel = new JLabel(getString("user") + ":");
        userPanel.add( userLabel, "0,0,l,f" );
        userPanel.add( userSelect.getComponent(),"2,0,l,f" );

        Category rootCategory =   getQuery().getUserGroupsCategory();
        if ( rootCategory != null) {
            AbstractEditField groupSelect;
            if (rootCategory.getDepth() > 2) {
                CategorySelectField field= new CategorySelectField(facade, i18n, raplaLocale, logger, treeFactory, raplaImages, dialogUiFactory, rootCategory);
                this.groupSelect = field;
                groupSelect = field;
            } else {
                CategoryListField field = new CategoryListField(facade, i18n, raplaLocale, logger, rootCategory);
                this.groupSelect = field;
                groupSelect = field;
            }
            userPanel.add( new JLabel(getString("group") + ":"), "0,2,l,f" );
            userPanel.add( groupSelect.getComponent(),"2,2,l,f" );
            groupSelect.addChangeListener( this );
           
        }


        reservationPanel = new JPanel();
        panel.add( reservationPanel , "0,2,f,f" );
        reservationPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), getString("allocatable_in_timeframe") + ":" ));
        reservationPanel.setLayout( new TableLayout( new double[][]
            {{pre,3, pre, 5, pre, 5},  // Columns
             {pre, 5, pre}} // Rows
                                                     ));

        reservationPanel.add( new JLabel( getString("start_date") + ":" ) , "0,0,l,f" );
        reservationPanel.add( startSelection , "2,0,l,f" );
        startSelection.setModel( createSelectionModel() );
        startSelection.setSelectedIndex( 0 );

        startDate = dateFieldFactory.create();
        reservationPanel.add( startDate.getComponent() , "4,0,l,f" );

        minAdvance = longFieldFactory.create(new Long(0) );
        reservationPanel.add( minAdvance.getComponent() , "4,0,l,f" );

        reservationPanel.add( new JLabel( getString("end_date") + ":" ), "0,2,l,f" );
        reservationPanel.add( endSelection , "2,2,l,f" );
        endSelection.setModel( createSelectionModel() );
        endSelection.setSelectedIndex( 0 );

        endDate = dateFieldFactory.create();
        reservationPanel.add( endDate.getComponent() , "4,2,l,f" );

        maxAdvance = longFieldFactory.create(new Long(1) );
        reservationPanel.add( maxAdvance.getComponent() , "4,2,l,f" );

        userPanel.add( new JLabel(getString("permission.access") + ":"), "0,4,f,f" );
        accessField = new ListField<Permission.AccessLevel>(facade, i18n, raplaLocale, logger, permissionLevels );
        accessField.setRenderer( new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Permission.AccessLevel intValue = null;
                if (value != null) {
                   intValue = ((Permission.AccessLevel) value);
                   String key = intValue.name().toLowerCase();
                   if ( key.equalsIgnoreCase(Permission.CREATE.name()))
                   {
                       String typeName = eventType ? getString("reservation") : getString("resource");
                       value = getI18n().format("permission." + key, typeName );
                   }
                   else  if (key.equalsIgnoreCase(Permission.READ_TYPE.name()))
                   {
                       String typeName = eventType ? getString("reservation_type") : getString("resource_type");
                       value = getI18n().format("permission." + key, typeName );
                   }
                   else  if (key.equalsIgnoreCase(Permission.READ.name()) && permissionLevels.contains(Permission.READ_NO_ALLOCATION))
                   {
                       value = getI18n().getString("permission.read_allocation"  );
                   }
                        
                   else
                   {
                       value = getI18n().getString("permission." + key );
                   }

                }
                Component listCellRendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus );
                if ( intValue == Permission.CREATE || intValue == Permission.READ_TYPE)
                {   
                    Font newFont = listCellRendererComponent.getFont().deriveFont(Font.BOLD);
                    listCellRendererComponent.setFont( newFont);
                }
                return listCellRendererComponent;
            }}
        );
        
        userPanel.add( accessField.getComponent(), "2,4,f,f" );
       
        toggleVisibility();
        userSelect.addChangeListener( this );

        startSelection.addActionListener(this);
        minAdvance.addChangeListener(this);
        startDate.addChangeListener(this);

        endSelection.addActionListener(this);
        maxAdvance.addChangeListener(this);
        endDate.addChangeListener(this);

        accessField.addChangeListener(this);
        panel.revalidate();
    }

    public JComponent getComponent() {
        return panel;
    }


    @SuppressWarnings("unchecked")
	private DefaultComboBoxModel createSelectionModel() {
        DefaultComboBoxModel model = new DefaultComboBoxModel();
        model.addElement(getString( "open" ) );
        model.addElement(getString( "fixed_date") );
        model.addElement(getString( "x_days_advance") );
        return model;
    }


    private void toggleVisibility() {
        Permission.AccessLevel level = accessField.getValue();
        reservationPanel.setVisible( level.includes(Permission.ALLOCATE) && level.excludes(Permission.ADMIN));
        
        int i = startSelection.getSelectedIndex();
        startDate.getComponent().setVisible( i == 1 );
        minAdvance.getComponent().setVisible( i == 2 );

        int j = endSelection.getSelectedIndex();
        endDate.getComponent().setVisible( j == 1 );
        maxAdvance.getComponent().setVisible( j == 2 );
    }

    boolean listenersDisabled = false;
    public void setValue(Permission value)  {
        try {
            listenersDisabled = true;

            permission =  value;
           
            int startIndex = 0;
            if ( permission.getStart() != null )
                startIndex = 1;
            if ( permission.getMinAdvance() != null )
                startIndex = 2;
            startSelection.setSelectedIndex( startIndex );

            int endIndex = 0;
            if ( permission.getEnd() != null )
                endIndex = 1;
            if ( permission.getMaxAdvance() != null )
                endIndex = 2;
            endSelection.setSelectedIndex( endIndex );

            
            startDate.setValue( permission.getStart());
            minAdvance.setValue( permission.getMinAdvance());
            endDate.setValue(permission.getEnd() );
            maxAdvance.setValue(permission.getMaxAdvance());
            if ( groupSelect != null )
            {
                groupSelect.setValue( permission.getGroup());
            }
            userSelect.setValue(permission.getUser() );
            accessField.setValue( permission.getAccessLevel() );

            toggleVisibility();
        } finally {
            listenersDisabled = false;
        }
    }



    public void actionPerformed(ActionEvent evt) {
        if ( listenersDisabled )
            return;
            
        if (evt.getSource() == startSelection) {
            int i = startSelection.getSelectedIndex();
            if ( i == 0 ) {
                permission.setStart( null );
                permission.setMinAdvance( null );
            }
            if ( i == 1 ) {
                Date today = getQuery().today();
                permission.setStart( today );
                startDate.setValue( today);
            } if ( i == 2 ) {
                permission.setMinAdvance( new Integer(0) );
                minAdvance.setValue( new Integer(0 ));
            }
        }
        if (evt.getSource() == endSelection) {
            int i = endSelection.getSelectedIndex();
            if ( i == 0 ) {
                permission.setEnd( null );
                permission.setMaxAdvance( null );
            }
            if ( i == 1 ) {
                Date today = getQuery().today();
                permission.setEnd( today );
                endDate.setValue( today);
            } if ( i == 2 ) {
                permission.setMaxAdvance( new Integer( 30 ) );
                maxAdvance.setValue( new Integer(30));
            }
        }
        toggleVisibility();
        fireContentChanged();
    }

    public Permission getValue() {
        return permission;
    }

    public void stateChanged(ChangeEvent evt) {
        if ( listenersDisabled )
            return;
        Permission perm = permission;
        if (evt.getSource() == groupSelect) {
            perm.setGroup(groupSelect.getValue() );
            try
            {
                listenersDisabled = true;
                userSelect.setValue(perm.getUser()) ;
            }
            finally
            {
                listenersDisabled = false;
            }
        } else if (evt.getSource() == userSelect) {
            perm.setUser( userSelect.getValue());
            try
            {
                listenersDisabled = true;
                if ( groupSelect != null )
                    groupSelect.setValue(perm.getGroup());
            }
            finally
            {
                listenersDisabled = false;
            }
        } else if (evt.getSource() == startDate) {
            perm.setStart(startDate.getValue() );
        } else if (evt.getSource() == minAdvance) {
            perm.setMinAdvance( minAdvance.getIntValue());
        } else if (evt.getSource() == endDate) {
            perm.setEnd(endDate.getValue());
        } else if (evt.getSource() == maxAdvance) {
            perm.setMaxAdvance(maxAdvance.getIntValue() );
        } else if (evt.getSource() == accessField ) {
            perm.setAccessLevel( accessField.getValue() );
            toggleVisibility();
        }
        fireContentChanged();
    }

    class UserListField extends ListField<User> {

        public UserListField(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger) throws RaplaException{
            super(facade, i18n, raplaLocale, logger, true);
            User[] users = getQuery().getUsers();
            List<User> asList = new ArrayList<User>(Arrays.asList(users ));
            Collections.sort( asList, new NamedComparator<User>( getLocale()));
            setVector(asList);
        }
    }

    
    public void setPermissionLevels(Permission.AccessLevel... permissionLevels) {
        this.permissionLevels = Arrays.asList( permissionLevels);
        accessField.setVector( this.permissionLevels);
    }
    
    public Collection<Permission.AccessLevel> getPermissionLevels() 
    {
        return permissionLevels;
    }
    
    public void setUserVisible(boolean userVisible) {
        userSelect.getComponent().setVisible( userVisible );
        userLabel.setVisible( userVisible);
    }
    
    public boolean isUserVisible() 
    {
        return userSelect.getComponent().isVisible();
    }
    
    public boolean isEventType() {
        return eventType;
    }

    public void setEventType(boolean eventType) {
        this.eventType = eventType;
    }
    
    @Singleton
    public static class PermissionFieldFactory
    {
        private final ClientFacade facade;
        private final RaplaResources i18n;
        private final RaplaLocale raplaLocale;
        private final Logger logger;
        private final TreeFactory treeFactory;
        private final RaplaImages raplaImages;
        private final DialogUiFactoryInterface dialogUiFactory;
        private final DateFieldFactory dateFieldFactory;
        private final LongFieldFactory longFieldFactory;

        @Inject
        public PermissionFieldFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TreeFactory treeFactory,
                RaplaImages raplaImages, DateRenderer dateRenderer, DialogUiFactoryInterface dialogUiFactory, DateFieldFactory dateFieldFactory, LongFieldFactory longFieldFactory)
        {
            this.facade = facade;
            this.i18n = i18n;
            this.raplaLocale = raplaLocale;
            this.logger = logger;
            this.treeFactory = treeFactory;
            this.raplaImages = raplaImages;
            this.dialogUiFactory = dialogUiFactory;
            this.dateFieldFactory = dateFieldFactory;
            this.longFieldFactory = longFieldFactory;
        }

        public PermissionField create() throws RaplaException
        {
            return new PermissionField(facade, i18n, raplaLocale, logger, treeFactory, raplaImages, dialogUiFactory, dateFieldFactory, longFieldFactory);
        }
    }

}



