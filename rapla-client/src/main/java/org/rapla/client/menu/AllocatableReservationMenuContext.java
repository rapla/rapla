package org.rapla.client.menu;

import org.rapla.client.PopupContext;
import org.rapla.facade.CalendarSelectionModel;

import java.time.LocalDateTime;
public class AllocatableReservationMenuContext extends SelectionMenuContext
{
    LocalDateTime start;

    public CalendarSelectionModel getModel()
    {
        return model;
    }

    public void setModel(CalendarSelectionModel model)
    {
        this.model = model;
    }

    CalendarSelectionModel model;
    public AllocatableReservationMenuContext(Object focusedObject, PopupContext popupContext)
    {
        super(focusedObject, popupContext);
    }


    public void setStart(LocalDateTime start)
    {
        this.start = start;
    }

    public LocalDateTime getStart()
    {
        return start;
    }


}
