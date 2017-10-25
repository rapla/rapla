package org.rapla.client.internal.edit;

import com.google.web.bindery.event.shared.EventBus;
import io.reactivex.functions.Consumer;
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
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.inject.Extension;
import org.rapla.inject.ExtensionRepeatable;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
@ExtensionRepeatable({ @Extension(id = EditTaskPresenter.EDIT_EVENTS_ID, provides = TaskPresenter.class),
        @Extension(id = EditTaskPresenter.EDIT_RESOURCES_ID, provides = TaskPresenter.class),
        @Extension(id = EditTaskPresenter.CREATE_RESERVATION_FOR_DYNAMIC_TYPE, provides = TaskPresenter.class),
        @Extension(id = EditTaskPresenter.CREATE_RESERVATION_FROM_TEMPLATE, provides = TaskPresenter.class),
        @Extension(id = EditTaskPresenter.MERGE_RESOURCES_ID, provides = TaskPresenter.class)
})
public class EditTaskPresenter implements TaskPresenter
{
    public static final String CREATE_RESERVATION_FROM_TEMPLATE = "reservationFromTemplate";
    private final DialogUiFactoryInterface dialogUiFactory;
    private final RaplaFacade raplaFacade;
    private final RaplaResources i18n;
    private final ClientFacade clientFacade;
    private final EventBus eventBus;
    private final CalendarSelectionModel model;

    public static final String CREATE_RESERVATION_FOR_DYNAMIC_TYPE = "createReservationFromDynamicType";
    final static public String EDIT_EVENTS_ID = "editEvents";
    final static public String MERGE_RESOURCES_ID = "mergeResources";
    final static public String EDIT_RESOURCES_ID = "editResources";
    private final Provider<ReservationEdit> reservationEditProvider;
    private final EditTaskView editTaskView;
    AppointmentBlock appointmentBlock= null;
    boolean bSaving = false;
    boolean bDeleting = false;
    final ReservationController reservationController;
    private final Set<MergeCheckExtension> mergeCheckers;

    public interface EditTaskView
    {
        <T  extends Entity> RaplaWidget doSomething(Collection<T> toEdit,Consumer<Collection<T>> save, Runnable close, boolean isMerge) throws RaplaException;
    }

    @Inject
    public EditTaskPresenter(ClientFacade clientFacade, EditTaskView editTaskView, DialogUiFactoryInterface dialogUiFactory, RaplaResources i18n, EventBus eventBus, CalendarSelectionModel model, Provider<ReservationEdit> reservationEditProvider,
            ReservationController reservationController, Set<MergeCheckExtension> mergeCheckers)
    {
        this.editTaskView = editTaskView;
        this.dialogUiFactory = dialogUiFactory;
        this.i18n = i18n;
        this.clientFacade = clientFacade;
        this.eventBus = eventBus;
        this.model = model;
        this.reservationEditProvider = reservationEditProvider;
        this.raplaFacade = clientFacade.getRaplaFacade();
        this.reservationController = reservationController;
        this.mergeCheckers = mergeCheckers;
    }

    @Override
    public Promise<RaplaWidget> startActivity(ApplicationEvent applicationEvent)
    {
        appointmentBlock = null;
        final String taskId = applicationEvent.getApplicationEventId();
        String info = applicationEvent.getInfo();
        PopupContext popupContext = applicationEvent.getPopupContext();
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
                    entities=new LinkedHashSet<>(editApplicationEventContext.getSelectedObjects());
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

                RaplaWidget<?> edit = createEditDialog(entities,  popupContext, applicationEvent);
                return new ResolvedPromise<>(edit);
            }
            else if (CREATE_RESERVATION_FOR_DYNAMIC_TYPE.equals(taskId))
            {
                final String dynamicTypeId = info;
                final Entity resolve = raplaFacade.resolve(new ReferenceInfo<Entity>(dynamicTypeId, DynamicType.class));
                final DynamicType type = (DynamicType) resolve;
                Classification newClassification = type.newClassification();
                final User user = clientFacade.getUser();
                Reservation r = raplaFacade.newReservation(newClassification, user);
                Appointment appointment = createAppointment();
                r.addAppointment(appointment);
                final List<Reservation> singletonList = Collections.singletonList(r);
                List<Reservation> list = RaplaComponent.addAllocatables(model, singletonList, user);
                String title = null;
                final RaplaWidget<?> editDialog = createEditDialog(list,  popupContext, applicationEvent);
                return new ResolvedPromise<>(editDialog);
            }
            else if (CREATE_RESERVATION_FROM_TEMPLATE.equals(taskId))
            {

                final String templateId = info;
                User user = clientFacade.getUser();
                Allocatable template = findTemplate(templateId);
                final Promise<Collection<Reservation>> templatePromise = raplaFacade.getTemplateReservations(template);
                Promise<RaplaWidget> widgetPromise = templatePromise.thenApply((reservations) ->
                {
                    if (reservations.size() == 0)
                    {
                        throw new EntityNotFoundException("Template " + template + " is empty. Please create events in template first.");
                    }
                    Boolean keepOrig = (Boolean) template.getClassification().getValue("fixedtimeandduration");
                    Collection<TimeInterval> markedIntervals = model.getMarkedIntervals();
                    boolean markedIntervalTimeEnabled = model.isMarkedIntervalTimeEnabled();
                    boolean keepTime = !markedIntervalTimeEnabled || (keepOrig == null || keepOrig);
                    Date beginn = RaplaComponent.getStartDate(model, raplaFacade, user);
                    Collection<Reservation> newReservations = raplaFacade.copy(reservations, beginn, keepTime, user);
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
                return widgetPromise;
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

    protected Appointment createAppointment() throws RaplaException
    {

        Date startDate = RaplaComponent.getStartDate(model, raplaFacade, clientFacade.getUser());
        Date endDate = RaplaComponent.calcEndDate(model, startDate);
        Appointment appointment = raplaFacade.newAppointment(startDate, endDate);
        return appointment;
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
        Set<Class<? extends Entity>> types = new HashSet<Class<? extends Entity>>();

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

    private <T extends Entity> RaplaWidget createEditDialog(Collection<T> list,  PopupContext popupContext, ApplicationEvent applicationEvent)
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


        //		gets for all objects in array a modifiable version and add it to a set to avoid duplication
        Collection<T> nonEditableObjects = new ArrayList<>(list);
        Collection<T> toEdit = new ArrayList<>();
        for (Iterator<T> iterator = nonEditableObjects.iterator(); iterator.hasNext(); )
        {
            T t = iterator.next();
            if (!t.isReadOnly())
            {
                iterator.remove();
                toEdit.add(t);
            }
        }
        toEdit.addAll(raplaFacade.editList(nonEditableObjects));
        List<T> originals = new ArrayList<T>();
        Map<T, T> persistant = raplaFacade.getPersistantForList(nonEditableObjects);
        for (T entity : toEdit)
        {

            @SuppressWarnings("unchecked") Entity<T> mementable = persistant.get(entity);
            if (mementable != null)
            {
                if (originals == null)
                {
                    throw new RaplaException("You cannot edit persistant and new entities in one operation");
                }
                originals.add(mementable.clone());
            }
            else
            {
                if (originals != null && !originals.isEmpty())
                {
                    throw new RaplaException("You cannot edit persistant and new entities in one operation");
                }
                originals = null;
            }
        }
        final List<T> origs = originals;
        if (toEdit.size() > 0)
        {
            Consumer<Collection<T>> saveCmd =  (saveObjects) ->
            {
                Collection<T> entities = new ArrayList<T>();
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
                    doMerge(selectedAllocatable, allocatableIds);
                    close( applicationEvent);
                    try
                    {
                        raplaFacade.refresh();
                    }
                    catch (RaplaException e)
                    {
                        dialogUiFactory.showException(e, null);
                    }
                }
                else
                {
                    Promise<Void> promise;
                    if (canUndo)
                    {
                        @SuppressWarnings({ "unchecked", "rawtypes" })
                        SaveUndo<T> saveCommand = new SaveUndo(raplaFacade, i18n, entities, origs);
                        CommandHistory commandHistory = clientFacade.getCommandHistory();
                        promise = commandHistory.storeAndExecute(saveCommand);
                    }
                    else
                    {
                        promise = raplaFacade.dispatch(saveObjects, Collections.emptyList());
                    }
                    handleException(promise.thenRun(() ->
                    {
                        close(applicationEvent);
                    }), popupContext);
                }
            };

            Runnable closeCmd = () -> close(applicationEvent);

            if (list.size() == 1)
            {
                final Entity<?> testObj = (Entity<?>) toEdit.iterator().next();
                if (testObj instanceof Reservation)
                {
                    ReservationEdit<?> c = reservationEditProvider.get();
                    PopupContext popupEditContext = dialogUiFactory.createPopupContext( c);
                    Runnable reservationSaveCmd = () -> {
                        this.bSaving = true;
                        boolean firstTime = true;
                        final Promise promise = reservationController
                                .checkAndDistpatch((Collection<Reservation>) toEdit, Collections.EMPTY_LIST, firstTime, popupEditContext).thenRun(()->closeCmd.run());

                        handleException( promise,popupEditContext).whenComplete((t,ex)->bSaving = false);
                    };
                    Runnable deleteCmd = () -> {
                        this.bDeleting = true;
                        final Reservation original = (Reservation) origs.get(0);
                        final Promise<Void> promise = reservationController.deleteReservation(original, popupContext);
                        promise.thenRun( () ->closeCmd.run()).whenComplete((t,ex) ->  bDeleting = false);
                    };
                    Runnable closeCmd2 = () ->
                    {
                        ApplicationEvent event = new ApplicationEvent(applicationEvent.getApplicationEventId(), applicationEvent.getInfo(), popupEditContext, null);
                        Promise<Void> pr = processStop(event,c).thenRun(() ->
                        {
                            event.setStop(true);
                            eventBus.fireEvent(event);
                        });
                        handleException(pr, popupEditContext);
                    };
                    final Reservation original = origs != null ? (Reservation) origs.get(0) : null;
                    c.editReservation((Reservation) testObj, original,appointmentBlock, reservationSaveCmd, closeCmd2, deleteCmd);
                    //c.addAppointmentListener();
                    return c;
                }
            }
            final RaplaWidget raplaWidget = editTaskView.doSomething(toEdit, saveCmd, closeCmd, isMerge);
            return raplaWidget;
        }
        return null;
    }

    @Override
    public Promise<Void> processStop(ApplicationEvent event, RaplaWidget widget)
    {
        PopupContext popupContext = event.getPopupContext();
        if ( widget != null && widget instanceof ReservationEdit)
        {
            if (!((ReservationEdit) widget).hasChanged())
            {
                return new ResolvedPromise<>(Promise.VOID);
            }
        }
        return processStop(popupContext);
    }

    private Promise<Void> processStop(PopupContext popupContext)
    {
        try
        {
            DialogInterface dlg = dialogUiFactory.create(popupContext, false, i18n.getString("confirm-close.title"), i18n.getString("confirm-close.question"),
                    new String[] { i18n.getString("confirm-close.ok"), i18n.getString("back") });
            dlg.setIcon("icon.question");
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
        catch (RaplaException ex)
        {
            return new ResolvedPromise<Void>(ex);
        }
    }

    Promise handleException(Promise promise,PopupContext popupContext)
    {
        return promise.exceptionally(ex->
                {
                    final Throwable cause = ((Throwable) ex).getCause();
                    if ( cause != null)
                    {
                        ex = cause;
                    }
                    if (!(ex instanceof CommandAbortedException))
                    {
                        dialogUiFactory.showException((Throwable) ex, popupContext);
                    }
                    return Promise.VOID;
                }
        );
    }

    public void doMerge(Allocatable selectedObject, Set<ReferenceInfo<Allocatable>> allocatableIds)
    {
        try
        {
            allocatableIds.remove(selectedObject.getReference());
            final User user = clientFacade.getUser();
            raplaFacade.doMerge(selectedObject, allocatableIds, user);
        }
        catch (RaplaException e)
        {
            dialogUiFactory.showWarning(e.getMessage(), null);
        }
    }

    public void close(ApplicationEvent applicationEvent)
    {
        applicationEvent.setStop(true);
        eventBus.fireEvent(applicationEvent);
    }

    @Override
    public void updateView(ModificationEvent event)
    {
        // FIXME
//
//        ArrayList<ReservationEdit> clone = new ArrayList<ReservationEdit>(editWindowList);
//        for (ReservationEdit edit : clone)
//        {
//            ReservationEdit c = edit;
//            c.updateView(evt);
//            TimeInterval invalidateInterval = event.getInvalidateInterval();
//            Reservation original = c.getOriginal();
//            if (invalidateInterval != null && original != null)
//            {
//                boolean test = false;
//                for (Appointment app : original.getAppointments())
//                {
//                    if (app.overlaps(invalidateInterval))
//                    {
//                        test = true;
//                    }
//
//                }
//                if (test)
//                {
//                    try
//                    {
//                        Reservation persistant = getFacade().getPersistant(original);
//                        Date version = persistant.getLastChanged();
//                        Date originalVersion = original.getLastChanged();
//                        if (originalVersion != null && version != null && originalVersion.before(version))
//                        {
//                            c.updateReservation(persistant);
//                        }
//                    }
//                    catch (EntityNotFoundException ex)
//                    {
//                        c.deleteReservation();
//                    }
//
//                }
//            }
//
//        }
    }

//    public void deleteReservation() throws RaplaException
//    {
//        if (bDeleting)
//            return;
//        getLogger().debug("Reservation has been deleted.");
//        DialogInterface dlg = dialogUiFactory
//                .create(new SwingPopupContext(mainContent, null), true, getString("warning"), getString("warning.reservation.delete"));
//        dlg.setIcon("icon.warning");
//        dlg.start(true);
//        closeWindow();
//    }
//
//    public void updateReservation(Reservation newReservation) throws RaplaException
//    {
//        if (bSaving)
//            return;
//        getLogger().debug("Reservation has been changed.");
//        DialogInterface dlg = dialogUiFactory
//                .create(new SwingPopupContext(mainContent, null), true, getString("warning"), getString("warning.reservation.update"));
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
//    }

//     if (bSaving || dlg == null || !dlg.isVisible() || ui == null)
//        return;
//        if (shouldCancelOnModification(evt))
//    {
//        getLogger().warn("Object has been changed outside.");
//        final Component component = ui.getComponent();
//        DialogInterface warning = dialogUiFactory.create(new SwingPopupContext(component, null), true, getString("warning"), getI18n().format("warning.update", ui.getObjects()));
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
//                    .create(new SwingPopupContext(mainContent, null), true, getString("confirm-close.title"), getString("confirm-close.question"),
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



    //    public void dataChanged(ModificationEvent evt) throws RaplaException
    //    {
    //        super.dataChanged(evt);
    //        if (bSaving || dlg == null || !dlg.isVisible() || ui == null)
    //            return;
    //        if (shouldCancelOnModification(evt))
    //        {
    //            getPrivateEditDialog().removeEditDialog(this);
    //        }
    //    }
    //
    //    @Override
    //    protected void cleanupAfterClose()
    //    {
    //        getPrivateEditDialog().removeEditDialog(EditDialog.this);
    //    }





}
