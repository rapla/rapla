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
import org.rapla.client.AppointmentListener;
import org.rapla.client.PopupContext;
import org.rapla.client.ReservationController;
import org.rapla.client.ReservationEdit;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.AppointmentStatusFactory;
import org.rapla.client.internal.ReservationControllerImpl;
import org.rapla.client.swing.InfoFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.internal.edit.reservation.AllocatableSelection.AllocatableSelectionFactory;
import org.rapla.client.swing.internal.edit.reservation.AppointmentListEdit.AppointmentListEditFactory;
import org.rapla.client.swing.internal.edit.reservation.ReservationInfoEdit.ReservationInfoEditFactory;
import org.rapla.client.swing.toolkit.EmptyLineBorder;
import org.rapla.client.swing.toolkit.FrameControllerList;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.client.swing.toolkit.RaplaFrame;
import org.rapla.client.swing.toolkit.RaplaWidget;
import org.rapla.components.layout.TableLayout;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.components.util.undo.CommandHistoryChangedListener;
import org.rapla.components.util.undo.CommandUndo;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationModule;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.storage.PermissionController;

import javax.inject.Inject;
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
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Toolkit;
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

final class ReservationEditImpl extends AbstractAppointmentEditor implements ReservationEdit
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
			    dialogUiFactory.showException(ex, new SwingPopupContext(getMainComponent(), null));
			}
		}
	};
    Action redoAction = new AbstractAction() {
        private static final long serialVersionUID = 1L;

		public void actionPerformed(ActionEvent e) {
			try {
				commandHistory.redo();
			} catch (Exception ex) {
			    dialogUiFactory.showException(ex, new SwingPopupContext(getMainComponent(), null));
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


    private final Set<AppointmentStatusFactory> appointmentStatusFactories;
    private final ReservationControllerImpl reservationController;
    private final InfoFactory infoFactory;
    private final RaplaImages raplaImages;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final PermissionController permissionController;

    @Inject
    public ReservationEditImpl(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,
            Set<AppointmentStatusFactory> appointmentStatusFactories, ReservationController reservationController, InfoFactory infoFactory,
            RaplaImages raplaImages, DialogUiFactoryInterface dialogUiFactory, ReservationInfoEditFactory reservationInfoEditFactory,
            AppointmentListEditFactory appointmentListEditFactory, AllocatableSelectionFactory allocatableSelectionFactory,
             FrameControllerList frameControllerList) throws RaplaException
    {
        super(facade, i18n, raplaLocale, logger);
        this.appointmentStatusFactories = appointmentStatusFactories;
        this.infoFactory = infoFactory;
        this.raplaImages = raplaImages;
        this.dialogUiFactory = dialogUiFactory;
        this.permissionController = facade.getPermissionController();
        this.reservationController = (ReservationControllerImpl) reservationController;
        commandHistory = new CommandHistory();
        this.reservationInfo = reservationInfoEditFactory.create(commandHistory);
        this.appointmentEdit = appointmentListEditFactory.create(commandHistory);
        allocatableEdit = allocatableSelectionFactory.create(true, commandHistory);

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

        frame = new RaplaFrame(frameControllerList);
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

        frame.setIconImage( raplaImages.getIconFromKey("icon.edit_window_small").getImage());
        
        JPanel contentPane = (JPanel) frame.getContentPane();
        contentPane.setLayout(new BorderLayout());
        mainContent.setBorder(BorderFactory.createLoweredBevelBorder());
        contentPane.add(toolBar, BorderLayout.NORTH);
        contentPane.add(buttonsPanel, BorderLayout.SOUTH);
        contentPane.add(mainContent, BorderLayout.CENTER);
        Dimension dimension = Toolkit.getDefaultToolkit().getScreenSize();
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
        saveButton.setIcon(raplaImages.getIconFromKey("icon.save"));
        
        saveButtonTop.setText(getString("save"));
        saveButtonTop.setMnemonic(KeyEvent.VK_S);
        saveButtonTop.setIcon(raplaImages.getIconFromKey("icon.save"));
        
        deleteButton.setText(getString("delete"));
        deleteButton.setIcon(raplaImages.getIconFromKey("icon.delete"));
        
        closeButton.setText(getString("abort"));
        closeButton.setIcon(raplaImages.getIconFromKey("icon.abort"));

        vor.setToolTipText(getString("redo"));
        vor.setIcon(raplaImages.getIconFromKey("icon.redo"));
        
        back.setToolTipText(getString("undo"));
        back.setIcon(raplaImages.getIconFromKey("icon.undo"));
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
     * @see org.rapla.client.swing.gui.edit.reservation.IReservationEdit#isModifiedSinceLastChange()
     */
    public boolean isModifiedSinceLastChange() {
        return !bSaved;
    }

    final private ReservationControllerImpl getPrivateReservationController() {
        return reservationController;
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

    public void deleteReservation() throws RaplaException {
        if (bDeleting)
            return;
        getLogger().debug("Reservation has been deleted.");
        DialogInterface dlg = dialogUiFactory.create(
                new SwingPopupContext(mainContent, null)
                ,true
                ,getString("warning")
                ,getString("warning.reservation.delete")
        );
        dlg.setIcon("icon.warning");
        dlg.start(true);
        closeWindow();
    }

    public void updateReservation(Reservation newReservation) throws RaplaException {
        if (bSaving)
            return;
        getLogger().debug("Reservation has been changed.");
        DialogInterface dlg = dialogUiFactory.create(
                new SwingPopupContext(mainContent, null)
                ,true
                ,getString("warning")
                ,getString("warning.reservation.update")
        );
        commandHistory.clear();
        try {
            dlg.setIcon("icon.warning");
            dlg.start(true);
            this.original = newReservation;
            setReservation(getModification().edit(newReservation) , null);
        } catch (RaplaException ex) {
            dialogUiFactory.showException(ex,new SwingPopupContext(frame, null));
        }
    }

    public void refresh(ModificationEvent evt) throws RaplaException {
        allocatableEdit.dataChanged(evt);
    }

    public void editReservation(Reservation reservation, AppointmentBlock appointmentBlock) throws RaplaException  {
    	ModificationModule mod = getModification();
        boolean bNew = false;
        if ( reservation.isReadOnly()) {
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
    	Appointment mutableAppointment = null;
        for (Appointment app:mutableReservation.getAppointments()) {
        	if ( appointment != null && app.equals(appointment))
        	{
        		mutableAppointment = app;
        	}
        }
        Date selectedDate = appointmentBlock != null ? new Date(appointmentBlock.getStart()) : null;
        setReservation(mutableReservation, mutableAppointment);
        appointmentEdit.getAppointmentController().setSelectedEditDate( selectedDate);

        setTitle();
        boolean packFrame = false;
        frame.place( true, packFrame);
        frame.setVisible( true );
        // Insert into open ReservationEditWindows, so that
        // we can't edit the same Reservation in different windows
        getPrivateReservationController().addReservationEdit(this);
        reservationInfo.requestFocus();
        getLogger().debug("New Reservation-Window created");
        final ClientFacade clientFacade = getClientFacade();
        final User user = clientFacade.getUser();
        deleteButton.setEnabled( permissionController.canAdmin( reservation, user ));
        if ( !permissionController.canModify( reservation, user) )
        {
            disableComponentAndAllChildren(appointmentEdit.getComponent());
            disableComponentAndAllChildren(reservationInfo.getComponent());
        }
    }

    @Override
    public void toFront()
    {
        frame.requestFocus();
        frame.toFront();
    }
    
    static void disableComponentAndAllChildren(Container component) {
        component.setEnabled( false );
        Component[] components = component.getComponents();
        for ( int i=0; i< components.length; i++)
        {
            if ( components[i] instanceof Container) {
                disableComponentAndAllChildren( (Container) components[i] );
            }
        }
    }


    @Override
    public Reservation getReservation() {
        return mutableReservation;
    }
    
    public Reservation getOriginal() {
		return original;
	}
    
    @Override
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

    private void setReservation(Reservation newReservation, Appointment mutableAppointment) throws RaplaException {
    	commandHistory.clear();
    	this.mutableReservation = newReservation;
        appointmentEdit.setReservation(mutableReservation, mutableAppointment);
        Collection<Reservation> emptySet = Collections.emptySet();
        Collection<Reservation> originalCollection = original != null ? Collections.singleton( original ) : emptySet;
        allocatableEdit.setReservation(Collections.singleton(mutableReservation), originalCollection);
        reservationInfo.setReservation(mutableReservation);

        List<AppointmentStatusFactory> statusFactories = new ArrayList<AppointmentStatusFactory>();
       	for (AppointmentStatusFactory entry: appointmentStatusFactories)
       	{
       		statusFactories.add(entry);
       	}
       	
        JPanel status =appointmentEdit.getListEdit().getStatusBar(); 
        status.removeAll();
        
        for (AppointmentStatusFactory factory: statusFactories)
        {
        	RaplaWidget statusWidget = factory.createStatus(this);
        	status.add((Component) statusWidget.getComponent());
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
//        		PermissionContainer.Util.processOldPermissionModify(mutableReservation, original);
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
                dialogUiFactory.showException(ex, new SwingPopupContext(null, null));
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
		    DialogInterface dlg = dialogUiFactory.create(
                new SwingPopupContext(mainContent, null)
                            ,true
                            ,getString("confirm-close.title")
                            ,getString("confirm-close.question")
                            ,new String[] {
                                getString("confirm-close.ok")
                                ,getString("back")
                            }
                            );
			dlg.setIcon("icon.question");
            dlg.setDefault(1);
            dlg.start(true);
            return (dlg.getSelectedIndex() == 0) ;
		} catch (RaplaException e) {
			return true;
		}

    }


    /* (non-Javadoc)
     * @see org.rapla.client.swing.gui.edit.reservation.IReservationEdit#save()
     */
    @Override
    public void save() throws RaplaException {        
        try {
        	frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        	
            bSaving = true;
            
            PopupContext popupContext = createPopupContext(frame, null);
            ReservationControllerImpl.ReservationSave saveCommand = getPrivateReservationController().new ReservationSave(Collections.singleton(mutableReservation), original != null ? Collections.singleton( original) : null, popupContext);
            if (getClientFacade().getCommandHistory().storeAndExecute(saveCommand))
            {
                setSaved(true);
            }
        } catch (RaplaException ex) {
            dialogUiFactory.showException(ex, new SwingPopupContext(frame, null));
        } finally {
            frame.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            if (bSaved)
                closeWindow();
            bSaving = false;
        }
    }

    /* (non-Javadoc)
     * @see org.rapla.client.swing.gui.edit.reservation.IReservationEdit#delete()
     */
    @Override
    public void delete() throws RaplaException {
        try {
            DialogInterface dlg = infoFactory.createDeleteDialog(new Object[] {mutableReservation}
                                                               ,new SwingPopupContext(frame, null));
            dlg.start(true);
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