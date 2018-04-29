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

import io.reactivex.functions.Consumer;
import org.rapla.RaplaResources;
import org.rapla.client.AppointmentListener;
import org.rapla.client.RaplaWidget;
import org.rapla.client.ReservationEdit;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.AppointmentStatusFactory;
import org.rapla.client.internal.RaplaColors;
import org.rapla.client.swing.ReservationToolbarExtension;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.internal.edit.reservation.AllocatableSelection.AllocatableSelectionFactory;
import org.rapla.client.swing.internal.edit.reservation.AppointmentListEdit.AppointmentListEditFactory;
import org.rapla.client.swing.internal.edit.reservation.ReservationInfoEdit.ReservationInfoEditFactory;
import org.rapla.client.swing.toolkit.AWTColorUtil;
import org.rapla.client.swing.toolkit.EmptyLineBorder;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.components.layout.TableLayout;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.components.i18n.I18nIcon;
import org.rapla.entities.User;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.RaplaObjectAnnotations;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;
import org.rapla.storage.PermissionController;

import javax.inject.Inject;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ComponentInputMap;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
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
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

@DefaultImplementation(context = InjectionContext.swing, of = ReservationEdit.class)
public final class ReservationEditImpl extends AbstractAppointmentEditor implements ReservationEdit<Component>
{
    protected Reservation mutableReservation;
    private Reservation original;

    CommandHistory commandHistory;
    JToolBar toolBar = new JToolBar();
    JLabel statusLabel = new JLabel();
    RaplaButton saveButtonTop = new RaplaButton();
    RaplaButton saveButton = new RaplaButton();
    RaplaButton deleteButton = new RaplaButton();
    RaplaButton closeButton = new RaplaButton();

    Action undoAction = new AbstractAction()
    {
        private static final long serialVersionUID = 1L;

        public void actionPerformed(ActionEvent e)
        {
            try
            {
                commandHistory.undo();
            }
            catch (Exception ex)
            {
                dialogUiFactory.showException(ex, new SwingPopupContext(getMainComponent(), null));
            }
        }
    };
    Action redoAction = new AbstractAction()
    {
        private static final long serialVersionUID = 1L;

        public void actionPerformed(ActionEvent e)
        {
            try
            {
                commandHistory.redo();
            }
            catch (Exception ex)
            {
                dialogUiFactory.showException(ex, new SwingPopupContext(getMainComponent(), null));
            }
        }
    };

    JPanel mainContent = new JPanel();
    ReservationInfoEdit reservationInfo;
    AppointmentListEdit appointmentEdit;
    AllocatableSelection allocatableEdit;

    boolean bNew;

    TableLayout tableLayout = new TableLayout(new double[][] { { TableLayout.FILL }, { TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.FILL } });

    private final Listener listener = new Listener();

    private final Set<AppointmentStatusFactory> appointmentStatusFactories;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final PermissionController permissionController;
    private final Set<ReservationToolbarExtension> reservationToolbarExtensions;
    private final JPanel contentPane;
    Consumer<Collection<Reservation>> saveCmd;
    Runnable closeCmd;
    Runnable deleteCmd;

    @Inject
    public ReservationEditImpl(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,
            Set<AppointmentStatusFactory> appointmentStatusFactories,
            DialogUiFactoryInterface dialogUiFactory, ReservationInfoEditFactory reservationInfoEditFactory,
            AppointmentListEditFactory appointmentListEditFactory, AllocatableSelectionFactory allocatableSelectionFactory,
            Set<ReservationToolbarExtension> reservationToolbarExtensions) throws RaplaInitializationException
    {
        super(facade, i18n, raplaLocale, logger);
        this.reservationToolbarExtensions = reservationToolbarExtensions;
        this.appointmentStatusFactories = appointmentStatusFactories;
        this.dialogUiFactory = dialogUiFactory;
        this.permissionController = facade.getRaplaFacade().getPermissionController();
        commandHistory = new CommandHistory();
        try
        {
            this.reservationInfo = reservationInfoEditFactory.create(commandHistory);
            this.appointmentEdit = appointmentListEditFactory.create(commandHistory);
        }
        catch (RaplaException ex)
        {
            throw new RaplaInitializationException(ex);
        }
        allocatableEdit = allocatableSelectionFactory.create(true, commandHistory, true);
        mainContent.setLayout(tableLayout);
        mainContent.add(reservationInfo.getComponent(), "0,0");
        mainContent.add(appointmentEdit.getComponent(), "0,1");
        mainContent.add(allocatableEdit.getComponent(), "0,2");
        saveButtonTop.setAction(listener);
        saveButton.setAction(listener);
        toolBar.setFloatable(false);
        saveButton.setAlignmentY(JButton.CENTER_ALIGNMENT);
        deleteButton.setAlignmentY(JButton.CENTER_ALIGNMENT);
        closeButton.setAlignmentY(JButton.CENTER_ALIGNMENT);
        JPanel buttonsPanel = new JPanel();
        buttonsPanel.add(saveButton);
        buttonsPanel.add(closeButton);
        toolBar.add(saveButtonTop);
        toolBar.add(deleteButton);
        deleteButton.setAction(listener);
        RaplaButton vor = new RaplaButton();
        RaplaButton back = new RaplaButton();

        // Undo-Buttons in Toolbar
        //        final JPanel undoContainer = new JPanel();

        redoAction.setEnabled(false);
        undoAction.setEnabled(false);

        vor.setAction(redoAction);
        back.setAction(undoAction);
        final KeyStroke undoKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_Z, ActionEvent.CTRL_MASK);
        setAccelerator(back, undoAction, undoKeyStroke);
        final KeyStroke redoKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_Y, ActionEvent.CTRL_MASK);
        setAccelerator(vor, redoAction, redoKeyStroke);

        commandHistory.addCommandHistoryChangedListener(()->
            {
                redoAction.setEnabled(commandHistory.canRedo());
                boolean canUndo = commandHistory.canUndo();
                undoAction.setEnabled(canUndo);
                String modifier = KeyEvent.getKeyModifiersText(ActionEvent.CTRL_MASK);

                String redoKeyString = modifier + "-Y";
                String undoKeyString = modifier + "-Z";
                redoAction.putValue(Action.SHORT_DESCRIPTION, getString("redo") + ": " + commandHistory.getRedoText() + "  " + redoKeyString);
                undoAction.putValue(Action.SHORT_DESCRIPTION, getString("undo") + ": " + commandHistory.getUndoText() + "  " + undoKeyString);
        });

        toolBar.add(back);
        toolBar.add(vor);

        for (ReservationToolbarExtension extension : reservationToolbarExtensions)
        {
            for (RaplaWidget comp : extension.createExtensionButtons(this))
            {
                if ( comp != null)
                {
                    toolBar.add((Component)comp.getComponent());
                }
            }
        }


        closeButton.addActionListener(listener);
        appointmentEdit.addAppointmentListener(allocatableEdit);
        appointmentEdit.addAppointmentListener(listener);
        allocatableEdit.addChangeListener(listener);
        reservationInfo.addChangeListener(listener);
        reservationInfo.addDetailListener(listener);

        JPanel toolBarPanel = new JPanel();
        statusLabel.setForeground( AWTColorUtil.getColorForHex(RaplaColors.HIGHLICHT_COLOR));
        toolBar.addSeparator();
        toolBar.add(Box.createHorizontalGlue());
        toolBar.add( statusLabel);
        toolBarPanel.setLayout( new BorderLayout());
        toolBarPanel.add( toolBar, BorderLayout.CENTER);
        //toolBarPanel.add( statusLabel, BorderLayout.EAST);

        contentPane = new JPanel();
        contentPane.setLayout(new BorderLayout());
        mainContent.setBorder(BorderFactory.createLoweredBevelBorder());
        contentPane.add(toolBarPanel, BorderLayout.NORTH);
        contentPane.add(buttonsPanel, BorderLayout.SOUTH);
        contentPane.add(mainContent, BorderLayout.CENTER);

        Border emptyLineBorder = new EmptyLineBorder();
        Border border2 = BorderFactory.createTitledBorder(emptyLineBorder, getString("reservation.appointments"));
        Border border3 = BorderFactory.createTitledBorder(emptyLineBorder, getString("reservation.allocations"));
        appointmentEdit.getComponent().setBorder(border2);
        allocatableEdit.getComponent().setBorder(border3);

        saveButton.setText(getString("save"));
        setIcon(saveButton,i18n.getIcon("icon.save"));

        saveButtonTop.setText(getString("save"));
        saveButtonTop.setMnemonic(KeyEvent.VK_S);
        setIcon(saveButtonTop,i18n.getIcon("icon.save"));

        deleteButton.setText(getString("delete"));
        setIcon(deleteButton,i18n.getIcon("icon.delete"));

        closeButton.setText(getString("abort"));
        setIcon(closeButton,i18n.getIcon("icon.abort"));

        vor.setToolTipText(getString("redo"));
        setIcon(vor,i18n.getIcon("icon.redo"));

        back.setToolTipText(getString("undo"));
        setIcon(back,i18n.getIcon("icon.undo"));
    }

    public void setIcon(JButton button, I18nIcon icon)
    {
        button.setIcon(RaplaImages.getIcon( icon));
    }

    @Override
    public Component getComponent()
    {
        return contentPane;
    }

    protected void setAccelerator(JButton button, Action yourAction, KeyStroke keyStroke)
    {
        InputMap keyMap = new ComponentInputMap(button);
        keyMap.put(keyStroke, "action");

        ActionMap actionMap = new ActionMapUIResource();
        actionMap.put("action", yourAction);

        SwingUtilities.replaceUIActionMap(button, actionMap);
        SwingUtilities.replaceUIInputMap(button, JComponent.WHEN_IN_FOCUSED_WINDOW, keyMap);
    }

    @Override
    public void setHasChanged(boolean flag)
    {
        super.setHasChanged(flag);
        saveButton.setEnabled(flag);
        saveButtonTop.setEnabled(flag);
    }

    public Promise<Void> addAppointment(Date start, Date end)
    {
        return getFacade().newAppointmentAsync(new TimeInterval(start, end)).thenAccept( (appointment)->
        {
            AppointmentController controller = appointmentEdit.getAppointmentController();
            Repeating repeating = controller.getRepeating();
            if (repeating != null)
            {
                appointment.setRepeatingEnabled(true);
                appointment.getRepeating().setFrom(repeating);
            }
            appointmentEdit.addAppointment(appointment);
        });
    }

    @Override
    public void updateView(ModificationEvent evt)
    {
        try
        {
            allocatableEdit.dataChanged(evt);
        }
        catch (RaplaException e)
        {
            dialogUiFactory.showException(e, null);
        }
    }

    @Override
    public void fireChange()
    {
        try
        {
            setReservation( mutableReservation, appointmentEdit.getAppointmentController().getAppointment());
        }
        catch (RaplaException e)
        {
            getLogger().error( e.getMessage(),e);
        }
        setHasChanged( true);
    }

    @Override
    public void start(Consumer<Collection<Reservation>> saveCmd, Runnable closeCmd, Runnable deleteCmd) {
        this.saveCmd = saveCmd;
        this.closeCmd = closeCmd;
        this.deleteCmd = deleteCmd;
    }


    @Override
    public void editReservation(Reservation reservation, Reservation original,AppointmentBlock appointmentBlock)
            throws RaplaException
    {
        statusLabel.setText( reservation.getAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE) != null ? getI18n().getString("edit-templates") : "");
        boolean bNew = !original.isReadOnly();
        this.original = original;
        mutableReservation = reservation;
        setHasChanged(bNew);
        this.bNew = bNew;
        deleteButton.setEnabled(!bNew);
        Appointment appointment = appointmentBlock != null ? appointmentBlock.getAppointment() : null;
        Appointment mutableAppointment = null;
        for (Appointment app : mutableReservation.getAppointments())
        {
            if (appointment != null && app.equals(appointment))
            {
                mutableAppointment = app;
            }
        }
        Date selectedDate = appointmentBlock != null ? new Date(appointmentBlock.getStart()) : null;
        setReservation(mutableReservation, mutableAppointment);
        appointmentEdit.getAppointmentController().setSelectedEditDate(selectedDate);

        boolean packFrame = false;
        // Insert into open ReservationEditWindows, so that
        // we can't edit the same Reservation in different windows
        reservationInfo.requestFocus();
        getLogger().debug("New Reservation-Window created");
        final User user = getUser();
        deleteButton.setEnabled(permissionController.canDelete(reservation, user));
        if (!permissionController.canModify(reservation, user))
        {
            disableComponentAndAllChildren(appointmentEdit.getComponent());
            disableComponentAndAllChildren(reservationInfo.getComponent());
        }
    }

    static void disableComponentAndAllChildren(Container component)
    {
        component.setEnabled(false);
        Component[] components = component.getComponents();
        for (int i = 0; i < components.length; i++)
        {
            if (components[i] instanceof Container)
            {
                disableComponentAndAllChildren((Container) components[i]);
            }
        }
    }

    @Override
    public Reservation getReservation()
    {
        return mutableReservation;
    }

    public Reservation getOriginal()
    {
        return original;
    }

    @Override
    public Collection<Appointment> getSelectedAppointments()
    {
        Collection<Appointment> appointments = new ArrayList<>();
        for (Appointment value : appointmentEdit.getListEdit().getSelectedValues())
        {
            appointments.add(value);
        }
        return appointments;
    }

    @Override
    public boolean isNew()
    {
        return bNew;
    }

    public void setReservation(Reservation newReservation, Appointment mutableAppointment) throws RaplaException
    {
        commandHistory.clear();

        this.mutableReservation = newReservation;
        appointmentEdit.setReservation(mutableReservation, mutableAppointment);
        Collection<Reservation> emptySet = Collections.emptySet();
        Collection<Reservation> originalCollection = original != null ? Collections.singleton(original) : emptySet;
        allocatableEdit.setReservation(Collections.singleton(mutableReservation), originalCollection);
        allocatableEdit.appointmentSelected( Collections.singletonList( mutableAppointment));
        reservationInfo.setReservation(mutableReservation);

        List<AppointmentStatusFactory> statusFactories = new ArrayList<>();
        for (AppointmentStatusFactory entry : appointmentStatusFactories)
        {
            statusFactories.add(entry);
        }

        for (ReservationToolbarExtension extension : reservationToolbarExtensions)
        {
            extension.setReservation(newReservation, mutableAppointment);
        }

        JPanel status = appointmentEdit.getListEdit().getStatusBar();
        status.removeAll();

        for (AppointmentStatusFactory factory : statusFactories)
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

    class Listener extends AbstractAction implements AppointmentListener, ChangeListener, ReservationInfoEdit.DetailListener
    {
        private static final long serialVersionUID = 1L;

        // Implementation of ReservationListener
        public void appointmentRemoved(Collection<Appointment> appointment)
        {
            ReservationEditImpl.this.fireAppointmentRemoved(appointment);
            fireReservationChanged(new ChangeEvent(appointmentEdit));
        }

        public void appointmentAdded(Collection<Appointment> appointment)
        {
            ReservationEditImpl.this.fireAppointmentAdded(appointment);
            fireReservationChanged(new ChangeEvent(appointmentEdit));
        }

        public void appointmentChanged(Collection<Appointment> appointment)
        {
            ReservationEditImpl.this.fireAppointmentChanged(appointment);
            fireReservationChanged(new ChangeEvent(appointmentEdit));
        }

        public void appointmentSelected(Collection<Appointment> appointment)
        {
            ReservationEditImpl.this.fireAppointmentSelected(appointment);
        }

        public void stateChanged(ChangeEvent evt)
        {
            if (evt.getSource() == reservationInfo)
            {
                getLogger().debug("ReservationInfo changed");
                //        		PermissionContainer.Util.processOldPermissionModify(mutableReservation, original);
                setHasChanged(true);
            }
            if (evt.getSource() == allocatableEdit)
            {
                getLogger().debug("AllocatableEdit changed");
                setHasChanged(true);
            }
            fireReservationChanged(evt);
        }

        public void detailChanged()
        {
            boolean isMain = reservationInfo.isMainView();
            if (isMain != appointmentEdit.getComponent().isVisible())
            {
                appointmentEdit.getComponent().setVisible(isMain);
                allocatableEdit.getComponent().setVisible(isMain);
                if (isMain)
                {
                    tableLayout.setRow(0, TableLayout.PREFERRED);
                    tableLayout.setRow(1, TableLayout.PREFERRED);
                    tableLayout.setRow(2, TableLayout.FILL);
                }
                else
                {
                    tableLayout.setRow(0, TableLayout.FILL);
                    tableLayout.setRow(1, 0);
                    tableLayout.setRow(2, 0);
                }
                mainContent.validate();
            }
        }

        public void actionPerformed(ActionEvent evt)
        {
            if (evt.getSource() == saveButton || evt.getSource() == saveButtonTop)
            {
                try {
                    saveCmd.accept(Collections.singleton(mutableReservation));
                } catch (Exception ex) {
                    dialogUiFactory.showException( ex,null);
                }
            }
            if (evt.getSource() == deleteButton)
            {
                deleteCmd.run();
            }
            if (evt.getSource() == closeButton)
            {
                closeCmd.run();
            }
        }
    }

    public CommandHistory getCommandHistory()
    {
        return getClientFacade().getCommandHistory();
    }


    protected void fireReservationChanged(ChangeEvent evt)
    {
        setHasChanged(true);
        //fireReservationChanged(new ChangeEvent(appointmentEdit));
    }

    @Override
    public Map<Reservation, Reservation> getEditMap() {
        return Collections.singletonMap( original, mutableReservation);
    }
}