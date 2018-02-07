package org.rapla.client;

import org.rapla.client.internal.PresenterChangeCallback;
import org.rapla.facade.ModificationEvent;
import org.rapla.framework.RaplaException;
import org.rapla.scheduler.Observable;

public interface CalendarContainer
{
    void scrollToStart();
    void closeFilterButton();
    Observable update();
    void update(ModificationEvent evt) throws RaplaException;
    void init(boolean editable,PresenterChangeCallback callback) throws RaplaException;
    RaplaWidget provideContent();
}
