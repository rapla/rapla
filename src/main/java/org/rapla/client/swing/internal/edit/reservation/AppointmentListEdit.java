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
package org.rapla.client.swing.internal.edit.reservation;

import org.rapla.RaplaResources;
import org.rapla.client.RaplaWidget;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.AppointmentEditExtensionFactory;
import org.rapla.client.extensionpoints.AppointmentStatusFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.edit.RaplaListEdit;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.components.calendar.DateRenderer;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.layout.TableLayout;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.components.util.undo.CommandUndo;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.domain.AppointmentStartComparator;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Default GUI for editing multiple appointments.*/
class AppointmentListEdit extends AbstractAppointmentEditor
    implements
        RaplaWidget
        ,Disposable
{

    private final AppointmentController appointmentController;
    private final RaplaListEdit<Appointment> listEdit;

    private CommandHistory commandHistory;
	private boolean disableInternSelectionListener = false;

    protected Reservation mutableReservation;
    private final Listener listener = new Listener();
    // use sorted model to start with sorting
    // respect dependencies ! on other components
    @SuppressWarnings("rawtypes")
    Comparator comp = new AppointmentStartComparator();
    @SuppressWarnings("unchecked")
	DefaultListModel model = new DefaultListModel();
	SortedListModel sortedModel = new SortedListModel(model, SortedListModel.SortOrder.ASCENDING,comp );
    RaplaButton freeButtonNext = new RaplaButton();
    AppointmentFormater appointmentFormater;
    private final DialogUiFactoryInterface dialogUiFactory;
	@SuppressWarnings("unchecked")
	AppointmentListEdit(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, AppointmentFormater appointmentFormater, CommandHistory commandHistory, DateRenderer dateRenderer, DialogUiFactoryInterface dialogUiFactory, IOInterface ioInterface, Set<AppointmentEditExtensionFactory> appointmentEditFactories)
			throws RaplaException {
		super(facade, i18n, raplaLocale, logger);
        this.appointmentFormater = appointmentFormater;
		this.commandHistory = commandHistory;
        this.dialogUiFactory = dialogUiFactory;
        appointmentController = new AppointmentController(facade, i18n, raplaLocale, logger, commandHistory,  dateRenderer, dialogUiFactory, ioInterface, appointmentEditFactories);
        listEdit = new RaplaListEdit<>(getI18n(), appointmentController.getComponent(), listener, false);
        listEdit.getToolbar().add( freeButtonNext);

        freeButtonNext.setText(getString("appointment.search_free"));
		freeButtonNext.setActionCommand("nextFreeAppointment");
        freeButtonNext.addActionListener( listener );
        appointmentController.addChangeListener(listener);
        // activate this as a first step
        listEdit.getList().setModel(sortedModel);
        //listEdit.getList().setModel(model);
        listEdit.setColoredBackgroundEnabled(true);
        listEdit.setMoveButtonVisible(false);
        listEdit.getList().setCellRenderer(new AppointmentCellRenderer());

    }
	
	public void setCommandHistory(CommandHistory commandHistory)
    {
        this.commandHistory = commandHistory;
    }
    
    public RaplaListEdit<Appointment> getListEdit()
    {
    	return listEdit;
    }

    @SuppressWarnings("unchecked")
	private void addToModel(Appointment appointment) {
		model.addElement( appointment);
	}
    public JComponent getComponent() {
        return listEdit.getComponent();
    }

    public void setReservation(Reservation mutableReservation, Appointment mutableAppointment) {
        this.mutableReservation = mutableReservation;
        Appointment[] appointments = replaceList(mutableReservation);
        if ( mutableAppointment != null ) {
            selectAppointment(mutableAppointment, false);
        } else if ( appointments.length> 0 ){
            selectAppointment(appointments[0], false);
        }
    }

    private Appointment[] replaceList(Reservation mutableReservation) {
        Appointment[] appointments = mutableReservation.getAppointments();
        model.clear();
        for (Appointment app:appointments) {
            addToModel( app);
        }
        return appointments;
    }
    
	private void selectAppointment(Appointment appointment,boolean disableListeners) {
		if (disableListeners)
		{
			disableInternSelectionListener = true;
		}
		try {
			boolean shouldScroll = true;
			listEdit.getList().clearSelection();
			appointmentController.setAppointment( appointment );
			listEdit.getList().setSelectedValue(  appointment ,shouldScroll );
		} finally {
			if (disableListeners)
			{
				disableInternSelectionListener = false;
			}
		}


	}

    public void dispose() {
        appointmentController.dispose();
    }

    class AppointmentCellRenderer implements ListCellRenderer {
        Border focusBorder = UIManager.getBorder("List.focusCellHighlightBorder");
        Border emptyBorder = new EmptyBorder(1,1,1,1);

        Color selectionBackground = UIManager.getColor("List.selectionBackground");
        Color background = UIManager.getColor("List.background");

        AppointmentRow row = new AppointmentRow();
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            row.setAppointment((Appointment) value,index);
            row.setBackground((isSelected) ? selectionBackground : background);
            row.setBorder((cellHasFocus) ? focusBorder : emptyBorder);
            return row;
        }
    }

    class AppointmentRow extends JPanel {
        private static final long serialVersionUID = 1L;

        JPanel content = new JPanel();
        AppointmentIdentifier identifier = new AppointmentIdentifier();
        AppointmentRow() {
            double fill = TableLayout.FILL;
            double pre = TableLayout.PREFERRED;
            this.setLayout(new TableLayout(new double[][] {{pre,5,fill,10,pre},{1,fill,1}}));
            this.add(identifier,"0,1,l,f");
            this.add(content,"2,1,f,c");

            this.setMaximumSize(new Dimension(500,40));
            content.setOpaque(false);
            identifier.setOpaque(true);
            identifier.setBorder(null);
        }

        public void setAppointment(Appointment appointment,int index) {
            identifier.setText(getRaplaLocale().formatNumber(Long.valueOf(index + 1)));
            identifier.setIndex(index);
            content.setLayout(new BoxLayout(content,BoxLayout.Y_AXIS));
            content.removeAll();
            JLabel label1 = new JLabel(appointmentFormater.getSummary(appointment));
            content.add( label1 );
            if (appointment.getRepeating() != null) {
                label1.setIcon( RaplaImages.getIcon(i18n.getIcon("icon.repeating") ));
                Repeating r = appointment.getRepeating();
                List<Period> periods = getPeriodModel().getPeriodsFor(appointment.getStart());
                String repeatingString = appointmentFormater.getSummary(r,periods);
                content.add(new JLabel(repeatingString));
                if ( r.hasExceptions() ) {
                    content.add(new JLabel( appointmentFormater.getExceptionSummary( r ) ) );
                }
            } else {
                label1.setIcon( RaplaImages.getIcon(i18n.getIcon("icon.single") ));
            }
        }
    }

	class Listener implements ActionListener, ChangeListener {
		public void actionPerformed(ActionEvent evt) {
			final String actionCommand = evt.getActionCommand();
			if ( actionCommand.equals("nextFreeAppointment"))
			{
				appointmentController.nextFreeAppointment();
			}
			if (actionCommand.equals("remove"))
			{
				@SuppressWarnings("deprecation")
				Object[] objects = listEdit.getList().getSelectedValues();
				RemoveAppointments command = new RemoveAppointments(
						objects);
				commandHistory.storeAndExecute(command);
			} 
			else if (actionCommand.equals("new"))
			{
				handleException(createAppointmentFromSelected().thenAccept( appointment->commandHistory.storeAndExecute(new NewAppointment(appointment))));
			}
			else if (actionCommand.equals("split"))
			{
				AppointmentSplit command = new AppointmentSplit();
				commandHistory.storeAndExecute(command);
			} 
			else if (actionCommand.equals("edit")) {
				Appointment newAppointment = (Appointment) listEdit.getList().getSelectedValue();
				Appointment oldAppointment = appointmentController.getAppointment();
				
				if (!disableInternSelectionListener && oldAppointment != newAppointment)
				{
				    if (oldAppointment!=null && !newAppointment.equals(oldAppointment) ) {
				        AppointmentSelectionChange appointmentCommand = new AppointmentSelectionChange(oldAppointment, newAppointment);
				        commandHistory.storeAndExecute(appointmentCommand);
					}else {
					    appointmentController.setAppointment(newAppointment);
					}
				}
			}
			else if (actionCommand.equals("select"))
			{
				Collection<Appointment> appointments = new ArrayList<>();
				@SuppressWarnings("deprecation")
				Object[] values = listEdit.getList().getSelectedValues();
		        for ( Object value:values)
				{
					appointments.add( (Appointment)value);
				}
		        freeButtonNext.setEnabled( appointments.size() == 1);
		        fireAppointmentSelected(appointments);
			}

		}

		@SuppressWarnings("unchecked")
		public void stateChanged(ChangeEvent evt) {
			Appointment appointment = appointmentController.getAppointment();
			@SuppressWarnings("deprecation")
			Object[] values = listEdit.getList().getSelectedValues();
			List<Object> selectedValues = Arrays.asList(values);
	    	int indexOf = model.indexOf(appointment);
			if ( indexOf >=0)
			{
			    model.set(indexOf, appointment);
			}
			listEdit.updateSort( selectedValues);
			fireAppointmentChanged(Collections.singleton(appointment));
		}

	}

	private void handleException(Promise<Void> promise) {
		promise.exceptionally((ex)->dialogUiFactory.showException(ex, dialogUiFactory.createPopupContext(()->getMainComponent())));

	}

	public AppointmentController getAppointmentController() {
		return appointmentController;
	}
	
	public void addAppointment(Appointment appointment) 
	{
		NewAppointment newAppointment = new NewAppointment(appointment);
		commandHistory.storeAndExecute( newAppointment);
	}

	
	/**
	 * This class collects any information of removed appointments in the edit view.
	 * This is where undo/redo for removing an appointment at the fields on the top-left
	 * of the edit view is realized. 
	 * @author Jens Fritz
	 *
	 */
	public class RemoveAppointments implements CommandUndo<RuntimeException> {
		private final Map<Appointment,Allocatable[]> list;
		private int[] selectedAppointment;
		
		
		public RemoveAppointments(Object[] list) {
			this.list = new LinkedHashMap<>();
			for ( Object obj:list)
			{
				Appointment appointment = (Appointment) obj;
				Allocatable[] restrictedAllocatables = mutableReservation.getRestrictedAllocatables(appointment);
				this.list.put ( appointment,  restrictedAllocatables);
			}
		}

		public Promise<Void> execute() {
			selectedAppointment = listEdit.getList().getSelectedIndices();
			Set<Appointment> appointmentList = list.keySet();
			for (Appointment appointment:appointmentList) {
				mutableReservation.removeAppointment(appointment);
			}
			replaceList(mutableReservation);
	        fireAppointmentRemoved(appointmentList);
			listEdit.getList().requestFocus();
			return ResolvedPromise.VOID_PROMISE;
		}
		
		public Promise<Void> undo() {
			Set<Appointment> appointmentList = list.keySet();
			for (Appointment appointment:appointmentList) 
			{
				mutableReservation.addAppointment(appointment);
                Allocatable[] removedAllocatables = list.get( appointment);
				mutableReservation.setRestrictionForAppointment(appointment, removedAllocatables);
			}
			replaceList(mutableReservation);
			fireAppointmentAdded(appointmentList);
			disableInternSelectionListener = true;
			try {
				listEdit.getList().setSelectedIndices(selectedAppointment);
			} finally {
				disableInternSelectionListener = false;
			}
			return ResolvedPromise.VOID_PROMISE;
		}
		
		public String getCommandoName() {
			return getString("remove")+ " " + getString("appointment");
		}
	}

	protected Promise<Appointment> createAppointmentFromSelected()
	{
//		Appointment[] appointments = mutableReservation.getAppointments();
		Promise<Appointment> appointment;
		if (sortedModel.getSize() == 0) {
			Date start = new Date(DateTools.cutDate(new Date()).getTime()+ getCalendarOptions().getWorktimeStartMinutes()	* DateTools.MILLISECONDS_PER_MINUTE);
			Date end = new Date(start.getTime()+ DateTools.MILLISECONDS_PER_HOUR);
			appointment = getFacade().newAppointmentAsync(new TimeInterval(start, end));
		} else { 
			// copyReservations the selected appointment as template
			// if no appointment is selected use the last
			final int selectedIndex = listEdit.getSelectedIndex();
			final int index = selectedIndex > -1 ? selectedIndex : sortedModel.getSize() - 1;
			final Appointment toClone = (Appointment)sortedModel.getElementAt( index);
             //= appointments[index];
			// this allows each appointment as template
			appointment = getFacade().cloneAsync(toClone).thenApply(AppointmentListEdit::clearExceptions);
		}
		return appointment;
	}

	public static Appointment clearExceptions(Appointment app)
	{
		Repeating repeating = app.getRepeating();
		if (repeating != null) {
			repeating.clearExceptions();
		}
		return app;
	}
	
	/**
	 * This class collects any information of added appointments in the edit view.
	 * This is where undo/redo for adding an appointment at the fields on the top-left
	 * of the edit view is realized. 
	 * @author Jens Fritz
	 *
	 */
	
	//Erstellt von Matthias Both
	public class NewAppointment implements CommandUndo<RuntimeException> {
		private final Appointment newAppointment;
		public NewAppointment( Appointment appointment) {
			this.newAppointment = appointment;
		}

		public Promise<Void> execute()  {
			mutableReservation.addAppointment(newAppointment);
			addToModel(newAppointment);
			selectAppointment(newAppointment, true);
			fireAppointmentAdded(Collections.singleton(newAppointment));
			return ResolvedPromise.VOID_PROMISE;
		}
		
		public Promise<Void> undo() {
			model.removeElement(newAppointment);
			mutableReservation.removeAppointment(newAppointment);
			fireAppointmentRemoved(Collections.singleton(newAppointment));
			return ResolvedPromise.VOID_PROMISE;
		}
		
		public String getCommandoName() {
			return getString("new_appointment");
		}
	}

	
	/**
	 * This class collects any information of an appointment that is split from a repeating type
	 * into several single appointments in the edit view.
	 * This is where undo/redo for splitting an appointment at the fields on the top-right
	 * of the edit view is realized. 
	 * @author Jens Fritz
	 *
	 */
	
	//Erstellt von Matthias Both
	public class AppointmentSplit implements CommandUndo<RuntimeException> 
	{
		Appointment wholeAppointment;
		Allocatable[] allocatablesFor;
		List<Appointment> splitAppointments;

		public AppointmentSplit() {
		}

		public Promise<Void> execute()  {
			// Generate time blocks from selected appointment
			List<AppointmentBlock> splits = new ArrayList<>();
			Appointment appointment = appointmentController.getAppointment();
			appointment.createBlocks(appointment.getStart(), DateTools.fillDate(appointment.getMaxEnd()), splits);
			allocatablesFor = mutableReservation.getAllocatablesFor(appointment).toArray(Allocatable[]::new);

			wholeAppointment = appointment;
			fireAppointmentRemoved(Collections.singleton(appointment));

			splitAppointments = new ArrayList<>();
			// Create single appointments for every time block
			List<TimeInterval> intervals= splits.stream().map(AppointmentBlock::toInterval).collect(Collectors.toList());
			return getFacade().newAppointmentsAsync(intervals).thenAccept( newApps->
			{
				for (Appointment newApp : newApps) {
					// Add appointment to list
					splitAppointments.add(newApp);
					mutableReservation.addAppointment(newApp);
				}
				for (Allocatable alloc : allocatablesFor) {
					Appointment[] restrictions = mutableReservation.getRestriction(alloc);
					if (restrictions.length > 0) {
						LinkedHashSet<Appointment> newRestrictions = new LinkedHashSet<>(Arrays.asList(restrictions));
						newRestrictions.addAll(splitAppointments);
						mutableReservation.setRestriction(alloc, newRestrictions.toArray(Appointment.EMPTY_ARRAY));
					}
				}
				// we need to remove the appointment after splitting not before, otherwise allocatable connections could be lost
				mutableReservation.removeAppointment(appointment);
				model.removeElement(appointment);

				for (Appointment newApp : splitAppointments) {
					addToModel(newApp);
				}
				fireAppointmentAdded(splitAppointments);

				if (splitAppointments.size() > 0) {
					Appointment app = splitAppointments.get(0);
					selectAppointment(app, true);
				}
			});
		}
		
		public Promise<Void> undo()  {
            // Switch the type of the appointment to old type
            mutableReservation.addAppointment(wholeAppointment);

			for (Allocatable alloc : allocatablesFor) {
				Appointment[] restrictions = mutableReservation.getRestriction(alloc);
				if (restrictions.length > 0) {
					LinkedHashSet<Appointment> newRestrictions = new LinkedHashSet<>(Arrays.asList(restrictions));
					newRestrictions.removeAll(splitAppointments);
					newRestrictions.add(wholeAppointment);
					mutableReservation.setRestriction(alloc,newRestrictions.toArray(Appointment.EMPTY_ARRAY));
				}
			}
			// same here we remove the split appointments after we add the old appointment so no allocatable connections gets lost 
			for (Appointment newApp : splitAppointments) {
                mutableReservation.removeAppointment(newApp);
                model.removeElement(newApp);
            }
			addToModel(wholeAppointment);
			fireAppointmentAdded(Collections.singleton(wholeAppointment));
		
			selectAppointment(wholeAppointment, true);
			return ResolvedPromise.VOID_PROMISE;
		}
	

		public String getCommandoName() 
		{
			return getString("appointment.convert");
		}
	}

	
	/**
	 * This class collects any information of which appointment is selected in the edit view.
	 * This is where undo/redo for selecting an appointment at the fields on the top-left
	 * of the edit view is realized. 
	 * @author Jens Fritz
	 *
	 */
	
	//Erstellt von Dominick Krickl-Vorreiter
	public class AppointmentSelectionChange implements CommandUndo<RuntimeException> {

		private final Appointment oldAppointment;
		private final Appointment newAppointment;
		
		public AppointmentSelectionChange(Appointment oldAppointment, Appointment newAppointment) {
			this.oldAppointment = oldAppointment;
			this.newAppointment = newAppointment;
		}
		
		public Promise<Void> execute() {
			setAppointment(newAppointment);
			return ResolvedPromise.VOID_PROMISE;
		}

		public Promise<Void> undo()  {
			setAppointment(oldAppointment);
			return ResolvedPromise.VOID_PROMISE;
		}
		
		private void setAppointment(Appointment toAppointment) {
			selectAppointment(toAppointment, true);
		}
		
		public String getCommandoName() {
			return getString("select") + " " + getString("appointment");
		}
	}

    @Singleton
    public static class AppointmentListEditFactory
    {
        private final ClientFacade facade;
        private final RaplaResources i18n;
        private final RaplaLocale raplaLocale;
        private final Logger logger;
        private final AppointmentFormater appointmentFormater;
        private final DateRenderer dateRenderer;
        private final DialogUiFactoryInterface dialogUiFactory;
        private final IOInterface ioInterface;
		private final Set<AppointmentEditExtensionFactory> appointmentEditFactories;

        @Inject
        public AppointmentListEditFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,
                AppointmentFormater appointmentFormater, DateRenderer dateRenderer,
                DialogUiFactoryInterface dialogUiFactory, IOInterface ioInterface,Set<AppointmentEditExtensionFactory> appointmentEditFactories)
        {
            super();
            this.facade = facade;
            this.i18n = i18n;
            this.raplaLocale = raplaLocale;
            this.logger = logger;
            this.appointmentFormater = appointmentFormater;
            this.dateRenderer = dateRenderer;
            this.dialogUiFactory = dialogUiFactory;
            this.ioInterface = ioInterface;
			this.appointmentEditFactories = appointmentEditFactories;
        }

        public AppointmentListEdit create(CommandHistory commandHistory) throws RaplaException
        {
            return new AppointmentListEdit(facade, i18n, raplaLocale, logger, appointmentFormater, commandHistory,
                    dateRenderer, dialogUiFactory, ioInterface, appointmentEditFactories);
        }
    }
	
}


