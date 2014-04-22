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
package org.rapla.gui.internal.edit.fields;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;

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

import org.rapla.components.layout.TableLayout;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Permission;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;

public class PermissionField extends AbstractEditField implements  ChangeListener, ActionListener {
    SetGetField<Category> groupSelect;
    ListField<User> userSelect;

    JPanel panel = new JPanel();
    JPanel reservationPanel;
    Permission permission;

    JComboBox startSelection = new JComboBox();
    JComboBox endSelection = new JComboBox();
    DateField startDate;
    DateField endDate;
    LongField minAdvance;
    LongField maxAdvance;

    ListField<Integer> accessField;

    @SuppressWarnings("unchecked")
	public PermissionField(RaplaContext context) throws RaplaException {
        super( context);

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

        userSelect = new UserListField( context );
        userPanel.add( new JLabel(getString("user") + ":"), "0,0,l,f" );
        userPanel.add( userSelect.getComponent(),"2,0,l,f" );

        Category rootCategory =   getQuery().getUserGroupsCategory();
        if ( rootCategory != null) {
            AbstractEditField groupSelect;
            if (rootCategory.getDepth() > 2) {
                CategorySelectField field= new CategorySelectField(getContext(), rootCategory);
                this.groupSelect = field;
                groupSelect = field;
            } else {
                CategoryListField field = new CategoryListField(getContext(), rootCategory);
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

        startDate = new DateField(context);
        reservationPanel.add( startDate.getComponent() , "4,0,l,f" );

        minAdvance = new LongField(context,new Long(0) );
        reservationPanel.add( minAdvance.getComponent() , "4,0,l,f" );

        reservationPanel.add( new JLabel( getString("end_date") + ":" ), "0,2,l,f" );
        reservationPanel.add( endSelection , "2,2,l,f" );
        endSelection.setModel( createSelectionModel() );
        endSelection.setSelectedIndex( 0 );

        endDate = new DateField(context);
        reservationPanel.add( endDate.getComponent() , "4,2,l,f" );

        maxAdvance = new LongField(context, new Long(1) );
        reservationPanel.add( maxAdvance.getComponent() , "4,2,l,f" );

        userPanel.add( new JLabel(getString("permission.access") + ":"), "0,4,f,f" );
        Collection<Integer> vector = new ArrayList<Integer>();
        for (Integer accessLevel:Permission.ACCESS_LEVEL_NAMEMAP.keySet()) 
        {
            vector.add( accessLevel ) ;
        }
        accessField = new ListField<Integer>(context, vector );
        accessField.setRenderer( new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                if (value != null) {
                   String key = Permission.ACCESS_LEVEL_NAMEMAP.get( ((Integer) value).intValue() );
                   value = getI18n().getString("permission." + key );
                }
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus );
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
        int level = accessField.getValue().intValue();
        reservationPanel.setVisible( level >= Permission.ALLOCATE && level < Permission.ADMIN);
        
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
            userSelect.setValue(perm.getUser()) ;
        } else if (evt.getSource() == userSelect) {
            perm.setUser( userSelect.getValue());
            if ( groupSelect != null )
                groupSelect.setValue(perm.getGroup());
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

        public UserListField(RaplaContext sm) throws RaplaException{
            super(sm,true);
            setVector(Arrays.asList(getQuery().getUsers() ));
        }
    }

}


