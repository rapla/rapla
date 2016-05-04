package org.rapla.client;

import java.util.List;

import org.rapla.client.event.ApplicationEvent.ApplicationEventContext;
import org.rapla.entities.Entity;
import org.rapla.entities.domain.AppointmentBlock;

public class EditApplicationEventContext<T extends Entity<T>> implements ApplicationEventContext
{
    
    private final List<T> selectedObjects;
    private AppointmentBlock appointmentBlock;

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
}