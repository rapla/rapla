package org.rapla.client.internal.edit;

import io.reactivex.functions.Consumer;
import org.jetbrains.annotations.Nullable;
import org.rapla.RaplaResources;
import org.rapla.client.EditApplicationEventContext;
import org.rapla.client.PopupContext;
import org.rapla.client.RaplaWidget;
import org.rapla.client.ReservationController;
import org.rapla.client.ReservationEdit;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.event.ApplicationEvent.ApplicationEventContext;
import org.rapla.client.event.ApplicationEventBus;
import org.rapla.client.event.TaskPresenter;
import org.rapla.client.extensionpoints.MergeCheckExtension;
import org.rapla.client.internal.CommandAbortedException;
import org.rapla.client.internal.SaveUndo;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.inject.Extension;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Observable;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;
import org.rapla.scheduler.Subject;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Extension(id = EditTaskPresenter.EDIT_EVENTS_ID, provides = TaskPresenter.class)
@Extension(id = EditTaskPresenter.EDIT_RESOURCES_ID, provides = TaskPresenter.class)
@Extension(id = EditTaskPresenter.CREATE_RESERVATION_FOR_DYNAMIC_TYPE, provides = TaskPresenter.class)
@Extension(id = EditTaskPresenter.CREATE_RESERVATION_FROM_TEMPLATE, provides = TaskPresenter.class)
@Extension(id = EditTaskPresenter.MERGE_RESOURCES_ID, provides = TaskPresenter.class)
public class EditTaskPresenter implements TaskPresenter
{
    public static final String CREATE_RESERVATION_FROM_TEMPLATE = "reservationFromTemplate";
    private final DialogUiFactoryInterface dialogUiFactory;
    private final RaplaFacade raplaFacade;
    private final RaplaResources i18n;
    private final ClientFacade clientFacade;
    private final ApplicationEventBus eventBus;
    private final CalendarSelectionModel model;

    public static final String CREATE_RESERVATION_FOR_DYNAMIC_TYPE = "createReservationFromDynamicType";
    final static public String EDIT_EVENTS_ID = "editEvents";
    final static public String MERGE_RESOURCES_ID = "mergeResources";
    final static public String EDIT_RESOURCES_ID = "editResources";
    private final Provider<ReservationEdit> reservationEditProvider;
    private final EditTaskViewFactory editTaskViewFactory;
    AppointmentBlock appointmentBlock= null;
    final ReservationController reservationController;
    private final Set<MergeCheckExtension> mergeCheckers;
    Subject<String> busyIdleObservable;
    EditTaskView editTaskView;

    public interface EditTaskView<T extends Entity,C> extends  RaplaWidget<C>
    {
        void start(Consumer<Collection<T>> save, Runnable close, Runnable deleteCmd);

        Map<T,T> getEditMap();

        boolean hasChanged();
    }

    @Inject
    public EditTaskPresenter(ClientFacade clientFacade, EditTaskViewFactory editTaskViewFactory, DialogUiFactoryInterface dialogUiFactory, RaplaResources i18n, ApplicationEventBus eventBus, CalendarSelectionModel model, Provider<ReservationEdit> reservationEditProvider,
                             ReservationController reservationController, Set<MergeCheckExtension> mergeCheckers, CommandScheduler scheduler)
    {
        this.editTaskViewFactory = editTaskViewFactory;
        this.dialogUiFactory = dialogUiFactory;
        this.i18n = i18n;
        this.clientFacade = clientFacade;
        this.eventBus = eventBus;
        this.model = model;
        this.reservationEditProvider = reservationEditProvider;
        this.raplaFacade = clientFacade.getRaplaFacade();
        this.reservationController = reservationController;
        this.mergeCheckers = mergeCheckers;
        this.busyIdleObservable = scheduler.createPublisher();
    }

    @Override
    public Observable<String> getBusyIdleObservable() {
        return busyIdleObservable;
    }

    @Override
    public Promise<RaplaWidget> startActivity(ApplicationEvent applicationEvent)
    {
        appointmentBlock = null;
        String taskId = applicationEvent.getApplicationEventId();
        String info = applicationEvent.getInfo();
        PopupContext popupContext = applicationEvent.getPopupContext();
        final Promise<EditTaskView> editTaskView;
        try
        {
            final boolean isMerge = taskId.equals(MERGE_RESOURCES_ID);
            if (taskId.equals(EDIT_RESOURCES_ID) || taskId.equals(EDIT_EVENTS_ID) || isMerge)
            {
                final ApplicationEventContext context = applicationEvent.getContext();
                Collection<Entity> entities;
                if (context != null && context instanceof EditApplicationEventContext)
                {
                    final EditApplicationEventContext editApplicationEventContext = (EditApplicationEventContext) context;
                    final List<Entity> selectedObjects = editApplicationEventContext.getSelectedObjects();
                    entities=new LinkedHashSet<>(selectedObjects);
                    appointmentBlock = editApplicationEventContext.getAppointmentBlock();
                    if (appointmentBlock != null)
                    {
                        final Reservation reservation = appointmentBlock.getAppointment().getReservation();
                        entities.add( reservation);
                    }
                }
                else
                {
                    entities = new LinkedHashSet<>();
                    Class<? extends Entity> clazz = taskId.equals(EDIT_EVENTS_ID) ?  Reservation.class: Allocatable.class;
                    String[] ids =  info.split(",");
                    for (String id : ids)
                    {
                        Entity<?> resolve;
                        try
                        {
                            resolve = raplaFacade.resolve(new ReferenceInfo(id, clazz));
                        }
                        catch (EntityNotFoundException e)
                        {
                            return new ResolvedPromise<>(e);
                        }
                        entities.add(resolve);
                    }
                    if ( clazz.equals( Allocatable.class) && isMerge)
                    {
                        for (MergeCheckExtension mergeCheckExtension : mergeCheckers)
                        {
                            final Collection allocatableCollection =  entities;
                            mergeCheckExtension.precheckAllocatableSelection(allocatableCollection);
                        }
                    }
                }

                editTaskView = createEditDialog(entities,  popupContext, applicationEvent);
            }
            else if (CREATE_RESERVATION_FOR_DYNAMIC_TYPE.equals(taskId))
            {
                final String dynamicTypeId = info;
                final Entity resolve = raplaFacade.resolve(new ReferenceInfo<>(dynamicTypeId, DynamicType.class));
                final DynamicType type = (DynamicType) resolve;
                final User user = clientFacade.getUser();
                final Promise<List<Reservation>> newReservations = RaplaComponent.newReservation(type, user, raplaFacade, model)
                        .thenApply(r->RaplaComponent.addAllocatables(model, Collections.singletonList(r), user));
                editTaskView = newReservations.thenCompose( list->createEditDialog(list, popupContext, applicationEvent));

            }
            else if (CREATE_RESERVATION_FROM_TEMPLATE.equals(taskId))
            {

                final String templateId = info;
                User user = clientFacade.getUser();
                Allocatable template = findTemplate(templateId);
                final Promise<Collection<Reservation>> templatePromise = raplaFacade.getTemplateReservations(template);
                Promise<EditTaskView> widgetPromise = templatePromise.thenCompose((reservations) ->
                {
                    if (reservations.size() == 0)
                    {
                        throw new EntityNotFoundException("Template " + template + " is empty. Please createInfoDialog events in template first.");
                    }
                    Boolean keepOrig = (Boolean) template.getClassification().getValue("fixedtimeandduration");
                    Collection<TimeInterval> markedIntervals = model.getMarkedIntervals();
                    boolean markedIntervalTimeEnabled = model.isMarkedIntervalTimeEnabled();
                    boolean keepTime = !markedIntervalTimeEnabled || (keepOrig == null || keepOrig);
                    Date beginn = RaplaComponent.getStartDate(model, raplaFacade, user);
                    return raplaFacade.copyReservations(reservations, beginn, keepTime, user).thenCompose((newReservations)-> {
                        if (markedIntervals.size() > 0 && reservations.size() == 1 && reservations.iterator().next().getAppointments().length == 1
                                && keepOrig == Boolean.FALSE)
                        {
                            Appointment app = newReservations.iterator().next().getAppointments()[0];
                            TimeInterval first = markedIntervals.iterator().next();
                            Date end = first.getEnd();
                            if (!markedIntervalTimeEnabled)
                            {
                                end = DateTools.toDateTime(end, app.getEnd());
                            }
                            if (!beginn.before(end))
                            {
                                end = new Date(app.getStart().getTime() + DateTools.MILLISECONDS_PER_HOUR);
                            }
                            app.move(app.getStart(), end);
                        }
                        List<Reservation> list = RaplaComponent.addAllocatables(model, newReservations, user);
                        return createEditDialog(list, popupContext, applicationEvent);
                    });
                });
                editTaskView = widgetPromise;
            }
            else
            {
                return new ResolvedPromise<>(new RaplaException("Unknow taskId" + taskId));
            }
        }
        catch (RaplaException e)
        {
            return new ResolvedPromise<>(e);
        }
        return editTaskView.thenApply(( view) -> this.editTaskView = view);
    }

    private Allocatable findTemplate(String templateId) throws RaplaException
    {
        final Collection<Allocatable> templates = raplaFacade.getTemplates();
        for (Allocatable allocatable : templates)
        {
            if (allocatable.getId().equals(templateId))
            {
                return allocatable;
            }
        }
        return null;
    }

    public String getTitle(ApplicationEvent event)
    {
        final String applicationEventId = event.getApplicationEventId();
        event.getContext();
        if (applicationEventId.equals(MERGE_RESOURCES_ID))
        {
            return i18n.getString("merge");
        }
        else if ( applicationEventId.equals( EDIT_EVENTS_ID))
        {
            String objectsString = "";
            String titleI18n = i18n.format("edit_reservation.format", objectsString);
            return titleI18n;
        }
        else if ( applicationEventId.equals( EDIT_RESOURCES_ID))
        {
            String objectsString = i18n.getString("resources");
            String titleI18n = i18n.format("edit.format", objectsString);
            return titleI18n;
        }
        else
        {
            String titleI18n = i18n.getString("new_reservation");
            return  titleI18n;
        }
    }

    //	enhancement of the method to deal with arrays
    private String guessTitle(Collection obj)
    {
        Class<? extends Entity> raplaType = getRaplaType(obj);
        String title = "";
        if (raplaType != null)
        {
            String localname = RaplaType.getLocalName(raplaType);
            title = i18n.getString(localname);
        }

        return title;
    }

    //	method for determining the consistent RaplaType from different objects
    protected Class<? extends Entity> getRaplaType(Collection obj)
    {
        Set<Class<? extends Entity>> types = new HashSet<>();

        //		iterate all committed objects and store RaplayType of the objects in a Set
        //		identic typs aren't stored double because of Set
        for (Object o : obj)
        {
            if (o instanceof Entity)
            {
                final Class<? extends Entity> type = ((Entity) o).getTypeClass();
                types.add(type);
            }
        }

        //		check if there is a explicit type, then return this type; otherwise return null
        if (types.size() == 1)
            return types.iterator().next();
        else
            return null;
    }

    private <T extends Entity> Promise<EditTaskView> createEditDialog(Collection<T> list,  PopupContext popupContext, ApplicationEvent applicationEvent)
            throws RaplaException
    {
        boolean isMerge = applicationEvent.getApplicationEventId().equals(MERGE_RESOURCES_ID);
        if (list.size() == 0)
        {
            throw new RaplaException("Empty list not allowed. You must have at least one entity to edit.");
        }

        //		checks if all entities are from the same type; otherwise return
        if (getRaplaType(list) == null)
        {
            return null;
        }
        Collection<T> toEdit = new ArrayList<>(list);

        return raplaFacade.editListAsync(toEdit).thenCompose((editMap) -> getEditWidget(popupContext, applicationEvent, isMerge, editMap));
    }

    @Nullable
    private <T extends Entity> Promise<EditTaskView> getEditWidget(PopupContext popupContext, ApplicationEvent applicationEvent, boolean isMerge, Map<T, T> editMap) throws RaplaException {
        final Collection<T> origs = editMap.keySet();
        if (editMap.size() == 0) {
            return new ResolvedPromise<>(new RaplaException("No object to edit passed"));
        }
        boolean allReservations = editMap.values().stream().allMatch(entity->(entity instanceof Reservation));
        final Collection<T> editValues = editMap.values();
        final EditTaskView editTaskView;
        if ( allReservations && editValues.size() == 1)
        {
            editTaskView = reservationEditProvider.get();
            final Reservation original = (Reservation) editMap.keySet().iterator().next();
            final Reservation toEdit = (Reservation) editMap.get( original);
            ((ReservationEdit)editTaskView).editReservation( toEdit, original,appointmentBlock);
        }
        else {
            editTaskView = this.editTaskViewFactory.create(editMap, isMerge);
        }
        PopupContext popupEditContext = dialogUiFactory.createPopupContext( editTaskView);
        Runnable closeCmd = () ->
        {
            ApplicationEvent event = new ApplicationEvent(applicationEvent.getApplicationEventId(), applicationEvent.getInfo(), popupEditContext, null);
            Promise<Void> pr = processStop(event,editTaskView).thenRun(() ->
            {
                event.setStop(true);
                eventBus.publish(event);
            });
            handleException(pr, popupEditContext);
        };
        Consumer<Collection<T>> saveCmd =  (saveObjects) ->
        {
            Promise<Void> promise;
            Collection<T> entities = new ArrayList<>();
            entities.addAll(saveObjects);
            boolean canUndo = true;
            for (T obj : saveObjects)
            {
                if (obj instanceof Preferences || obj instanceof DynamicType || obj instanceof Category)
                {
                    canUndo = false;
                }
            }
            if ( isMerge)
            {
                final List<Allocatable> allAllocatables = (List<Allocatable>)saveObjects;
                final Allocatable selectedAllocatable = allAllocatables.get(0);

                final Set<ReferenceInfo<Allocatable>> allocatableIds = new LinkedHashSet<>();
                for (Allocatable allocatable : allAllocatables)
                {
                    allocatableIds.add(allocatable.getReference());
                }
                busyIdleObservable.onNext(i18n.getString("merge"));
                promise = raplaFacade.doMerge(selectedAllocatable, allocatableIds, clientFacade.getUser()).thenApply( (allocatable -> null));
            }
            else
            {
                busyIdleObservable.onNext(i18n.getString("save"));
                if ( allReservations) {
                    promise = reservationController
                            .saveReservations((Map<Reservation,Reservation>) editMap,  popupEditContext);

                }
                else if (canUndo)
                {
                    @SuppressWarnings({ "unchecked", "rawtypes" })
                    SaveUndo<T> saveCommand = new SaveUndo(raplaFacade, i18n, editMap);
                    CommandHistory commandHistory = clientFacade.getCommandHistory();
                    promise = commandHistory.storeAndExecute(saveCommand);
                }
                else
                {
                    promise = raplaFacade.dispatch(saveObjects, Collections.emptyList());
                }
            }

            Promise<Boolean> savePromise = handleExceptionCommandAbort(promise, popupEditContext);
            handleException(savePromise.thenAccept( ( successfull) ->
                    {
                        if (successfull)
                        {
                           close(applicationEvent);
                           raplaFacade.refreshAsync();
                        }
                    }),popupEditContext);
        };
        Runnable deleteCmd = () -> {
            busyIdleObservable.onNext(i18n.getString("delete"));
            final Promise<Void> promise = reservationController.deleteReservations(new HashSet(origs), popupContext);
            handleException( promise,popupContext);
            promise.thenRun( () ->closeCmd.run()).finally_(() ->  busyIdleObservable.onComplete());
        };
        editTaskView.start( saveCmd, closeCmd, deleteCmd);
        return new ResolvedPromise<>(editTaskView);
    }

    @Override
    public Promise<Void> processStop(ApplicationEvent event)
    {
        return processStop(event,  this.editTaskView);
    }

    private Promise<Void> processStop(ApplicationEvent event, EditTaskView editTaskView)
    {
        PopupContext popupContext = event.getPopupContext();
        if ( editTaskView != null)
        {
            if (!editTaskView.hasChanged())
            {
                return new ResolvedPromise<>(Promise.VOID);
            }
        }
        return processStop(popupContext);
    }

    private Promise<Void> processStop(PopupContext popupContext)
    {
            DialogInterface dlg = dialogUiFactory.createTextDialog(popupContext, i18n.getString("confirm-close.title"), i18n.getString("confirm-close.question"),
                    new String[] { i18n.getString("confirm-close.ok"), i18n.getString("back") });
            dlg.setIcon(i18n.getIcon("icon.question"));
            dlg.setDefault(1);
            final Promise<Integer> start = dlg.start(true);
            return start.thenAccept((integer)->
                    {
                        if (integer != 0)
                        {
                            throw new CommandAbortedException("test");
                        }
                    }
            );
    }

    void handleException(Promise promise,PopupContext popupContext)
    {
        promise.exceptionally(ex->
                {
                    final Throwable cause = ((Throwable) ex).getCause();
                    if ( cause != null)
                    {
                        ex = cause;
                    }
                    dialogUiFactory.showException((Throwable) ex, popupContext);
                }
        );
    }

    Promise<Boolean> handleExceptionCommandAbort(Promise promise,PopupContext popupContext)
    {
        return promise.handle((result,ex)->
                {
                    if( ex != null) {
                        final Throwable cause = ((Throwable) ex).getCause();
                        if (cause != null) {
                            ex = cause;
                        }
                        if (!(ex instanceof CommandAbortedException)) {
                            dialogUiFactory.showException((Throwable) ex, popupContext);
                        }
                        busyIdleObservable.onNext("");
                        return false;
                    }
                    else
                    {
                        busyIdleObservable.onNext("");
                        return true;
                    }
                });
    }

    public void close(ApplicationEvent applicationEvent)
    {
        applicationEvent.setStop(true);
        eventBus.publish(applicationEvent);
    }

    @Override
    public void updateView(ModificationEvent event)
    {
        if ( editTaskView != null)
        {
            PopupContext popupContext = dialogUiFactory.createPopupContext( editTaskView);
            if (editTaskView instanceof  ReservationEdit)
            {
                ReservationEdit c = (ReservationEdit)editTaskView;
                c.updateView(event);
                TimeInterval invalidateInterval = event.getInvalidateInterval();
                Reservation original = c.getOriginal();
                if (invalidateInterval != null && original != null && invalidateInterval.overlaps( new TimeInterval( original.getFirstDate(), original.getMaxEnd())))
                {

                    handleException(raplaFacade.getUpdateState( original ).thenAccept(state-> {
                        if ( state== RaplaFacade.ChangeState.newerVersionAvailable)  updateReservation( original);
                        if ( state== RaplaFacade.ChangeState.deleted)  deleteReservation();
                    }),popupContext);
                }
            }
            else
            {
                final Set<Entity> set = editTaskView.getEditMap().keySet();
                if (set.stream().anyMatch( event::isModified)) {
//                    DialogInterface warning = dialogUiFactory.createInfoDialog(popupContext,  i18n.getString("warning"), i18n.format("warning.update",set.toArray()));
//                    warning.start(true);
                    //applicationEvent.setStop( true );
                    //processStop( applicationEvent);
                }
            }

        }
    }

    public void updateReservation(Reservation newReservation) throws RaplaException
    {
        //        if (bSaving)
//            return;
//        getLogger().debug("Reservation has been changed.");
//        final PopupContext popupContext = dialogUiFactory.createPopupContext(()->contentPane);
//        DialogInterface dlg = dialogUiFactory
//                .createInfoDialog(popupContext, true, getI18n().getString("warning"), getI18n().getString("warning.reservation.update"));
//        commandHistory.clear();
//        try
//        {
//            dlg.setIcon("icon.warning");
//            dlg.start(true);
//            this.original = newReservation;
//            setReservation(getFacade().edit(newReservation), null);
//        }
//        catch (RaplaException ex)
//        {
//            dialogUiFactory.showException(ex, new SwingPopupContext(mainContent, null));
//        }
    }


    public void deleteReservation()
    {
//        if (bDeleting)
//            return;
//        getLogger().debug("Reservation has been deleted.");
//        DialogInterface dlg = dialogUiFactory
//                .createInfoDialog(new SwingPopupContext(mainContent, null), true, getI18n().getString("warning"), getI18n().getString("warning.reservation.delete"));
//        dlg.setIcon("icon.warning");
//        dlg.start(true);
//        closeWindow();
    }






    //     if (bSaving || dlg == null || !dlg.isVisible() || ui == null)
//        return;
//        if (shouldCancelOnModification(evt))
//    {
//        getLogger().warn("Object has been changed outside.");
//        final Component component = ui.getComponent();
//        DialogInterface warning = dialogUiFactory.createInfoDialog(new SwingPopupContext(component, null), true, getString("warning"), getI18n().format("warning.update", ui.getObjects()));
//        warning.start(true);
//        dlg.close();
//    }
//
//    private void setTitle()
//    {
//        String title = getI18n().format((bNew) ? "new_reservation.format" : "edit_reservation.format", getName(mutableReservation));
//        // FIXME post new title to popup container
//        //frame.setTitle(title);
//    }
//
//
//    protected boolean canClose()
//    {
//        if (!isModifiedSinceLastChange())
//            return true;
//
//        try
//        {
//            DialogInterface dlg = dialogUiFactory
//                    .createInfoDialog(new SwingPopupContext(mainContent, null), true, getString("confirm-close.title"), getString("confirm-close.question"),
//                            new String[] { getString("confirm-close.ok"), getString("back") });
//            dlg.setIcon("icon.question");
//            dlg.setDefault(1);
//            dlg.start(true);
//            return (dlg.getSelectedIndex() == 0);
//        }
//        catch (RaplaException e)
//        {
//            return true;
//        }
//
//    }
//
//    public boolean isModifiedSinceLastChange()
//    {
//        return !bSaved;
//    }







}
