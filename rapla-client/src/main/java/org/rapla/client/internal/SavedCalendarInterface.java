package org.rapla.client.internal;

import org.rapla.client.RaplaWidget;
import org.rapla.framework.RaplaException;

public interface SavedCalendarInterface extends RaplaWidget
{
    void update() throws RaplaException;
}
