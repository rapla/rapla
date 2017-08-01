package org.rapla.client.base;

import org.rapla.framework.RaplaException;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

import java.util.Date;

@ExtensionPoint(context = InjectionContext.gwt, id = CalendarPlugin.CALENDAR_PLUGIN_ID)
public interface CalendarPlugin<W>
{

    W provideContent();

    void updateContent() throws RaplaException;

    String CALENDAR_PLUGIN_ID = "calendar";

    String getName();

    boolean isEnabled();

    Date calcNext(Date currentDate);

    Date calcPrevious(Date currentDate);
}
