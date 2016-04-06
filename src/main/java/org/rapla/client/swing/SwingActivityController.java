package org.rapla.client.swing;

import com.google.web.bindery.event.shared.EventBus;
import org.rapla.client.event.AbstractActivityController;
import org.rapla.client.event.ActivityPresenter;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.toolkit.FrameControllerList;
import org.rapla.client.swing.toolkit.RaplaFrame;
import org.rapla.client.swing.toolkit.RaplaWidget;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.util.Map;

@Singleton
@DefaultImplementation(context=InjectionContext.swing, of=AbstractActivityController.class)
public class SwingActivityController extends AbstractActivityController implements VetoableChangeListener
{
    
    public static final String CREATE_RESERVATION_FOR_DYNAMIC_TYPE = "createReservationFromDynamicType";
    public static final String CREATE_RESERVATION_FROM_TEMPLATE = "reservationFromTemplate";
    public static final String EDIT_EVENTS = "editEvents";
    public static final String EDIT_RESORCES = "editResources";
    public static final String MERGE_ALLOCATABLES = "merge";

    private final RaplaImages raplaImages;
    private final FrameControllerList frameControllerList;
//    private final EditController editController;
//    private final ClientFacade facade;
//    private final CalendarSelectionModel model;
//    private final MergeController mergeController;

    @Inject
    public SwingActivityController(@SuppressWarnings("rawtypes") EventBus eventBus, Logger logger, Map<String, ActivityPresenter> activityPresenters,
            RaplaImages raplaImages, FrameControllerList frameControllerList)
    {
        super(eventBus, logger, activityPresenters);
        this.raplaImages = raplaImages;
        this.frameControllerList = frameControllerList;
    }

    protected void initComponent( RaplaWidget<Object> objectRaplaWidget)
    {
        RaplaFrame frame = new RaplaFrame(frameControllerList);
        final Container component = (Container)objectRaplaWidget.getComponent();
        frame.setContentPane(component);
        Dimension dimension = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setSize(new Dimension(
                        Math.min(dimension.width,990)
                        // BJO 00000032 temp fix for filter out of frame bounds
                        ,Math.min(dimension.height-10,720)
                        //,Math.min(dimension.height-10,1000)
                )
        );
        frame.addVetoableChangeListener(this);
        frame.setIconImage( raplaImages.getIconFromKey("icon.edit_window_small").getImage());
    }

    private void showFrame()
    {



    }

    public void vetoableChange(PropertyChangeEvent evt) throws PropertyVetoException
    {
        if (!canClose())
            throw new PropertyVetoException("Don't close",evt);
        closeWindow();
    }

    @Override protected void parsePlaceAndActivities() throws RaplaException
    {


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
