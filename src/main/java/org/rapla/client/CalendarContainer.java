package org.rapla.client;

import org.rapla.client.internal.PresenterChangeCallback;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ModificationEvent;
import org.rapla.framework.RaplaException;
import org.rapla.scheduler.Observable;

public interface CalendarContainer
{
    void scrollToStart();
    void closeFilterButton();
    Observable<Object> update();
    void update(ModificationEvent evt) throws RaplaException;
    void init(boolean editable,CalendarSelectionModel model,PresenterChangeCallback callback) throws RaplaException;
    RaplaWidget provideContent();
}
