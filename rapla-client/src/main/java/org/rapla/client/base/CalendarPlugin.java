package org.rapla.client.base;

import org.rapla.framework.RaplaException;

import java.time.LocalDateTime;
public interface CalendarPlugin<W>
{

    W provideContent();

    void updateContent() throws RaplaException;

    String CALENDAR_PLUGIN_ID = "calendar";

    String getName();

    boolean isEnabled();

    LocalDateTime calcNext(LocalDateTime currentDate);

    LocalDateTime calcPrevious(LocalDateTime currentDate);

}
