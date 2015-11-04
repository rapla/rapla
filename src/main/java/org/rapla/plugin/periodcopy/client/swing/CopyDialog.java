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
package org.rapla.plugin.periodcopy.client.swing;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.rapla.RaplaResources;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.internal.common.NamedListCellRenderer;
import org.rapla.client.swing.internal.edit.fields.BooleanField;
import org.rapla.client.swing.internal.edit.fields.BooleanField.BooleanFieldFactory;
import org.rapla.client.swing.toolkit.DialogUI.DialogUiFactory;
import org.rapla.client.swing.toolkit.RaplaWidget;
import org.rapla.components.calendar.DateChangeEvent;
import org.rapla.components.calendar.DateChangeListener;
import org.rapla.components.calendar.DateRenderer;
import org.rapla.components.calendar.RaplaCalendar;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.PeriodImpl;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.plugin.periodcopy.PeriodCopyResources;

/** sample UseCase that only displays the text of the configuration and
 all reservations of the user.*/
public class CopyDialog extends RaplaGUIComponent implements RaplaWidget
{
    @SuppressWarnings("unchecked")
	JComboBox sourcePeriodChooser = new JComboBox(new String[] {"a", "b"});
    @SuppressWarnings("unchecked")
	JComboBox destPeriodChooser = new JComboBox(new String[] {"a", "b"});
    RaplaLocale locale = getRaplaLocale();
    RaplaCalendar destBegin;
    RaplaCalendar sourceBegin; 
    RaplaCalendar sourceEnd;
    
    JPanel panel = new JPanel();
    JLabel label = new JLabel();
    JList  selectedReservations = new JList();
    BooleanField singleChooser;
    PeriodImpl customPeriod = new PeriodImpl("", null, null);

    JPanel customSourcePanel = new JPanel();
    JPanel customDestPanel = new JPanel();

    PeriodCopyResources periodCopyI18n;
    private final CalendarModel model;
    
    @SuppressWarnings("unchecked")
    @Inject
	public CopyDialog(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, PeriodCopyResources periodCopyI18n, CalendarModel model, DateRenderer dateRenderer, BooleanFieldFactory booleanFieldFactory, final DialogUiFactory dialogUiFactory, IOInterface ioInterface) throws RaplaException {
        super(facade, i18n, raplaLocale, logger);
        this.periodCopyI18n = periodCopyI18n;
        this.model = model;
        locale = getRaplaLocale();
        sourceBegin = createRaplaCalendar(dateRenderer, ioInterface);
        sourceEnd = createRaplaCalendar(dateRenderer, ioInterface);
        destBegin = createRaplaCalendar(dateRenderer, ioInterface);
        
        
        Period[] periods = getQuery().getPeriods();
        singleChooser = booleanFieldFactory.create("singleChooser");
        singleChooser.addChangeListener( new ChangeListener() {
			
			public void stateChanged(ChangeEvent e) {
				try {
					updateReservations();
				} catch (RaplaException ex) {
					showException(ex, getComponent(), dialogUiFactory);
				}
			}
		});
       
		DefaultComboBoxModel sourceModel = new DefaultComboBoxModel(  periods );
		Date today = getQuery().today();
        final PeriodImpl customSource = new PeriodImpl(periodCopyI18n.getString("custom_period"), today, today);
        sourceModel.insertElementAt(customSource, 0);
        
		DefaultComboBoxModel destModel = new DefaultComboBoxModel(  periods );
        final PeriodImpl customDest = new PeriodImpl(periodCopyI18n.getString("custom_period"),today, null);
        {
	        destModel.insertElementAt(customDest, 0);
        }
        
        //customEnd.setStart( destDate.getDate());
        
        
        sourcePeriodChooser.setModel( sourceModel);
        destPeriodChooser.setModel( destModel);
        label.setText(periodCopyI18n.getString("copy_selected_events_from"));
        panel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        panel.setLayout(new TableLayout(new double[][]{
                 {TableLayout.PREFERRED ,5 , TableLayout.FILL }
                 ,{20, 5, TableLayout.PREFERRED ,5 ,TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED,5, TableLayout.PREFERRED,5, TableLayout.PREFERRED  }
        }
        ));
        selectedReservations.setEnabled( false );
        customSourcePanel.add( sourceBegin );
        customSourcePanel.add( new JLabel(getString("time_until")) );
        customSourcePanel.add( sourceEnd );
        
        customDestPanel.add( destBegin);
        
        panel.add(label, "0,0,2,1");
        panel.add( new JLabel(getString("source")),"0,2" );
        panel.add( sourcePeriodChooser,"2,2" );
        panel.add( customSourcePanel,"2,4" );
        panel.add( new JLabel(getString("destination")),"0,6" );
        panel.add( destPeriodChooser,"2,6" );
        panel.add( customDestPanel,"2,8" );
        panel.add( new JLabel(periodCopyI18n.getString("copy_single")),"0,10" );
        panel.add( singleChooser.getComponent(),"2,10" );
        singleChooser.setValue( Boolean.TRUE);
        panel.add( new JLabel(getString("reservations")) , "0,12,l,t");
        panel.add( new JScrollPane( selectedReservations ),"2,12" );
        
        updateView();
        sourcePeriodChooser.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				updateView();
				if ( sourcePeriodChooser.getSelectedIndex() > 1)
				{
					Period beginPeriod = (Period)sourcePeriodChooser.getSelectedItem();
					sourceBegin.setDate(beginPeriod.getStart());
					sourceEnd.setDate(beginPeriod.getEnd());
				}
			}
		});
        NamedListCellRenderer aRenderer = new NamedListCellRenderer( getRaplaLocale().getLocale());
		sourcePeriodChooser.setRenderer( aRenderer);
		destPeriodChooser.setRenderer( aRenderer);
        
        destPeriodChooser.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				updateView();
				if ( destPeriodChooser.getSelectedIndex() > 0)
				{
					Period endPeriod = (Period)destPeriodChooser.getSelectedItem();
					destBegin.setDate(endPeriod.getStart());
				}
			}
		});
        

        DateChangeListener dateChangeListener = new DateChangeListener() {
			
			public void dateChanged(DateChangeEvent evt) {
				customSource.setStart( sourceBegin.getDate());
				customSource.setEnd( sourceEnd.getDate());
				customDest.setStart( destBegin.getDate());
				try {
					updateReservations();
				} catch (RaplaException ex) {
					showException(ex, getComponent(), dialogUiFactory);
				}
			}
		};
		
		sourceBegin.addDateChangeListener(dateChangeListener);
        sourceEnd.addDateChangeListener(dateChangeListener);
        destBegin.addDateChangeListener(dateChangeListener);
        
        sourcePeriodChooser.setSelectedIndex(0);
        destPeriodChooser.setSelectedIndex(0);
        
        updateReservations();
    }
    
    public Date getSourceStart()
    {
    	return sourceBegin.getDate();
    }
   
    public Date getSourceEnd()
    {
      	return sourceEnd.getDate();
    }
    
    public Date getDestStart()
    {	return destBegin.getDate();
    	
    }
    
    public Date getDestEnd()
    {
    	if ( destPeriodChooser.getSelectedIndex() > 0)
		{
			Period endPeriod = (Period)destPeriodChooser.getSelectedItem();
			return endPeriod.getStart();
		}
    	else
    	{
    		return null;
    	}
    }
    
    private boolean isIncluded(Reservation r, boolean includeSingleAppointments)
    {
        Appointment[] appointments = r.getAppointments();
        int count = 0;
        for ( int j=0;j<appointments.length;j++) {
            Appointment app = appointments[j];
            Repeating repeating = app.getRepeating();
            if (( repeating == null && !includeSingleAppointments) || (repeating != null && repeating.getEnd() == null)) {
                continue;
            }
            count++;
        } 
        return count > 0; 
    }
    
    private void updateView() {
    	boolean customStartEnabled = sourcePeriodChooser.getSelectedIndex() == 0;
    	sourceBegin.setEnabled( customStartEnabled);	
    	sourceEnd.setEnabled( customStartEnabled);	

    	boolean customDestEnabled = destPeriodChooser.getSelectedIndex() == 0;
    	destBegin.setEnabled( customDestEnabled);	
    }
    
    @SuppressWarnings("unchecked")
	private void updateReservations() throws RaplaException 
    {
    	DefaultListModel listModel = new DefaultListModel();
    	List<Reservation> reservations = getReservations();
		for ( Reservation reservation: reservations) {
         	listModel.addElement( reservation.getName( getLocale() ) );
         }
         selectedReservations.setModel( listModel);
    }

    public JComponent getComponent() {
        return panel;
    }

	public boolean isSingleAppointments() {
		Object value = singleChooser.getValue();
		return value != null && ((Boolean)value).booleanValue();
	}

	public List<Reservation> getReservations() throws RaplaException {
	    Reservation[] reservations = model.getReservations( getSourceStart(), getSourceEnd() );
	    List<Reservation> listModel = new ArrayList<Reservation>();
	      
        for ( Reservation reservation:reservations) {
        	
        	boolean includeSingleAppointments = isSingleAppointments();
			if  (isIncluded(reservation, includeSingleAppointments))
        	{
        		listModel.add( reservation );
        	}
        }
        return listModel;
	}
}

