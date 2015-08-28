package org.rapla.client.base;

import java.util.Date;

import org.rapla.framework.RaplaException;

public interface CalendarPlugin<W> {

    String getName();

    W provideContent();

    void updateContent() throws RaplaException;

    String getId();
    
    Date calcNext(Date currentDate);
    
    Date calcPrevious(Date currentDate);
}
