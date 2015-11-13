package org.rapla.client.swing;

import java.awt.Component;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.event.StartActivityEvent;
import org.rapla.client.event.StartActivityEvent.StartActivityEventHandler;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.plugin.defaultwizard.client.swing.DefaultWizard;

import com.google.web.bindery.event.shared.EventBus;

@Singleton
public class SwingActivityController extends RaplaComponent implements StartActivityEventHandler
{
    
    public static final String CREATE_RESERVATION_FOR_DYNAMIC_TYPE = "createReservationFromDynamicType";
    public static final String CREATE_RESERVATION_FROM_TEMPLATE = "reservationFromTemplate";
    
    private final EditController editController;
    private final ClientFacade facade;
    private final CalendarSelectionModel model;

    @Inject
    public SwingActivityController(final EventBus eventBus, final EditController editController, final ClientFacade facade, final CalendarSelectionModel model, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger)
    {
        super(facade, i18n, raplaLocale, logger);
        this.editController = editController;
        this.facade = facade;
        this.model = model;
        eventBus.addHandler(StartActivityEvent.TYPE, this);
    }

    @Override
    public void startActivity(StartActivityEvent event)
    {
        final String eventId = event.getId();
        switch (eventId)
        {
            case CREATE_RESERVATION_FOR_DYNAMIC_TYPE:
            {
                final String dynamicTypeId = event.getInfo();
                final Entity resolve = facade.getOperator().resolve(dynamicTypeId);
                final DynamicType type = (DynamicType)resolve;
                Classification newClassification = type.newClassification();
                Reservation r = facade.newReservation(newClassification);
                Appointment appointment = createAppointment(model);
                r.addAppointment(appointment);
                final List<Reservation> singletonList = Collections.singletonList( r);
                List<Reservation> list = DefaultWizard.addAllocatables(model, singletonList, facade.getUser());
                String title = null;
                // FIXME mainComponent
                Component mainComponent = null;
                final PopupContext swingPopupContext = new SwingPopupContext(mainComponent, null);
                EditController.EditCallback<List<Reservation>> callback = null;
                editController.edit(list, title, swingPopupContext,callback);
                break;
            }
            case CREATE_RESERVATION_FROM_TEMPLATE:
            {
                final String templateId = event.getInfo();
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
                Date beginn = getStartDate(model);
                Collection<Reservation> newReservations = getModification().copy(reservations, beginn, keepTime);
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
                List<Reservation> list = DefaultWizard.addAllocatables(model, newReservations, getUser());
                // FIXME lookup main component
                final Component mainComponent = null;//getMainComponent();
                final SwingPopupContext popupContext = new SwingPopupContext(mainComponent, null);
                String title = null;
                EditController.EditCallback<List<Reservation>> callback = null;
                editController.edit(list, title, popupContext, callback);
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
        final Collection<Allocatable> templates = facade.getTemplates();
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
        
        Date startDate = getStartDate(model);
        Date endDate = getEndDate( model, startDate);
        Appointment appointment =  getModification().newAppointment(startDate, endDate);
        return appointment;
    }


}
