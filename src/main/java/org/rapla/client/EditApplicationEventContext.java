package org.rapla.client;

import java.util.List;

import org.rapla.client.event.ApplicationEvent.ApplicationEventContext;
import org.rapla.entities.Entity;

public class EditApplicationEventContext<T extends Entity<T>> implements ApplicationEventContext
{
    
    private final List<T> selectedObjects;

    public EditApplicationEventContext(List<T> selectedObjects)
    {
        this.selectedObjects = selectedObjects;
    }
    
    public List<T> getSelectedObjects()
    {
        return selectedObjects;
    }
}