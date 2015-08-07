package org.rapla.client.base;

import org.rapla.framework.RaplaException;

public interface CalendarPlugin<W> {

    String getName();

    W provideContent();

    void updateContent() throws RaplaException;
}
