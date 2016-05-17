package org.rapla.client.swing;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.rapla.client.event.AbstractActivityController;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import com.google.web.bindery.event.shared.EventBus;

@Singleton
@DefaultImplementation(context=InjectionContext.swing, of=AbstractActivityController.class)
public class SwingActivityController extends AbstractActivityController
{

    public static final String MERGE_ALLOCATABLES = "merge";


    //    private final EditController editController;
//    private final ClientFacade facade;
//    private final CalendarSelectionModel model;
//    private final MergeController mergeController;

    @Inject
    public SwingActivityController(EventBus eventBus, Logger logger)
    {
        super(eventBus, logger);
    }


    @Override protected boolean isPlace(ApplicationEvent activity)
    {
        final String applicationEventId = activity.getApplicationEventId();
        switch(applicationEventId)
        {
            case "cal":
                return true;
        }
        return false;
    }

    @Override protected void parsePlaceAndActivities() throws RaplaException
    {
        activities.add(new ApplicationEvent("cal", "Standard", null, null));
    }

    @Override protected void updateHistroryEntry()
    {

    }

    /*
    @Override
    public void startActivity(StartActivityEvent event)
    {
        final String eventId = event.getId();
        final User user = facade.getUser();
        final String info = event.getInfo();
        final RaplaFacade raplaFacade = facade.getRaplaFacade();
        Component mainComponent = new RaplaGUIComponent(facade, getI18n(), getRaplaLocale(), getLogger()).getMainComponent();
        final PopupContext popupContext = new SwingPopupContext(mainComponent, null);

        switch (eventId)
        {
            case CREATE_RESERVATION_FOR_DYNAMIC_TYPE:
            {
                final String dynamicTypeId = info;
                final Entity resolve = raplaFacade.resolve(new ReferenceInfo<Entity>(dynamicTypeId, DynamicType.class));
                final DynamicType type = (DynamicType)resolve;
                Classification newClassification = type.newClassification();
                Reservation r = raplaFacade.newReservation(newClassification, user);
                Appointment appointment = createAppointment(model);
                r.addAppointment(appointment);
                final List<Reservation> singletonList = Collections.singletonList( r);
                List<Reservation> list = DefaultWizard.addAllocatables(model, singletonList, user);
                String title = null;
                // TODO think about a better solution
                EditController.EditCallback<List<Reservation>> callback = null;
                editController.edit(list, title, popupContext,callback);
                break;
            }
            case CREATE_RESERVATION_FROM_TEMPLATE:
            {
                final String templateId = info;
                Allocatable template = findTemplate(templateId);
                Collection<Reservation> reservations = getQuery().getTemplateReservations(template);
                if (reservations.size() == 0)
                {
                    throw new EntityNotFoundException("Template " + template + " is empty. Please create events in template first.");
                }
                Boolean keepOrig = (Boolean) template.getClassification().getValue("fixedtimeandduration");
                Collection<TimeInterval> markedIntervals = model.getMarkedIntervals();
                boolean markedIntervalTimeEnabled = model.isMarkedIntervalTimeEnabled();
                boolean keepTime = !markedIntervalTimeEnabled || (keepOrig == null || keepOrig); 
                Date beginn = getStartDate(model, user);
                Collection<Reservation> newReservations = getFacade().copy(reservations, beginn, keepTime, user);
                if ( markedIntervals.size() >0 && reservations.size() == 1 && reservations.iterator().next().getAppointments().length == 1 && keepOrig == Boolean.FALSE)
                {
                    Appointment app = newReservations.iterator().next().getAppointments()[0];
                    TimeInterval first = markedIntervals.iterator().next();
                    Date end = first.getEnd();
                    if (!markedIntervalTimeEnabled)
                    {
                        end = getRaplaLocale().toDate( end,app.getEnd() );
                    }
                    if (!beginn.before(end))
                    {
                        end = new Date( app.getStart().getTime() + DateTools.MILLISECONDS_PER_HOUR );
                    }
                    app.move(app.getStart(), end);
                }
                List<Reservation> list = DefaultWizard.addAllocatables(model, newReservations, user);
                String title = null;
                EditController.EditCallback<List<Reservation>> callback = null;
                editController.edit(list, title, popupContext, callback);
                break;
            }
            case MERGE_ALLOCATABLES:
            {
                if(info == null || info.isEmpty())
                {
                    getLogger().warn("no info sent for merge "+info);
                }
                final String[] split = info.split(",");
                Collection<Allocatable> entities = new ArrayList<>();
                for (String id : split)
                {   
                    final Allocatable resolve = raplaFacade.resolve(new ReferenceInfo<Allocatable>(id, Allocatable.class));
                    entities.add(resolve);
                }
                mergeController.startMerge(entities);
                break;
            }
            default:
            {
                getLogger().warn("received start activity event with id " + eventId + " but no handling was defined");
                break;
            }
        }
    }
    private Allocatable findTemplate(String templateId)
    {
        final Collection<Allocatable> templates = facade.getRaplaFacade().getTemplates();
        for (Allocatable allocatable : templates)
        {
            if(allocatable.getId().equals(templateId))
            {
                return allocatable;
            }
        }
        return null;
    }

    protected Appointment createAppointment(CalendarModel model)
            throws RaplaException {
        
        Date startDate = getStartDate(model, facade.getUser());
        Date endDate = getEndDate( model, startDate);
        Appointment appointment =  getFacade().newAppointment(startDate, endDate);
        return appointment;
    }
    */


}
