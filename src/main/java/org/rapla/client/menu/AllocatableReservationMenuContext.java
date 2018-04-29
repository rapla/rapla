package org.rapla.client.menu;

import org.rapla.client.PopupContext;
import org.rapla.facade.CalendarSelectionModel;

import java.util.Date;

public class AllocatableReservationMenuContext extends SelectionMenuContext
{
    Date start;

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


    public void setStart(Date start)
    {
        this.start = start;
    }

    public Date getStart()
    {
        return start;
    }


}
