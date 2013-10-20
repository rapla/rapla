/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
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
package org.rapla.gui.internal.edit.reservation;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.ComponentInputMap;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.ActionMapUIResource;

import org.rapla.client.RaplaClientExtensionPoints;
import org.rapla.components.layout.TableLayout;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.components.util.undo.CommandHistoryChangedListener;
import org.rapla.components.util.undo.CommandUndo;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.ReservationHelper;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationModule;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.AppointmentListener;
import org.rapla.gui.AppointmentStatusFactory;
import org.rapla.gui.ReservationController;
import org.rapla.gui.ReservationEdit;
import org.rapla.gui.toolkit.DialogUI;
import org.rapla.gui.toolkit.EmptyLineBorder;
import org.rapla.gui.toolkit.RaplaButton;
import org.rapla.gui.toolkit.RaplaFrame;
import org.rapla.gui.toolkit.RaplaWidget;

class ReservationEditImpl extends AbstractAppointmentEditor implements ReservationEdit
{
    ArrayList<ChangeListener> changeListenerList = new ArrayList<ChangeListener>();
    protected Reservation mutableReservation;
	private Reservation original;

    CommandHistory commandHistory;
    JToolBar toolBar = new JToolBar();
    RaplaButton saveButtonTop = new RaplaButton();
    RaplaButton saveButton = new RaplaButton();
    RaplaButton deleteButton = new RaplaButton();
    RaplaButton closeButton = new RaplaButton();
    
    Action undoAction = new AbstractAction() {
        private static final long serialVersionUID = 1L;

        public void actionPerformed(ActionEvent e) {
			try {
				commandHistory.undo();
			} catch (Exception ex) {
				showException(ex, getMainComponent());
			}
		}
	};
    Action redoAction = new AbstractAction() {
        private static final long serialVersionUID = 1L;

		public void actionPerformed(ActionEvent e) {
			try {
				commandHistory.redo();
			} catch (Exception ex) {
				showException(ex, getMainComponent());
			}
		}
	};
  
    JPanel mainContent = new JPanel();
    //JPanel split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

    RaplaFrame frame;

    ReservationInfoEdit reservationInfo;
    AppointmentListEdit appointmentEdit ;
    AllocatableSelection allocatableEdit;

    boolean bSaving = false;
    boolean bDeleting = false;
    boolean bSaved;
    boolean bNew;
    
    TableLayout tableLayout = new TableLayout(new double[][] {
            {TableLayout.FILL}
            ,{TableLayout.PREFERRED,TableLayout.PREFERRED,TableLayout.FILL}
        } );

    private final Listener listener = new Listener();
    
    List<AppointmentListener> appointmentListeners = new ArrayList<AppointmentListener>();



    ReservationEditImpl(RaplaContext sm) throws RaplaException {
        super( sm);
        
        commandHistory = new CommandHistory();
        reservationInfo = new ReservationInfoEdit(sm, commandHistory);
        appointmentEdit = new AppointmentListEdit(sm, commandHistory);
        allocatableEdit = new AllocatableSelection(sm,true, commandHistory);

        //      horizontalSplit.setTopComponent(appointmentEdit.getComponent());
        //horizontalSplit.setBottomComponent(allocatableEdit.getComponent());
        /*
        try {
            // If run on jdk < 1.3 this will throw a MethodNotFoundException
            // horizontalSplit.setResizeWeight(0.1);
            JSplitPane.class.getMethod("setResizeWeight",new Class[] {double.class}).invoke(horizontalSplit,new Object[] {new Double(0.1)});
        } catch (Exception ex) {
        }
        */

        frame = new RaplaFrame(sm);
        mainContent.setLayout( tableLayout );
        mainContent.add(reservationInfo.getComponent(),"0,0");
        mainContent.add(appointmentEdit.getComponent(),"0,1");
        mainContent.add(allocatableEdit.getComponent(),"0,2");
        //allocatableEdit.getComponent().setVisible(false);
        saveButtonTop.setAction( listener );
        saveButton.setAction( listener );
        toolBar.setFloatable(false);
        saveButton.setAlignmentY(JButton.CENTER_ALIGNMENT);
        deleteButton.setAlignmentY(JButton.CENTER_ALIGNMENT);
        closeButton.setAlignmentY(JButton.CENTER_ALIGNMENT);
        JPanel buttonsPanel = new JPanel();
        //buttonsPanel.add(deleteButton);
        buttonsPanel.add(saveButton);
        buttonsPanel.add(closeButton);
        toolBar.add(saveButtonTop);
        toolBar.add(deleteButton);
        deleteButton.setAction( listener );
        RaplaButton vor = new RaplaButton();
        RaplaButton back = new RaplaButton();

        // Undo-Buttons in Toolbar
//        final JPanel undoContainer = new JPanel();
       

        redoAction.setEnabled(false);
        undoAction.setEnabled(false);

        vor.setAction( redoAction);
        back.setAction( undoAction);
        final KeyStroke undoKeyStroke = KeyStroke.getKeyStroke( KeyEvent.VK_Z, ActionEvent.CTRL_MASK );
        setAccelerator(back, undoAction, undoKeyStroke);
        final KeyStroke redoKeyStroke = KeyStroke.getKeyStroke( KeyEvent.VK_Y, ActionEvent.CTRL_MASK );
        setAccelerator(vor, redoAction, redoKeyStroke);
       
        commandHistory.addCommandHistoryChangedListener(new CommandHistoryChangedListener() {
            
            public void historyChanged()
            {
                redoAction.setEnabled(commandHistory.canRedo());
                boolean canUndo = commandHistory.canUndo();
                undoAction.setEnabled(canUndo);
                String modifier = KeyEvent.getKeyModifiersText(ActionEvent.CTRL_MASK);
                
                String redoKeyString =modifier+  "-Y";
                String undoKeyString = modifier+  "-Z";
                redoAction.putValue(Action.SHORT_DESCRIPTION,getString("redo") + ": " + commandHistory.getRedoText() + "  " + redoKeyString);
                undoAction.putValue(Action.SHORT_DESCRIPTION,getString("undo") + ": " + commandHistory.getUndoText() + "  " + undoKeyString);
            }
        });
        
        toolBar.add(back);
        toolBar.add(vor);
                
        closeButton.addActionListener(listener);
        appointmentEdit.addAppointmentListener(allocatableEdit);
        appointmentEdit.addAppointmentListener(listener);
        allocatableEdit.addChangeListener(listener);
        reservationInfo.addChangeListener(listener);
        reservationInfo.addDetailListener(listener);
        frame.addVetoableChangeListener(listener);

        frame.setIconImage( getI18n().getIcon("icon.edit_window_small").getImage());
        
        JPanel contentPane = (JPanel) frame.getContentPane();
        contentPane.setLayout(new BorderLayout());
        mainContent.setBorder(BorderFactory.createLoweredBevelBorder());
        contentPane.add(toolBar, BorderLayout.NORTH);
        contentPane.add(buttonsPanel, BorderLayout.SOUTH);
        contentPane.add(mainContent, BorderLayout.CENTER);
        Dimension dimension = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        frame.setSize(new Dimension(
                                    Math.min(dimension.width,990)
                                    // BJO 00000032 temp fix for filter out of frame bounds
                                     ,Math.min(dimension.height-10,720)
                                    //,Math.min(dimension.height-10,1000) 
                                    )
                      );
        
        Border  emptyLineBorder = new EmptyLineBorder();
        //BorderFactory.createEmptyBorder();
        Border border2 = BorderFactory.createTitledBorder(emptyLineBorder,getString("reservation.appointments"));
        Border border3 = BorderFactory.createTitledBorder(emptyLineBorder,getString("reservation.allocations"));
        appointmentEdit.getComponent().setBorder(border2);
        allocatableEdit.getComponent().setBorder(border3);
        
        saveButton.setText(getString("save"));
        saveButton.setIcon(getIcon("icon.save"));
        
        saveButtonTop.setText(getString("save"));
        saveButtonTop.setMnemonic(KeyEvent.VK_S);
        saveButtonTop.setIcon(getIcon("icon.save"));
        
        deleteButton.setText(getString("delete"));
        deleteButton.setIcon(getIcon("icon.delete"));
        
        closeButton.setText(getString("abort"));
        closeButton.setIcon(getIcon("icon.abort"));

        vor.setToolTipText(getString("redo"));
        vor.setIcon(getIcon("icon.redo"));
        
        back.setToolTipText(getString("undo"));
        back.setIcon(getIcon("icon.undo"));
    }

	protected void setAccelerator(JButton button, Action yourAction,
			KeyStroke keyStroke) {
		InputMap keyMap = new ComponentInputMap(button);
		keyMap.put(keyStroke, "action");

        ActionMap actionMap = new ActionMapUIResource();
		actionMap.put("action", yourAction);

        SwingUtilities.replaceUIActionMap(button, actionMap);
        SwingUtilities.replaceUIInputMap(button, JComponent.WHEN_IN_FOCUSED_WINDOW, keyMap);
	}

    protected void setSaved(boolean flag) {
        bSaved = flag;
        saveButton.setEnabled(!flag);
        saveButtonTop.setEnabled(!flag);
    }

    /* (non-Javadoc)
     * @see org.rapla.gui.edit.reservation.IReservationEdit#isModifiedSinceLastChange()
     */
    public boolean isModifiedSinceLastChange() {
        return !bSaved;
    }

    final private ReservationControllerImpl getPrivateReservationController() {
        return (ReservationControllerImpl) getService(ReservationController.class);
    }

    
    public void addAppointment( Date start, Date end) throws RaplaException 
    {
       	Appointment appointment = getModification().newAppointment( start, end );
       	AppointmentController controller = appointmentEdit.getAppointmentController();
       	Repeating repeating= controller.getRepeating();
        if ( repeating!= null  )
        {
        	appointment.setRepeatingEnabled(true);
        	appointment.getRepeating().setFrom(repeating);
        }
        appointmentEdit.addAppointment( appointment);
    }

    /* (non-Javadoc)
     * @see org.rapla.gui.edit.reservation.IReservationEdit#addAppointment(java.util.Date, java.util.Date, java.lang.String, int)
     */
    public void addAppointment(Date start, Date end, RepeatingType repeatingType, Integer repeatings) throws RaplaException {
    	Appointment appointment = getModification().newAppointment( start, end );
         if ( repeatingType != null ) {
         	ReservationHelper.makeRepeatingForPeriod( getPeriodModel(),appointment, repeatingType , repeatings);
         }
         appointmentEdit.addAppointment( appointment);
    }


    void deleteReservation() throws RaplaException {
        if (bDeleting)
            return;
        getLogger().debug("Reservation has been deleted.");
        DialogUI dlg = DialogUI.create(
                getContext()
                ,mainContent
                ,true
                ,getString("warning")
                ,getString("warning.reservation.delete")
        );
        dlg.setIcon(getIcon("icon.warning"));
        dlg.start();
        closeWindow();
    }

    void updateReservation(Reservation newReservation) throws RaplaException {
        if (bSaving)
            return;
        getLogger().debug("Reservation has been changed.");
        DialogUI dlg = DialogUI.create(
                getContext()
                ,mainContent
                ,true
                ,getString("warning")
                ,getString("warning.reservation.update")
        );
        commandHistory.clear();
        try {
            dlg.setIcon(getIcon("icon.warning"));
            dlg.start();
            this.original = newReservation;
            setReservation(getModification().edit(newReservation) , null);
        } catch (RaplaException ex) {
            showException(ex,frame);
        }
    }

    void refresh(ModificationEvent evt) throws RaplaException {
        allocatableEdit.dataChanged(evt);
    }

    void editReservation(Reservation reservation, AppointmentBlock appointmentBlock) throws RaplaException  {
    	ModificationModule mod = getModification();
        boolean bNew = false;
        if ( reservation.isPersistant()) {
            mutableReservation =  mod.edit(reservation);
            original = reservation;
        } else {
            try {
                original = getModification().getPersistant( reservation);
            } catch ( EntityNotFoundException ex)  {
                bNew = true;
                original = null;
            }
            mutableReservation = reservation;
        }
    	setSaved(!bNew);
        //printBlocks( appointment );
        this.bNew = bNew;
        deleteButton.setEnabled(!bNew);
        Appointment appointment = appointmentBlock != null ? appointmentBlock.getAppointment() : null;
        Date selectedDate = appointmentBlock != null ? new Date(appointmentBlock.getStart()) : null;
        setReservation(mutableReservation, appointment);
        appointmentEdit.getAppointmentController().setSelectedEditDate( selectedDate);

        setTitle();
        boolean packFrame = false;
        frame.place( true, packFrame);
        frame.setVisible( true );
        // Insert into open ReservationEditWindows, so that
        // we can't edit the same Reservation in different windows
        getPrivateReservationController().addReservationEdit(this);
        // #TODO this should be done in allocatableEdit
        //allocatableEdit.content.setDividerLocation(0.5);
        //frame.requestFocus();
        reservationInfo.requestFocus();
        getLogger().debug("New Reservation-Window created");
    }

    /* (non-Javadoc)
     * @see org.rapla.gui.edit.reservation.IReservationEdit#getReservation()
     */
    public Reservation getReservation() {
        return mutableReservation;
    }
    
    public Reservation getOriginal() {
		return original;
	}
    
    public Collection<Appointment> getSelectedAppointments() {
		Collection<Appointment> appointments = new ArrayList<Appointment>();
        for ( Appointment value:appointmentEdit.getListEdit().getSelectedValues())
		{
			appointments.add( value);
		}
        return appointments;
    }

    private void setTitle() {
        String title = getI18n().format((bNew) ?
                                        "new_reservation.format" : "edit_reservation.format"
                                        ,getName(mutableReservation));
        frame.setTitle(title);
    }

    private void setReservation(Reservation newReservation, Appointment appointment) throws RaplaException {
        commandHistory.clear();
    	this.mutableReservation = newReservation;
        appointmentEdit.setReservation(mutableReservation, appointment);
        allocatableEdit.setReservation(mutableReservation, original);
        reservationInfo.setReservation(mutableReservation);

        List<AppointmentStatusFactory> statusFactories = new ArrayList<AppointmentStatusFactory>();
       Collection<AppointmentStatusFactory> list= getContainer().lookupServicesFor(RaplaClientExtensionPoints.APPOINTMENT_STATUS);
       	for (AppointmentStatusFactory entry:list)
       	{
       		statusFactories.add(entry);
       	}
       	
        JPanel status =appointmentEdit.getListEdit().getStatusBar(); 
        status.removeAll();
        
        for (AppointmentStatusFactory factory: statusFactories)
        {
        	RaplaWidget statusWidget = factory.createStatus(getContext(), this);
        	status.add( statusWidget.getComponent());
        }

        // Should be done in initialization method of Appointmentstatus. The appointments are already selected then, so you can query the selected appointments thers.
//        if(appointment == null)
//        	fireAppointmentSelected(Collections.singleton(mutableReservation.getAppointments()[0]));
//        else
//        	fireAppointmentSelected(Collections.singleton(appointment));
    }


    public void closeWindow() {
        appointmentEdit.dispose();
        getPrivateReservationController().removeReservationEdit(this);
        frame.dispose();
        getLogger().debug("Edit window closed.");
    }


    class Listener extends AbstractAction implements AppointmentListener,ChangeListener,VetoableChangeListener, ReservationInfoEdit.DetailListener {
        private static final long serialVersionUID = 1L;

    // Implementation of ReservationListener
        public void appointmentRemoved(Collection<Appointment> appointment) {
            setSaved(false);
            ReservationEditImpl.this.fireAppointmentRemoved(appointment);
            fireReservationChanged(new ChangeEvent(appointmentEdit));
        }

        public void appointmentAdded(Collection<Appointment> appointment) {
            setSaved(false);
            ReservationEditImpl.this.fireAppointmentAdded(appointment);
            fireReservationChanged(new ChangeEvent(appointmentEdit));
        }

        public void appointmentChanged(Collection<Appointment> appointment) {
            setSaved(false);
            ReservationEditImpl.this.fireAppointmentChanged(appointment);
            fireReservationChanged(new ChangeEvent(appointmentEdit));
        }
        
        public void appointmentSelected(Collection<Appointment> appointment) {
            ReservationEditImpl.this.fireAppointmentSelected(appointment);
        }


        public void stateChanged(ChangeEvent evt) {
        	if (evt.getSource() == reservationInfo) {
        		getLogger().debug("ReservationInfo changed");
        		setSaved(false);
                setTitle();
        	}
            if (evt.getSource() == allocatableEdit) {
                getLogger().debug("AllocatableEdit changed");
                setSaved(false);
            }
            fireReservationChanged(evt);
        }
        public void detailChanged() {
            boolean isMain = reservationInfo.isMainView();
            if ( isMain != appointmentEdit.getComponent().isVisible() ) {
                appointmentEdit.getComponent().setVisible( isMain );
                allocatableEdit.getComponent().setVisible( isMain );
                if ( isMain ) {
                    tableLayout.setRow(0, TableLayout.PREFERRED);
                    tableLayout.setRow(1, TableLayout.PREFERRED);
                    tableLayout.setRow(2, TableLayout.FILL);
                } else {
                    tableLayout.setRow(0, TableLayout.FILL);
                    tableLayout.setRow(1, 0);
                    tableLayout.setRow(2, 0);
                }
                mainContent.validate();
            }
        }

        public void actionPerformed(ActionEvent evt) {
            try {
                if (evt.getSource() == saveButton || evt.getSource() == saveButtonTop) {
                    save();
                }
                if (evt.getSource() == deleteButton) {
                    delete();
                }
                if (evt.getSource() == closeButton) {
                    if (canClose())
                        closeWindow();
                }
            } catch (RaplaException ex) {
                showException(ex, null);
            }
        }

        public void vetoableChange(PropertyChangeEvent evt) throws PropertyVetoException {
        	if (!canClose())
                throw new PropertyVetoException("Don't close",evt);
            closeWindow();
        }
    }

    protected boolean canClose() {
        if (!isModifiedSinceLastChange())
            return true;

		try {
        DialogUI dlg = DialogUI.create(
                    getContext()
                        ,mainContent
                            ,true
                            ,getString("confirm-close.title")
                            ,getString("confirm-close.question")
                            ,new String[] {
                                getString("confirm-close.ok")
                                ,getString("back")
                            }
                            );
			dlg.setIcon(getIcon("icon.question"));
            dlg.setDefault(1);
            dlg.start();
            return (dlg.getSelectedIndex() == 0) ;
		} catch (RaplaException e) {
			return true;
		}

    }


    /* (non-Javadoc)
     * @see org.rapla.gui.edit.reservation.IReservationEdit#save()
     */
    public void save() throws RaplaException {        
        try {
        	frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        	
            bSaving = true;
            
            ReservationControllerImpl.ReservationSave saveCommand = getPrivateReservationController().new ReservationSave(mutableReservation, original, frame);
            if (getClientFacade().getCommandHistory().storeAndExecute(saveCommand))
            {
                setSaved(true);
            }
        } catch (RaplaException ex) {
            showException(ex, frame);
        } finally {
            frame.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            if (bSaved)
                closeWindow();
            bSaving = false;
        }
    }

    /* (non-Javadoc)
     * @see org.rapla.gui.edit.reservation.IReservationEdit#delete()
     */
    public void delete() throws RaplaException {
        try {
            DialogUI dlg = getInfoFactory().createDeleteDialog(new Object[] {mutableReservation}
                                                               ,frame);
            dlg.start();
            if (dlg.getSelectedIndex() == 0) {
                bDeleting = true;
                Set<Reservation> reservationsToRemove = Collections.singleton( original);
                Set<Appointment> appointmentsToRemove = Collections.emptySet();
                Map<Appointment, List<Date>> exceptionsToAdd = Collections.emptyMap();
                CommandUndo<RaplaException> deleteCommand = getPrivateReservationController().new DeleteBlocksCommand(reservationsToRemove, appointmentsToRemove, exceptionsToAdd)
                {
                    public String getCommandoName() {
                        return getString("delete") + " " + getString("reservation");
                    }
                };
                getClientFacade().getCommandHistory().storeAndExecute(deleteCommand);
                closeWindow();
            }
        } finally {
            bDeleting = false;
        }
    }

    protected ChangeListener[] getReservationInfpListeners() {
        return changeListenerList.toArray(new ChangeListener[]{});
    }

    protected void fireReservationChanged(ChangeEvent evt) {
        for (ChangeListener listener: getReservationInfpListeners())
        {
            listener.stateChanged( evt);
        }
    }

    public void addReservationChangeListener(ChangeListener listener) {
        changeListenerList.add(listener);
    }

    public void removeReservationChangeListener(ChangeListener listener) {
        changeListenerList.remove( listener);
        
    }

    
    
    
}