package org.rapla.client;

import org.rapla.client.event.ApplicationEvent.ApplicationEventContext;
import org.rapla.entities.Entity;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarSelectionModel;

import java.util.List;

public class EditApplicationEventContext<T extends Entity<T>> implements ApplicationEventContext
{
    
    private final List<T> selectedObjects;
    private AppointmentBlock appointmentBlock;
    private CalendarSelectionModel calendarModel;

    public EditApplicationEventContext(List<T> selectedObjects)
    {
        this.selectedObjects = selectedObjects;
    }
    
    public List<T> getSelectedObjects()
    {
        return selectedObjects;
    }

    public void setAppointmentBlock(AppointmentBlock appointmentBlock)
    {
        this.appointmentBlock = appointmentBlock;
    }

    public AppointmentBlock getAppointmentBlock()
    {
        return appointmentBlock;
    }

    public CalendarSelectionModel getCalendarModel()
    {
        return calendarModel;
    }

    public void setCalendarModel(CalendarSelectionModel calendarModel)
    {
        this.calendarModel = calendarModel;
    }
}