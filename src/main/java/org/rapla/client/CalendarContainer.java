package org.rapla.client;

import org.rapla.client.internal.PresenterChangeCallback;
import org.rapla.facade.ModificationEvent;
import org.rapla.framework.RaplaException;

public interface CalendarContainer
{
    void scrollToStart();
    void closeFilterButton();
    void update();
    void update(ModificationEvent evt) throws RaplaException;
    void init(boolean editable,PresenterChangeCallback callback) throws RaplaException;
    RaplaWidget provideContent();
}
