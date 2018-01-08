package org.rapla.client.event;


import org.rapla.scheduler.Observable;

public interface CalendarEventBus {
    void publish(CalendarRefreshEvent event);
    void publish(OwnReservationsEvent event);
    Observable<CalendarRefreshEvent> getCalendarRefreshObservable();
    Observable<OwnReservationsEvent> getCalendarPreferencesObservable();
}
