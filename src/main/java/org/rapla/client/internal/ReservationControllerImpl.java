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
package org.rapla.client.internal;

import org.jetbrains.annotations.NotNull;
import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.ReservationController;
import org.rapla.client.dialog.DeleteDialogInterface;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.EventCheck;
import org.rapla.client.internal.HTMLInfo.Row;
import org.rapla.client.internal.RaplaClipboard.CopyType;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.DateTools.TimeWithoutTimezone;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.components.util.undo.CommandUndo;
import org.rapla.components.i18n.I18nBundle;
import org.rapla.components.i18n.I18nIcon;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@DefaultImplementation(of= ReservationController.class,context = InjectionContext.client)
public class ReservationControllerImpl implements ReservationController {
    /**
     * We store all open ReservationEditWindows with their reservationId
     * in a map, to lookupDeprecated if the reservation is already beeing edited.
     * That prevents editing the same Reservation in different windows
     */
    AppointmentFormater appointmentFormater;
    ClientFacade facade;
    private RaplaLocale raplaLocale;
    private final Logger logger;
    private final RaplaResources i18n;
    private final CalendarSelectionModel calendarModel;
    private final RaplaClipboard clipboard;
    private final DialogUiFactoryInterface dialogUI;
    private final DeleteDialogInterface deleteDialog;

    private final Provider<Set<EventCheck>> eventCheckers;

    @Inject
    public ReservationControllerImpl(ClientFacade facade, RaplaLocale raplaLocale, Logger logger, RaplaResources i18n, AppointmentFormater appointmentFormater,
                                     CalendarSelectionModel calendarModel, RaplaClipboard clipboard, DialogUiFactoryInterface dialogUI, DeleteDialogInterface deleteDialog, Provider<Set<EventCheck>> eventCheckers) {
        this.facade = facade;
        this.raplaLocale = raplaLocale;
        this.logger = logger;
        this.i18n = i18n;
        this.calendarModel = calendarModel;
        this.appointmentFormater = appointmentFormater;
        this.clipboard = clipboard;
        this.dialogUI = dialogUI;
        this.deleteDialog = deleteDialog;
        this.eventCheckers = eventCheckers;
    }

    protected RaplaFacade getFacade() {
        return facade.getRaplaFacade();
    }

    protected ClientFacade getClientFacade() {
        return facade;
    }

    protected RaplaLocale getRaplaLocale() {
        return raplaLocale;
    }

    protected Logger getLogger() {
        return logger;
    }

    protected RaplaResources getI18n() {
        return i18n;
    }


    @Override
    public Promise<Void> deleteReservations(Set<Reservation> reservationsToRemove, PopupContext context) {
        final Promise<Boolean> booleanPromise = deleteDialog.showDeleteDialog(context, reservationsToRemove.toArray(Reservation.RESERVATION_ARRAY));
        final Promise<Void> no_delete = booleanPromise.thenCompose(deleted -> {
            final Promise promise;
            Set<Appointment> appointmentsToRemove = Collections.emptySet();
            Map<Appointment, List<Date>> exceptionsToAdd = Collections.emptyMap();
            CommandUndo<RaplaException> command = new DeleteBlocksCommand(getClientFacade(), i18n, reservationsToRemove,
                    appointmentsToRemove, exceptionsToAdd) {
                public String getCommandoName() {
                    return i18n.getString("delete") + " " + i18n.getString("reservation");
                }
            };
            if (deleted) {

                CommandHistory commanHistory = getCommandHistory();
                promise = commanHistory.storeAndExecute(command);
            } else {
                promise = new ResolvedPromise<Void>(new CommandAbortedException("No delete"));
            }
            return promise;
        });
        return no_delete;
    }

    public Promise<Void> deleteBlocks(Collection<AppointmentBlock> blockList, PopupContext context) {
        return deleteDialog.showDeleteDialog(context, blockList.toArray()).thenCompose(deleted -> {
            if (!deleted)
                return ResolvedPromise.VOID_PROMISE;


            Set<Appointment> appointmentsToRemove = new LinkedHashSet<>();
            HashMap<Appointment, List<Date>> exceptionsToAdd = new LinkedHashMap<>();
            HashMap<Reservation, Integer> appointmentsRemoved = new LinkedHashMap<>();
            Set<Reservation> reservationsToRemove = new LinkedHashSet<>();

            for (AppointmentBlock block : blockList) {
                Appointment appointment = block.getAppointment();
                Date from = new Date(block.getStart());
                Repeating repeating = appointment.getRepeating();
                boolean exceptionsAdded = false;
                if (repeating != null) {
                    List<Date> dateList = exceptionsToAdd.get(appointment);
                    if (dateList == null) {
                        dateList = new ArrayList<>();
                        exceptionsToAdd.put(appointment, dateList);
                    }
                    dateList.add(from);
                    if (isNotEmptyWithExceptions(appointment, dateList)) {
                        exceptionsAdded = true;
                    } else {
                        exceptionsToAdd.remove(appointment);
                    }
                }
                if (!exceptionsAdded) {
                    boolean added = appointmentsToRemove.add(appointment);
                    if (added) {
                        Reservation reservation = appointment.getReservation();
                        Integer count = appointmentsRemoved.get(reservation);
                        if (count == null) {
                            count = 0;
                        }
                        count++;
                        appointmentsRemoved.put(reservation, count);
                    }
                }
            }

            for (Reservation reservation : appointmentsRemoved.keySet()) {
                Integer count = appointmentsRemoved.get(reservation);
                Appointment[] appointments = reservation.getAppointments();
                if (count == appointments.length) {
                    reservationsToRemove.add(reservation);
                    for (Appointment appointment : appointments) {
                        appointmentsToRemove.remove(appointment);
                    }
                }
            }

            DeleteBlocksCommand command = new DeleteBlocksCommand(getClientFacade(), i18n, reservationsToRemove, appointmentsToRemove, exceptionsToAdd);
            CommandHistory commanHistory = getCommandHistory();
            return commanHistory.storeAndExecute(command);
        });
    }

    protected void showException(Throwable ex, PopupContext sourceComponent)
    {
        dialogUI.showException(ex, sourceComponent);
        getLogger().error(ex.getMessage(), ex);
    }

    protected Promise<Integer> showDialog( PopupContext popupContext, List<String> optionList, List<I18nIcon> iconList, String title, String content,
                                          I18nIcon dialogIcon)
    {
        DialogInterface dialog = dialogUI.createTextDialog(
                popupContext
                , title
                ,content
                ,optionList.toArray(new String[] {})
        );
        if ( dialogIcon != null)
        {
            dialog.setIcon(dialogIcon);
        }
        for ( int i=0;i< optionList.size();i++)
        {
            final I18nIcon string = iconList.get( i);
            if ( string != null)
            {
                dialog.getAction(i).setIcon(string);
            }
        }
        return dialog.start(true);
    }


/*
    protected boolean showDeleteDialog(PopupContext context, Object[] deletables)
    {
        InfoFactory infoFactory = getService(InfoFactory.class);
        DialogUI dlg =infoFactory.createDeleteDialog(deletables, context);
        dlg.start();
        int result = dlg.getSelectedIndex();
        return result == 0;
    }

    private int showDialog(String action, PopupContext context, List<String> optionList, List<Icon> iconList, String title, String content, ImageIcon dialogIcon) throws RaplaException
    {
        DialogUI dialog = DialogUI.createInfoDialog(
                getContext()
                ,context
                ,true
                ,title
                ,content
                ,optionList.toArray(new String[] {})
        );
        dialog.setIcon(dialogIcon);
        for ( int i=0;i< optionList.size();i++)
        {
            dialog.getButton(i).setIcon(iconList.get( i));
        }
        
        dialog.start(context);
        int index = dialog.getSelectedIndex();
        return index;
    }
    
    protected Collection<EventCheck> getEventChecks()
    {
        Collection<EventCheck> eventCheckers = getContainer().lookupServicesFor(EventCheck.class);
        return eventCheckers;
    }
*/

    static public class DeleteBlocksCommand extends DeleteUndo<Reservation> {
        final Set<Reservation> reservationsToRemove;
        final Set<Appointment> appointmentsToRemove;
        final Map<Appointment, List<Date>> exceptionsToAdd;

        private Map<Appointment, Allocatable[]> allocatablesRemoved = new HashMap<>();
        private Map<Appointment, Reservation> parentReservations = new HashMap<>();

        public DeleteBlocksCommand(ClientFacade clientFacade, RaplaResources i18n, Set<Reservation> reservationsToRemove, Set<Appointment> appointmentsToRemove,
                                   Map<Appointment, List<Date>> exceptionsToAdd) throws RaplaException {
            super(clientFacade.getRaplaFacade(), i18n, reservationsToRemove, clientFacade.getUser());
            this.reservationsToRemove = reservationsToRemove;
            this.appointmentsToRemove = appointmentsToRemove;
            this.exceptionsToAdd = exceptionsToAdd;
        }

        public Promise<Void> execute() {
            HashSet<Reservation> toEdit = new LinkedHashSet<>();
            for (Appointment appointment : appointmentsToRemove) {
                Reservation reservation = appointment.getReservation();
                parentReservations.put(appointment, reservation);
                if (reservationsToRemove.contains(reservation)) {
                    continue;
                }
                toEdit.add(reservation);
            }
            for (Appointment appointment : exceptionsToAdd.keySet()) {
                Reservation reservation = appointment.getReservation();
                if (reservationsToRemove.contains(reservation)) {
                    continue;
                }
                toEdit.add(reservation);
            }
            final RaplaFacade facade = getFacade();
            final Promise<Map<ReferenceInfo<Reservation>, Reservation>> editablePromise = facade.editListAsync(toEdit).thenApply((mutableReservations) ->
            {
                HashMap<ReferenceInfo<Reservation>, Reservation> toUpdate = new LinkedHashMap<>();
                mutableReservations.values()
                        .forEach((mutableReservation) -> toUpdate.put(mutableReservation.getReference(), mutableReservation));
                return toUpdate;
            });
            Promise<Void> resultPromise = editablePromise.thenCompose((toUpdate) ->
            {
                allocatablesRemoved.clear();
                for (Appointment appointment : appointmentsToRemove) {
                    final Reservation reservation = appointment.getReservation();
                    if (reservationsToRemove.contains(reservation)) {
                        continue;
                    }
                    final ReferenceInfo<Reservation> eventId = reservation.getReference();
                    Reservation mutableReservation = toUpdate.get(eventId);
                    Allocatable[] restrictedAllocatables = mutableReservation.getRestrictedAllocatables(appointment);
                    mutableReservation.removeAppointment(appointment);
                    allocatablesRemoved.put(appointment, restrictedAllocatables);
                }
                for (Appointment appointment : exceptionsToAdd.keySet()) {
                    Reservation reservation = appointment.getReservation();
                    if (reservationsToRemove.contains(reservation)) {
                        continue;
                    }
                    final ReferenceInfo<Reservation> eventId = reservation.getReference();
                    Reservation mutableReservation = toUpdate.get(eventId);
                    Appointment found = mutableReservation.findAppointment(appointment);
                    if (found != null) {
                        Repeating repeating = found.getRepeating();
                        if (repeating != null) {
                            List<Date> list = exceptionsToAdd.get(appointment);
                            for (Date exception : list) {
                                repeating.addException(exception);
                            }
                        }
                    }
                }
                Collection<Reservation> updateArray = toUpdate.values();
                Collection<ReferenceInfo<Reservation>> removeArray = new ArrayList<>();
                for (Reservation reservation : reservationsToRemove) {
                    removeArray.add(reservation.getReference());
                }
                return facade.dispatch(updateArray, removeArray);
            });
            return resultPromise;
        }

        public Promise<Void> undo() {
            return super.undo().thenCompose((unusedParam) ->
            {
                Set<Reservation> toUpdateRequest = new HashSet();
                for (Appointment appointment : appointmentsToRemove) {
                    Reservation reservation = parentReservations.get(appointment);
                    if (reservationsToRemove.contains(reservation)) {
                        continue;
                    }
                    toUpdateRequest.add(reservation);
                }
                for (Appointment appointment : exceptionsToAdd.keySet()) {
                    Reservation reservation = appointment.getReservation();
                    toUpdateRequest.add( reservation);
                }
                final Promise<Map<ReferenceInfo<Reservation>, Reservation>> mapPromise = getFacade().editListAsyncForUndo(toUpdateRequest).thenApply(mutableReservations ->
                        mutableReservations.values().stream().collect(Collectors.toMap(Reservation::getReference, java.util.function.Function.identity())));
                return mapPromise.thenCompose( toUpdateMap-> {
                    for (Appointment appointment : appointmentsToRemove) {
                        final Reservation reservation = appointment.getReservation();
                        if (reservationsToRemove.contains(reservation)) {
                            continue;
                        }
                        Reservation mutableReservation = toUpdateMap.get(reservation.getReference());
                        mutableReservation.addAppointment(appointment);
                        Allocatable[] removedAllocatables = allocatablesRemoved.get(appointment);
                        mutableReservation.setRestrictionForAppointment(appointment, removedAllocatables);
                    }
                    for (Appointment appointment : exceptionsToAdd.keySet()) {
                        Reservation mutableReservation = toUpdateMap.get(appointment.getReservation().getReference());
                        Appointment found = mutableReservation.findAppointment(appointment);
                        if (found != null) {
                            Repeating repeating = found.getRepeating();
                            if (repeating != null) {
                                List<Date> list = exceptionsToAdd.get(appointment);
                                for (Date exception : list) {
                                    repeating.removeException(exception);
                                }
                            }
                        }
                    }
                    return facade.dispatch(toUpdateMap.values(), Collections.emptyList());
                });
            });
        }

        boolean cut;

        public boolean isCut() {
            return cut;
        }

        public void setCut(boolean cut) {
            this.cut = cut;
        }

        public String getCommandoName() {
            return getI18n().getString(isCut() ? "cut" : "delete") + " " + getI18n().getString("appointments");
        }
    }

    public Promise<Void> deleteAppointment(AppointmentBlock appointmentBlock, PopupContext context) {
        boolean includeEvent = true;
        return showDialog(appointmentBlock, "delete", includeEvent, context).thenCompose(dialogResult ->
                deleteAppointment(appointmentBlock, dialogResult, false));
    }

    private Promise<Void> deleteAppointment(AppointmentBlock appointmentBlock, final DialogAction dialogResult, final boolean isCut)  {
        Appointment appointment = appointmentBlock.getAppointment();
        final Date startDate = new Date(appointmentBlock.getStart());
        Set<Appointment> appointmentsToRemove = new LinkedHashSet<>();
        HashMap<Appointment, List<Date>> exceptionsToAdd = new LinkedHashMap<>();
        Set<Reservation> reservationsToRemove = new LinkedHashSet<>();
        switch (dialogResult) {
            case SINGLE:
                Repeating repeating = appointment.getRepeating();
                if (repeating != null) {
                    List<Date> exceptionList = Collections.singletonList(DateTools.cutDate(startDate));
                    if (isNotEmptyWithExceptions(appointment, exceptionList)) {
                        exceptionsToAdd.put(appointment, exceptionList);
                    } else {
                        appointmentsToRemove.add(appointment);
                    }
                } else {
                    appointmentsToRemove.add(appointment);
                }
                break;
            case EVENT:
                reservationsToRemove.add(appointment.getReservation());
                break;
            case SERIE:
                appointmentsToRemove.add(appointment);
                break;
            case CANCEL:
                return ResolvedPromise.VOID_PROMISE;
        }

        final DeleteBlocksCommand command;
        try {
            command = new DeleteBlocksCommand(getClientFacade(), i18n, reservationsToRemove, appointmentsToRemove, exceptionsToAdd) {
                public String getCommandoName() {
                    String name;
                    I18nBundle i18n = getI18n();
                    if (dialogResult == DialogAction.SINGLE)
                        name = i18n.format("single_appointment.format", startDate);
                    else if (dialogResult == DialogAction.EVENT)
                        name = i18n.getString("reservation");
                    else if (dialogResult == DialogAction.SERIE)
                        name = i18n.getString("serie");
                    else
                        name = i18n.getString("appointment");
                    return i18n.getString(isCut ? "cut" : "delete") + " " + name;
                }
            };
        } catch (RaplaException ex)
        {
            return new ResolvedPromise<>(ex);
        }
        CommandHistory commandHistory = getCommandHistory();
        return commandHistory.storeAndExecute(command);
    }

    private boolean isNotEmptyWithExceptions(Appointment appointment, List<Date> exceptions) {
        Repeating repeating = appointment.getRepeating();
        if (repeating != null) {

            int number = repeating.getNumber();
            if (number >= 1) {
                final int length = repeating.getExceptions().length + exceptions.size();
                if (length >= number - 1) {
                    Collection<AppointmentBlock> blocks = new ArrayList<>();
                    appointment.createBlocks(appointment.getStart(), appointment.getMaxEnd(), blocks);
                    int blockswithException = 0;
                    for (AppointmentBlock block : blocks) {
                        long start = block.getStart();
                        boolean blocked = false;
                        for (Date excepion : exceptions) {
                            if (DateTools.isSameDay(excepion.getTime(), start)) {
                                blocked = true;
                            }
                        }
                        if (blocked) {
                            blockswithException++;
                        }
                    }
                    if (blockswithException >= blocks.size()) {
                        return false;
                    }
                }

            }
        }
        return true;
    }

    enum DialogAction {
        EVENT,
        SERIE,
        SINGLE,
        CANCEL
    }

    private Promise<DialogAction> showDialog(AppointmentBlock appointmentBlock, String action, boolean includeEvent, PopupContext context)  {
        Appointment appointment = appointmentBlock.getAppointment();
        Date from = new Date(appointmentBlock.getStart());
        Reservation reservation = appointment.getReservation();
        getLogger().debug(action + " '" + appointment + "' for reservation '" + reservation + "'");
        List<String> optionList = new ArrayList<>();
        List<I18nIcon> iconList = new ArrayList<>();
        List<DialogAction> actionList = new ArrayList<>();
        String dateString = getRaplaLocale().formatDate(from);
        if (reservation.getAppointments().length <= 1 || includeEvent) {
            optionList.add(i18n.getString("reservation"));
            iconList.add(i18n.getIcon("icon.edit_window_small"));
            actionList.add(DialogAction.EVENT);
        }
        if (appointment.getRepeating() != null && reservation.getAppointments().length > 1) {
            String shortSummary = appointmentFormater.getShortSummary(appointment);
            optionList.add(i18n.getString("serie") + ": " + shortSummary);
            iconList.add(i18n.getIcon("icon.repeating"));
            actionList.add(DialogAction.SERIE);
        }
        // more then one block for the appointment exisst
        if ((appointment.getRepeating() != null && isNotEmptyWithExceptions(appointment, Collections.singletonList(from)))
                || reservation.getAppointments().length > 1) {
            optionList.add(i18n.format("single_appointment.format", dateString));
            iconList.add(i18n.getIcon("icon.single"));
            actionList.add(DialogAction.SINGLE);
        }
        if (optionList.size() > 1) {

            String title = i18n.getString(action);
            String content = i18n.getString(action + "_appointment.format");
            I18nIcon dialogIcon = i18n.getIcon("icon.question");
            return showDialog( context, optionList, iconList, title, content, dialogIcon).thenApply((index) -> index < 0 ? DialogAction.CANCEL : actionList.get(index));
        } else {
            if (action.equals("delete")) {
                return deleteDialog.showDeleteDialog(context, new Object[]{appointment.getReservation()}).thenApply(result -> {
                    if (!result) {
                        return DialogAction.CANCEL;
                    } else {
                        if (actionList.size() > 0) {
                            return actionList.get(0);
                        }
                        return DialogAction.EVENT;
                    }
                });
            }
            return new ResolvedPromise<>(actionList.size() > 0 ? actionList.get(0) : DialogAction.EVENT);
        }
    }

    @Override
    public Promise<Void> copyAppointmentBlock(AppointmentBlock appointmentBlock, PopupContext context, Collection<Allocatable> contextAllocatables)  {
        return copyCutAppointment(appointmentBlock, context, contextAllocatables, "copy", false);
    }

    @Override
    public Promise<Void> cutAppointment(AppointmentBlock appointmentBlock, PopupContext context, Collection<Allocatable> contextAllocatables) {
        return copyCutAppointment(appointmentBlock, context, contextAllocatables, "cut", false);
    }

    @Override
    public Promise<Void> copyReservations(Collection<Reservation> reservations, Collection<Allocatable> contextAllocatables)  {
        return cloneList(reservations).thenAccept(clones ->getClipboard().setReservation(clones, contextAllocatables));
    }

    private Promise<Collection<Reservation>> cloneList(Collection<Reservation> reservations) {
        final User user;
        try {
            user = getClientFacade().getUser();
        } catch (RaplaException e) {
            return new ResolvedPromise<>(e);
        }
        return getFacade().cloneList(reservations);
    }


    public Promise<Void> cutReservations(Collection<Reservation> reservations, Collection<Allocatable> contextAllocatables) {
        return cloneList(reservations).thenCompose( clones->
            {
                getClipboard().setReservation(clones, contextAllocatables);
                Set<Reservation> reservationsToRemove = new HashSet<>(reservations);
                Set<Appointment> appointmentsToRemove = Collections.emptySet();
                Map<Appointment, List<Date>> exceptionsToAdd = Collections.emptyMap();
                DeleteBlocksCommand command = new DeleteBlocksCommand(getClientFacade(), i18n, reservationsToRemove, appointmentsToRemove, exceptionsToAdd) {
                    public String getCommandoName() {
                        return getI18n().getString("cut");
                    }
                };
                CommandHistory commandHistory = getCommandHistory();
                return commandHistory.storeAndExecute(command);
            });
    }

    private Promise<Void> copyCutAppointment(AppointmentBlock appointmentBlock, PopupContext context, Collection<Allocatable> contextAllocatables, String action,
                                    boolean skipDialog)  {
        boolean deleteOriginal = action.equals("cut");
        RaplaClipboard raplaClipboard = getClipboard();
        Appointment appointment = appointmentBlock.getAppointment();


        Reservation sourceReservation = appointment.getReservation();

        // copyReservations info text to system clipboard
        {
            StringBuffer buf = new StringBuffer();
            ReservationInfoUI reservationInfoUI = new ReservationInfoUI(getI18n(), getRaplaLocale(), getFacade(), logger, appointmentFormater);
            boolean excludeAdditionalInfos = false;

            List<Row> attributes = reservationInfoUI.getAttributes(sourceReservation, null, null, excludeAdditionalInfos);
            for (Row row : attributes) {
                buf.append(row.getField());
            }
            String string = buf.toString();
            raplaClipboard.copyToSystemClipboard(string);

        }

        java.util.function.Function<DialogAction,Promise<Void>> copyCutAction = (dialogResult)-> {
            Allocatable[] restrictedAllocatables = sourceReservation.getRestrictedAllocatables(appointment);

            Promise<Void> ready;
            if (dialogResult == DialogAction.SINGLE) {
                ready = getFacade().cloneAsync(appointment).thenAccept( copy->
                {
                    copy.setRepeatingEnabled(false);
                    Date date = DateTools.cutDate(copy.getStart());
                    TimeWithoutTimezone time = DateTools.toTime(date.getTime());
                    Date newStart = new Date(date.getTime() + time.getMilliseconds());
                    copy.moveTo(newStart);
                    RaplaClipboard.CopyType copyType = deleteOriginal ? CopyType.CUT_BLOCK : CopyType.COPY_BLOCK;
                    raplaClipboard.setAppointment(copy, sourceReservation, copyType, restrictedAllocatables, contextAllocatables);
                });
            } else if (dialogResult == DialogAction.EVENT && appointment.getReservation().getAppointments().length > 1) {
                ready = getFacade().cloneAsync(appointment.getReservation()).thenAccept(( clone)->
                {
                    int num = getAppointmentIndex(appointment);
                    Appointment[] clonedAppointments = clone.getAppointments();
                    if (num >= clonedAppointments.length) {
                        // appointment may be deleted
                        return; //null;
                    }
                    Appointment clonedAppointment = clonedAppointments[num];
                    RaplaClipboard.CopyType copyType = deleteOriginal ? CopyType.CUT_RESERVATION : CopyType.COPY_RESERVATION;
                    raplaClipboard.setAppointment(clonedAppointment, clone, copyType, restrictedAllocatables, contextAllocatables);
                });
            } else {
                ready = getFacade().cloneAsync(appointment).thenAccept( copy->
                {
                    RaplaClipboard.CopyType copyType;
                    if (deleteOriginal) {
                        copyType = sourceReservation.getAppointments().length == 1 ? CopyType.CUT_RESERVATION : CopyType.CUT_BLOCK;
                    } else {
                        copyType = CopyType.COPY_BLOCK;
                    }
                    raplaClipboard.setAppointment(copy, sourceReservation, copyType, restrictedAllocatables, contextAllocatables);
                });
            }
            return ready.thenRun(()->
            {
                if (deleteOriginal) {
                    deleteAppointment(appointmentBlock, dialogResult, true);
                }
            });
        };
        if (skipDialog) {
            return copyCutAction.apply(DialogAction.EVENT);
        } else {
            return showDialog(appointmentBlock, action, true, context).thenCompose(copyCutAction::apply);
        }
        //return copyReservations;
    }

    public int getAppointmentIndex(Appointment appointment) {
        int num;
        Reservation reservation = appointment.getReservation();
        num = 0;
        for (Appointment app : reservation.getAppointments()) {

            if (appointment.equals(app)) {
                break;
            }
            num++;
        }
        return num;
    }

    private RaplaClipboard getClipboard() {
        return clipboard;
    }

    public boolean isAppointmentOnClipboard() {
        return (getClipboard().getAppointment() != null || !getClipboard().getReservations().isEmpty());
    }

    public Promise<Void> pasteAppointment(Date start, PopupContext popupContext, boolean asNewReservation, boolean keepTime) {
        RaplaClipboard clipboard = getClipboard();

        Collection<Reservation> reservations = clipboard.getReservations();
        Promise<CommandUndo<RaplaException>> pasteCommand;
        if (reservations.size() > 1) {
            pasteCommand = new ResolvedPromise<>(new ReservationPaste(reservations, start, keepTime, popupContext));
        } else {
            Appointment appointment = clipboard.getAppointment();
            if (appointment == null) {
                return ResolvedPromise.VOID_PROMISE;
            }

            final Reservation reservation = clipboard.getReservation();
            final boolean copyWholeReservation = clipboard.isWholeReservation();
            final Allocatable[] restrictedAllocatables = clipboard.getRestrictedAllocatables();
            final long offset = getOffset(appointment.getStart(), start, keepTime);
            final ResolvedPromise<CommandUndo<RaplaException>> defaultPastCommand = new ResolvedPromise<>(new AppointmentPaste(appointment, reservation, restrictedAllocatables, asNewReservation, copyWholeReservation, offset, popupContext));

            getLogger().debug("Paste appointment '" + appointment + "' for reservation '" + reservation + "' at " + start);

            Collection<Allocatable> currentlyMarked = calendarModel.getMarkedAllocatables();
            Collection<Allocatable> previouslyMarked = clipboard.getContextAllocatables();
            // exchange allocatables if pasted in a different allocatable slot
            if (copyWholeReservation && currentlyMarked != null && previouslyMarked != null && currentlyMarked.size() == 1 && previouslyMarked.size() == 1) {
                Allocatable newAllocatable = currentlyMarked.iterator().next();
                Allocatable oldAllocatable = previouslyMarked.iterator().next();
                if (!newAllocatable.equals(oldAllocatable)) {
                    if (!reservation.hasAllocated(newAllocatable)) {
                        pasteCommand = exchangeAllocatebleCmd(AppointmentBlock.create(appointment), oldAllocatable, newAllocatable, null, popupContext).thenCompose(cmd ->
                            cmd.getModifiedReservationForExecute().thenApply(reservation1 ->
                                    new AppointmentPaste(reservation1.getAppointments()[0], reservation1, restrictedAllocatables, asNewReservation, copyWholeReservation, offset, popupContext))
                        );
                    }
                    else
                    {
                        pasteCommand = defaultPastCommand;
                    }
                }
                else
                {
                    pasteCommand = defaultPastCommand;
                }
            }
            else
            {
                pasteCommand = defaultPastCommand;
            }
        }
        return  pasteCommand.thenCompose(command -> getCommandHistory().storeAndExecute(command));
    }

    public CommandHistory getCommandHistory() {
        return getClientFacade().getCommandHistory();
    }

    public Promise<Void> moveAppointment(AppointmentBlock appointmentBlock, Date newStart, PopupContext context, boolean keepTime)  {
        Date from = new Date(appointmentBlock.getStart());
        if (newStart.equals(from))
            return ResolvedPromise.VOID_PROMISE;
        getLogger().info("Moving appointment " + appointmentBlock.getAppointment() + " from " + from + " to " + newStart);
        return resizeAppointment(appointmentBlock, newStart, null, context, keepTime);
    }

    public Promise<Void> resizeAppointment(AppointmentBlock appointmentBlock, Date newStart, Date newEnd, final PopupContext context, boolean keepTime)
             {
        boolean includeEvent = newEnd == null;
        Appointment appointment = appointmentBlock.getAppointment();
        Date from = new Date(appointmentBlock.getStart());
        return showDialog(appointmentBlock, "move", includeEvent, context).thenCompose(result ->
        {
            if (result == DialogAction.CANCEL) {
                return ResolvedPromise.VOID_PROMISE;
            }

            Date oldStart = from;
            Date oldEnd = (newEnd == null) ? null : new Date(from.getTime() + appointment.getEnd().getTime() - appointment.getStart().getTime());
            Date newStart2;
            if (keepTime && newStart != null && !newStart.equals(oldStart)) {
                newStart2 = new Date(oldStart.getTime() + getOffset(oldStart, newStart, keepTime));
            } else {
                newStart2 = newStart;
            }
            AppointmentResize resizeCommand = new AppointmentResize(appointment, oldStart, oldEnd, newStart2, newEnd, context, result, keepTime);
            dialogUI.busy(i18n.getString("move"));
            return getCommandHistory().storeAndExecute(resizeCommand).finally_(()-> dialogUI.idle());
        });
    }

    protected Promise handleException(Promise promise, PopupContext context) {
        return promise.exceptionally(ex ->
        {
            if (!(ex instanceof CommandAbortedException)) {
                showException((Throwable) ex, context);
            }
        });
    }

    public long getOffset(Date appStart, Date newStart, boolean keepTime) {
        Date newStartAdjusted;
        if (!keepTime) {
            newStartAdjusted = newStart;
        } else {
            //TimeWithoutTimezone oldStartTime = DateTools.toTime( appStart.getTime());
            newStartAdjusted = DateTools.toDateTime(newStart, appStart);
        }
        long offset = newStartAdjusted.getTime() - appStart.getTime();
        return offset;
    }

    @Override
    public Promise<Void> exchangeAllocatable(final AppointmentBlock appointmentBlock, final Allocatable oldAllocatable, final Allocatable newAllocatable,
                                    final Date newStart, PopupContext context) {
        return exchangeAllocatebleCmd(appointmentBlock, oldAllocatable, newAllocatable, newStart, context).thenAccept(command ->
        {
            if (command != null) {
                CommandHistory commandHistory = getCommandHistory();
                final Promise promise = commandHistory.storeAndExecute(command);
                handleException(promise, context);
            }
        });
    }

    private Promise<AllocatableExchangeCommand> exchangeAllocatebleCmd(AppointmentBlock appointmentBlock, final Allocatable oldAllocatable,
                                                                       final Allocatable newAllocatable, final Date newStart, PopupContext context)  {
        Map<Allocatable, Appointment[]> newRestrictions = new HashMap<>();
        //Appointment appointment;
        //Allocatable oldAllocatable;
        //Allocatable newAllocatable;
        List<Date> exceptionsAdded = new ArrayList<>();
        Appointment appointment = appointmentBlock.getAppointment();
        Reservation reservation = appointment.getReservation();
        Date date = new Date(appointmentBlock.getStart());

        Appointment[] restriction = reservation.getRestriction(oldAllocatable);
        boolean includeEvent = restriction.length == 0;
        return getFacade().cloneAsync( appointment)
                .thenCompose( clonedAppointment-> showDialog(appointmentBlock, "exchange_allocatables", includeEvent, context)
                        .thenApply(dialogResult ->
         {
            Appointment addAppointment = null;
            boolean removeAllocatable = false;
            boolean addAllocatable = false;
            Appointment copy = null;
            if (dialogResult == DialogAction.CANCEL)
                return null;

            if (dialogResult == DialogAction.SINGLE && appointment.getRepeating() != null) {
                copy = clonedAppointment;
                copy.setRepeatingEnabled(false);
                Date dateTime = DateTools.toDateTime(date, appointment.getStart());
                copy.moveTo(dateTime);
            }

            if (dialogResult == DialogAction.EVENT && includeEvent) {
                removeAllocatable = true;
                //modifiableReservation.removeAllocatable( oldAllocatable);
                if (reservation.hasAllocated(newAllocatable)) {
                    newRestrictions.put(newAllocatable, Appointment.EMPTY_ARRAY);
                    //modifiableReservation.setRestriction( newAllocatable, Appointment.EMPTY_ARRAY);
                } else {

                    addAllocatable = true;
                    //modifiableReservation.addAllocatable(newAllocatable);
                }
            } else {
                Appointment[] apps = reservation.getAppointmentsFor(oldAllocatable);
                if (copy != null) {
                    exceptionsAdded.add(date);
                    //Appointment existingAppointment = modifiableReservation.findAppointment( appointment);
                    //existingAppointment.getRepeating().addException( date );
                    //modifiableReservation.addAppointment( copyReservations);
                    addAppointment = copy;

                    List<Allocatable> all = reservation.getAllocatablesFor(appointment).collect(Collectors.toList());
                    all.remove(oldAllocatable);
                    for (Allocatable a : all) {
                        Appointment[] restr = reservation.getRestriction(a);
                        if (restr.length > 0) {
                            List<Appointment> restrictions = new ArrayList<>(Arrays.asList(restr));
                            restrictions.add(copy);
                            newRestrictions.put(a, restrictions.toArray(Appointment.EMPTY_ARRAY));
                            //reservation.setRestriction(a, newRestrictions.toArray(new Appointment[] {}));
                        }
                    }

                    newRestrictions.put(oldAllocatable, apps);
                    //modifiableReservation.setRestriction(oldAllocatable,apps);
                } else {
                    if (apps.length == 1) {
                        //modifiableReservation.removeAllocatable(oldAllocatable);
                        removeAllocatable = true;
                    } else {
                        List<Appointment> appointments = new ArrayList<>(Arrays.asList(apps));
                        appointments.remove(appointment);
                        newRestrictions.put(oldAllocatable, appointments.toArray(Appointment.EMPTY_ARRAY));
                        //modifiableReservation.setRestriction(oldAllocatable, appointments.toArray(Appointment.EMPTY_ARRAY));
                    }
                }

                Appointment app;
                if (copy != null) {
                    app = copy;
                } else {
                    app = appointment;
                }

                if (reservation.hasAllocated(newAllocatable)) {
                    Appointment[] existingRestrictions = reservation.getRestriction(newAllocatable);
                    Collection<Appointment> restrictions = new LinkedHashSet<>(Arrays.asList(existingRestrictions));
                    if (existingRestrictions.length == 0 || restrictions.contains(app)) {
                        // is already allocated, do nothing
                    } else {
                        restrictions.add(app);
                    }
                    newRestrictions.put(newAllocatable, restrictions.toArray(Appointment.EMPTY_ARRAY));
                    //modifiableReservation.setRestriction(newAllocatable, newRestrictions.toArray(Appointment.EMPTY_ARRAY));
                } else {
                    addAllocatable = true;
                    //modifiableReservation.addAllocatable( newAllocatable);
                    if (reservation.getAppointments().length > 1 || addAppointment != null) {
                        newRestrictions.put(newAllocatable, new Appointment[]{app});
                        //modifiableReservation.setRestriction(newAllocatable, new Appointment[] {appointment});
                    }
                }
            }
            Date newStart2;
            if (newStart != null) {
                long offset = newStart.getTime() - appointmentBlock.getStart();
                Appointment app = addAppointment != null ? addAppointment : appointment;
                newStart2 = new Date(app.getStart().getTime() + offset);
            } else {
                newStart2 = null;
            }
            AllocatableExchangeCommand command = new AllocatableExchangeCommand(appointment, oldAllocatable, newAllocatable, newStart2, newRestrictions,
                    removeAllocatable, addAllocatable, addAppointment, exceptionsAdded, context);
            return command;
        }));
    }

    class AllocatableExchangeCommand implements CommandUndo<RaplaException> {
        Appointment appointment;
        Allocatable oldAllocatable;
        Allocatable newAllocatable;
        Map<Allocatable, Appointment[]> newRestrictions;
        Map<Allocatable, Appointment[]> oldRestrictions;

        boolean removeAllocatable;
        boolean addAllocatable;
        Appointment addAppointment;
        List<Date> exceptionsAdded;
        Date newStart;
        boolean firstTimeCall = true;
        PopupContext sourceComponent;

        AllocatableExchangeCommand(Appointment appointment, Allocatable oldAllocatable, Allocatable newAllocatable, Date newStart,
                                   Map<Allocatable, Appointment[]> newRestrictions, boolean removeAllocatable, boolean addAllocatable, Appointment addAppointment,
                                   List<Date> exceptionsAdded, PopupContext sourceComponent) {
            this.appointment = appointment;
            this.oldAllocatable = oldAllocatable;
            this.newAllocatable = newAllocatable;
            this.newStart = newStart;
            this.newRestrictions = newRestrictions;
            this.removeAllocatable = removeAllocatable;
            this.addAllocatable = addAllocatable;
            this.addAppointment = addAppointment;
            this.exceptionsAdded = exceptionsAdded;
            this.sourceComponent = sourceComponent;
        }

        public Promise<Void> execute() {
            return getModifiedReservationForExecute().thenCompose(mutableReservation->
                    checkAndDispatch(Collections.singleton(mutableReservation), Collections.emptyList(), firstTimeCall, sourceComponent)).thenRun(()->firstTimeCall = false);
        }

        public Promise<Void> undo() {
            return getModifiedReservationForUndo().thenCompose(mutableReservation->
                            checkAndDispatch(Collections.singleton(mutableReservation), Collections.emptyList(), false, sourceComponent));
        }

        protected Promise<Reservation> getModifiedReservationForExecute()  {
            Reservation reservation = appointment.getReservation();
            return getFacade().editAsync(reservation).thenApply(modifiableReservation ->
            {
                if (addAppointment != null) {
                    modifiableReservation.addAppointment(addAppointment);
                }
                Appointment existingAppointment = modifiableReservation.findAppointment(appointment);
                if (existingAppointment != null) {
                    for (Date exception : exceptionsAdded) {
                        existingAppointment.getRepeating().addException(exception);
                    }
                }
                if (removeAllocatable) {
                    modifiableReservation.removeAllocatable(oldAllocatable);
                }
                if (addAllocatable) {
                    modifiableReservation.addAllocatable(newAllocatable);
                }
                oldRestrictions = new HashMap<>();
                for (Allocatable alloc : reservation.getAllocatables()) {
                    oldRestrictions.put(alloc, reservation.getRestriction(alloc));
                }
                for (Allocatable alloc : newRestrictions.keySet()) {
                    Appointment[] restrictions = newRestrictions.get(alloc);
                    ArrayList<Appointment> foundAppointments = new ArrayList<>();
                    for (Appointment app : restrictions) {
                        Appointment found = modifiableReservation.findAppointment(app);
                        if (found != null) {
                            foundAppointments.add(found);
                        }
                    }
                    modifiableReservation.setRestriction(alloc, foundAppointments.toArray(Appointment.EMPTY_ARRAY));
                }
                if (newStart != null) {
                    if (addAppointment != null) {
                        addAppointment.moveTo(newStart);
                    } else if (existingAppointment != null) {
                        existingAppointment.moveTo(newStart);
                    }
                }
                //            long startTime = (dialogResult == DialogAction.SINGLE) ? sourceStart.getTime() : ap.getStart().getTime();
                //
                //            changeStart = new Date(startTime + offset);
                //
                //            if (resizing) {
                //                changeEnd = new Date(changeStart.getTime() + (destEnd.getTime() - destStart.getTime()));
                //                ap.move(changeStart, changeEnd);
                //            } else {
                //                ap.move(changeStart);
                //            }
                return modifiableReservation;
            });
        }

        protected Promise<Reservation> getModifiedReservationForUndo() {
            return getFacade().editAsync(appointment.getReservation()).thenApply((modifiableReservation )->
            {
                if (addAppointment != null) {
                    Appointment found = modifiableReservation.findAppointment(addAppointment);
                    if (found != null) {
                        modifiableReservation.removeAppointment(found);
                    }
                }

                Appointment existingAppointment = modifiableReservation.findAppointment(appointment);
                if (existingAppointment != null) {
                    for (Date exception : exceptionsAdded) {
                        existingAppointment.getRepeating().removeException(exception);
                    }
                    if (newStart != null) {
                        Date oldStart = appointment.getStart();
                        existingAppointment.moveTo(oldStart);
                    }
                }
                if (removeAllocatable) {
                    modifiableReservation.addAllocatable(oldAllocatable);
                }
                if (addAllocatable) {
                    modifiableReservation.removeAllocatable(newAllocatable);
                }

                for (Allocatable alloc : oldRestrictions.keySet()) {
                    Appointment[] restrictions = oldRestrictions.get(alloc);
                    ArrayList<Appointment> foundAppointments = new ArrayList<>();
                    for (Appointment app : restrictions) {
                        Appointment found = modifiableReservation.findAppointment(app);
                        if (found != null) {
                            foundAppointments.add(found);
                        }
                    }
                    modifiableReservation.setRestriction(alloc, foundAppointments.toArray(Appointment.EMPTY_ARRAY));
                }
                return modifiableReservation;
            });
        }

        public String getCommandoName() {
            return getI18n().getString("exchange_allocatables");
        }
    }

    /**
     * This class collects any information of an appointment that is resized or moved in any way
     * in the calendar view.
     * This is where undo/redo for moving or resizing of an appointment
     * in the calendar view is realized.
     *
     * @author Jens Fritz
     */

    //Erstellt und bearbeitet von Dominik Krickl-Vorreiter und Jens Fritz
    class AppointmentResize implements CommandUndo<RaplaException> {

        private final Date oldStart;
        private final Date oldEnd;
        private final Date newStart;
        private final Date newEnd;

        private final Appointment appointment;
        private final PopupContext sourceComponent;
        private final DialogAction dialogResult;

        private Appointment lastCopy;
        private boolean firstTimeCall = true;
        private boolean keepTime;

        public AppointmentResize(Appointment appointment, Date oldStart, Date oldEnd, Date newStart, Date newEnd, PopupContext sourceComponent,
                                 DialogAction dialogResult, boolean keepTime) {
            this.oldStart = oldStart;
            this.oldEnd = oldEnd;
            this.newStart = newStart;
            this.newEnd = newEnd;
            this.appointment = appointment;
            this.sourceComponent = sourceComponent;
            this.dialogResult = dialogResult;
            this.keepTime = keepTime;
            lastCopy = null;
        }

        public Promise<Void> execute() {
            boolean resizing = newEnd != null;
            Date sourceStart = oldStart;
            Date destStart = newStart;
            Date destEnd = newEnd;
            return doMove(resizing, sourceStart, destStart, destEnd, false);
        }

        public Promise<Void> undo() {
            boolean resizing = newEnd != null;

            Date sourceStart = newStart;
            Date destStart = oldStart;
            Date destEnd = oldEnd;

            return doMove(resizing, sourceStart, destStart, destEnd, true);
        }

        private Promise<Void> doMove(boolean resizing, Date sourceStart, Date destStart, Date destEnd, boolean undo) {
            Reservation reservation = appointment.getReservation();
            final Promise<Map<Reservation,Reservation>> reservationPromise;
            if ( undo)
            {
                reservationPromise = getFacade().editListAsyncForUndo(Collections.singletonList(reservation));
            }
            else
            {
                reservationPromise = getFacade().editListAsync(Collections.singletonList(reservation));
            }
            return reservationPromise.thenApply(ReservationControllerImpl::getFirstValue)
                    .thenCompose((mutableReservation) -> change(resizing, sourceStart, destStart, destEnd, undo, mutableReservation, findAppointment(reservation, mutableReservation)))
                    .thenCompose((modifiedReservation) -> checkAndDispatch(Collections.singleton(modifiedReservation), Collections.emptyList(), firstTimeCall, sourceComponent))
                    .finally_( ()->firstTimeCall = false);
        }

        @NotNull
        private Appointment findAppointment(Reservation reservation, Reservation mutableReservation) throws RaplaException {
            Appointment mutableAppointment = mutableReservation.findAppointment(appointment);
            if (mutableAppointment == null) {
                throw new IllegalStateException("Can't find the appointment: " + appointment);
            }
            if (firstTimeCall) {
                if (!reservation.getLastChanged().equals(mutableReservation.getLastChanged())) {
                    getFacade().refreshAsync();
                    throw new RaplaException(getI18n().format("error.new_version", reservation.toString()));
                }
            }
            return mutableAppointment;
        }

        private Promise<Reservation> change(boolean resizing, Date sourceStart, Date destStart, Date destEnd, boolean undo, Reservation mutableReservation, Appointment mutableAppointment) {
            final Promise<Appointment> clonedAppointment = getFacade().cloneAsync(mutableAppointment);
            return clonedAppointment.thenApply(appointmentClone ->
            {
                long offset = getOffset(sourceStart, destStart, keepTime);
                Collection<Appointment> appointments;
                // Move the complete serie
                switch (dialogResult) {
                    case SERIE:
                        // Wir wollen eine Serie (Appointment mit Wdh) verschieben
                        appointments = Collections.singleton(mutableAppointment);
                        break;
                    case EVENT:
                        // Wir wollen die ganze Reservation verschieben
                        appointments = Arrays.asList(mutableReservation.getAppointments());
                        break;
                    case SINGLE:
                        // Wir wollen nur ein Appointment aus einer Serie verschieben --> losl_sen von Serie

                        Repeating repeating = mutableAppointment.getRepeating();
                        if (repeating == null) {
                            appointments = Arrays.asList(mutableAppointment);
                        } else {
                            if (undo) {
                                mutableReservation.removeAppointment(lastCopy);
                                repeating.removeException(oldStart);
                                lastCopy = null;
                                appointments = Collections.emptyList();
                            } else {
                                lastCopy = appointmentClone;
                                lastCopy.setRepeatingEnabled(false);
                                appointments = Arrays.asList(lastCopy);
                            }
                        }

                        break;
                    default:
                        throw new IllegalStateException("Dialog choice not supported " + dialogResult);
                }

                Date changeStart;
                Date changeEnd;

                for (Appointment ap : appointments) {
                    long startTime = (dialogResult == DialogAction.SINGLE) ? sourceStart.getTime() : ap.getStart().getTime();

                    changeStart = new Date(startTime + offset);

                    if (resizing) {
                        changeEnd = new Date(changeStart.getTime() + (destEnd.getTime() - destStart.getTime()));
                        ap.move(changeStart, changeEnd);
                    } else {
                        ap.moveTo(changeStart);
                    }
                }

                if (!undo) {
                    if (dialogResult == DialogAction.SINGLE) {
                        Repeating repeating = mutableAppointment.getRepeating();

                        if (repeating != null) {
                            Allocatable[] restrictedAllocatables = mutableReservation.getRestrictedAllocatables(mutableAppointment);
                            mutableReservation.addAppointment(lastCopy);
                            mutableReservation.setRestrictionForAppointment(lastCopy, restrictedAllocatables);
                            repeating.addException(oldStart);
                        }
                    }
                }
                return mutableReservation;
            });
        }

        public String getCommandoName() {
            return getI18n().getString("move");
        }
    }

    private static <T> T getFirstValue(Collection<T> values) {
        return values.iterator().next();
    }
    private static <T> T getFirstValue(Map<?,T> map) {
        return map.values().iterator().next();
    }

    Promise<Boolean> checkEvents(Collection<? extends Entity> entities, PopupContext sourceComponent) {
        return checkEvents(eventCheckers, entities, sourceComponent);
    }

    public static Promise<Boolean> checkEvents(Provider<Set<EventCheck>> checkers, Collection<? extends Entity> entities, PopupContext sourceComponent) {
        List<Reservation> reservations = new ArrayList<>();
        for (Entity entity : entities) {
            if (entity.getTypeClass() == Reservation.class) {
                reservations.add((Reservation) entity);
            }
        }
        final Set<EventCheck> set = checkers.get();
        Promise<Boolean> check = new ResolvedPromise<>(true);
        for (EventCheck eventCheck : set) {
            check = check.thenCompose((result) ->
            {
                final Promise<Boolean> promise = result ? eventCheck.check(reservations, sourceComponent) : new ResolvedPromise<>(false);
                return promise;
            });
        }
        return check;
    }

    /**
     * This class collects any information of an appointment that is copied and pasted
     * in the calendar view.
     * This is where undo/redo for pasting an appointment
     * in the calendar view is realized.
     *
     * @author Jens Fritz
     */
    class AppointmentPaste implements CommandUndo<RaplaException> {

        private final Appointment fromAppointment;
        private final Reservation fromReservation;
        private final Allocatable[] restrictedAllocatables;
        private final boolean asNewReservation;
        private final boolean copyWholeReservation;
        private final long offset;
        private final PopupContext sourceComponent;

        private Reservation saveReservation = null;
        private Appointment saveAppointment = null;
        private boolean firstTimeCall = true;

        public AppointmentPaste(Appointment fromAppointment, Reservation fromReservation, Allocatable[] restrictedAllocatables, boolean asNewReservation,
                                boolean copyWholeReservation, long offset, PopupContext sourceComponent) {
            this.fromAppointment = fromAppointment;
            this.fromReservation = fromReservation;
            this.restrictedAllocatables = restrictedAllocatables;
            this.asNewReservation = asNewReservation;
            this.copyWholeReservation = copyWholeReservation;
            this.offset = offset;
            this.sourceComponent = sourceComponent;
            assert !(!asNewReservation && copyWholeReservation);
        }

        public Promise<Void> execute() {
            return getMutableReservationForExecute().thenCompose((mutableReservation)->
            {
                if (!copyWholeReservation) {
                    final boolean moveTo = saveAppointment == null;
                    return getFacade().cloneAsync( moveTo ?  fromAppointment: saveAppointment).thenApply(newAppointment
                            ->
                    {
                        saveAppointment = newAppointment;
                        if (moveTo) {
                            final Date newStart = new Date(saveAppointment.getStart().getTime() + offset);
                            saveAppointment.moveTo(newStart);
                        }
                        mutableReservation.addAppointment(saveAppointment);
                        mutableReservation.setRestrictionForAppointment(saveAppointment, restrictedAllocatables);
                        saveReservation = mutableReservation;
                        return mutableReservation;
                    });
                }
                else
                {
                    saveReservation = mutableReservation;
                    return new ResolvedPromise<>(mutableReservation);
                }
            }).thenCompose((mutableReservation)->
                    checkAndDispatch(Collections.singleton(mutableReservation), Collections.emptyList(), firstTimeCall, sourceComponent)
            ).thenRun(()->firstTimeCall = false);
        }

        protected Promise<Reservation> getMutableReservationForExecute()  {
            if (asNewReservation) {
                final Reservation reservation = saveReservation != null ? saveReservation : fromReservation;
                return cloneList(Collections.singletonList(reservation)).thenApply(ReservationControllerImpl::getFirstValue).thenApply(mutableReservation->
                {
                    // Alle anderen Appointments verschieben / entfernen
                    Appointment[] appointments = mutableReservation.getAppointments();
                    for (int i = 0; i < appointments.length; i++) {
                        Appointment app = appointments[i];
                        if (copyWholeReservation) {
                            if (saveReservation == null) {
                                app.moveTo(new Date(app.getStart().getTime() + offset));
                            }
                        } else {
                            mutableReservation.removeAppointment(app);
                        }
                    }
                    return mutableReservation;
                });
            } else {
                return getFacade().editAsync(fromReservation);
            }
        }

        public Promise<Void> undo() {
            if (asNewReservation) {
                Collection<ReferenceInfo<Reservation>> removeList = Collections.singleton(saveReservation.getReference());
                Collection<Reservation> storeList = Collections.emptyList();
                return checkAndDispatch(storeList, removeList, false, sourceComponent);
            } else {
                return getFacade().editAsync(saveReservation).thenCompose((mutableReservation)->
                {
                    mutableReservation.removeAppointment(saveAppointment);
                    return checkAndDispatch(Collections.singleton(mutableReservation), Collections.emptyList(), false, sourceComponent);
                });
            }
        }

        public String getCommandoName() {
            return getI18n().getString("paste");
        }

    }
    @Override
    public Promise<Void> saveReservations(Map<Reservation,Reservation> storeList, PopupContext context) {
        SaveUndo<Reservation> cmd = new SaveUndo<>(facade.getRaplaFacade(), i18n, storeList);
        return checkEvents(storeList.values(),context).thenCompose((result)-> {
                    if (result)
                    {
                        return facade.getCommandHistory().storeAndExecute(cmd);
                    }
                    return new ResolvedPromise<>(new CommandAbortedException(cmd.commandoName));
                }
            );
    }

    @Override
    public Promise<Void> saveReservation(Reservation origReservation,Reservation newReservation) {
        LinkedHashMap<Reservation, Reservation> storeList = new LinkedHashMap<>();
        Reservation key = origReservation != null ? origReservation:  newReservation;
        storeList.put(key, newReservation);
        SaveUndo<Reservation> cmd = new SaveUndo<>(facade.getRaplaFacade(), i18n, storeList);
        PopupContext popupContext = null;
        final Promise<Boolean> booleanPromise = checkEvents(storeList.values(), popupContext);
        return booleanPromise.thenCompose(result-> {
            if (result)
            {
                return facade.getCommandHistory().storeAndExecute(cmd);
            }
            else
            {
                return new ResolvedPromise<>(new CommandAbortedException("Command Aborted"));
            }
           }
        );
    }

    public Promise<Void> checkAndDispatch(Collection<Reservation> storeList, Collection<ReferenceInfo<Reservation>> removeList, boolean firstTime,
                                           PopupContext sourceComponent) {
        final RaplaFacade facade = getFacade();
        Promise<Boolean> promise;
        if (firstTime) {
            promise = checkEvents(storeList, sourceComponent);
        } else {
            promise = new ResolvedPromise<>(true);
        }
        Promise<Void> result = promise.thenCompose((checkResult) ->
        {
            if (checkResult) {
                return facade.dispatch(storeList, removeList);
            } else {
                return new ResolvedPromise<>(new CommandAbortedException("Command Aborted"));
            }
        });
        return result;
    }

    class ReservationPaste implements CommandUndo<RaplaException> {

        private final Collection<Reservation> fromReservations;
        Date start;
        boolean keepTime;
        Collection<Reservation> clones;
        boolean firstTimeCall = true;
        private final PopupContext popupContext;

        public ReservationPaste(Collection<Reservation> fromReservation, Date start, boolean keepTime, PopupContext popupContext) {
            this.fromReservations = fromReservation;
            this.start = start;
            this.keepTime = keepTime;
            this.popupContext = popupContext;
        }

        public Promise<Void> execute() {
            User user;
            try {
                 user = getClientFacade().getUser();
            } catch (RaplaException ex) {
                return new ResolvedPromise<>(ex);
            }
            return getFacade().copyReservations(fromReservations, start, keepTime, user).thenCompose(clones-> {
                        this.clones = clones;
                        return checkAndDispatch(clones, Collections.emptyList(), firstTimeCall, popupContext);
                    }
            ).thenRun(()->firstTimeCall = false);
        }

        public Promise<Void> undo() {
            Collection<ReferenceInfo<Reservation>> removeList = new ArrayList<>();
            for (Reservation clone : clones) {
                removeList.add(clone.getReference());
            }
            Promise result = getFacade().dispatch(Collections.emptyList(), removeList);
            return result;
        }

        public String getCommandoName() {
            return getI18n().getString("paste");
        }

    }

}



