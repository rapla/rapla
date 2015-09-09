package org.rapla.client.base;

import org.rapla.framework.RaplaException;
import org.rapla.inject.ExtensionPoint;

import java.util.Date;

@ExtensionPoint
public interface CalendarPlugin<W>  {
    String getName();

    W provideContent();

    void updateContent() throws RaplaException;

    String getId();

    boolean isEnabled();
    
    Date calcNext(Date currentDate);
    
    Date calcPrevious(Date currentDate);
}
