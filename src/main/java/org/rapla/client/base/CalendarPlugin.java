package org.rapla.client.base;

import org.rapla.framework.RaplaException;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

import java.util.Date;

@ExtensionPoint(context = InjectionContext.client, id = CalendarPlugin.CALENDAR_PLUGIN_ID)
public interface CalendarPlugin<W>
{

    W provideContent();

    void updateContent() throws RaplaException;

    String CALENDAR_PLUGIN_ID = "calendar";

    String getName();

    boolean isEnabled();

    Date calcNext(Date currentDate);

    Date calcPrevious(Date currentDate);

    /** {@code LocalDate} variants — distinct names. */
    default java.time.LocalDate calcNextLocalDate(java.time.LocalDate currentDate) {
        Date d = calcNext(currentDate == null ? null : org.rapla.components.util.DateTools.toDate(currentDate));
        return d == null ? null : org.rapla.components.util.DateTools.toLocalDate(d);
    }
    default java.time.LocalDate calcPreviousLocalDate(java.time.LocalDate currentDate) {
        Date d = calcPrevious(currentDate == null ? null : org.rapla.components.util.DateTools.toDate(currentDate));
        return d == null ? null : org.rapla.components.util.DateTools.toLocalDate(d);
    }
}
