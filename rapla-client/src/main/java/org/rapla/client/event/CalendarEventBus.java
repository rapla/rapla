package org.rapla.client.event;


import org.rapla.scheduler.Observable;

public interface CalendarEventBus {
    void publish(CalendarRefreshEvent event);
    Observable<CalendarRefreshEvent> getCalendarRefreshObservable();
}
