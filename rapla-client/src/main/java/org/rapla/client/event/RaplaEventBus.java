package org.rapla.client.event;


import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Observable;
import org.rapla.scheduler.Subject;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Autowired;
import jakarta.inject.Singleton;

@Service
@Singleton
public class RaplaEventBus implements ApplicationEventBus, CalendarEventBus
{
    final Subject<ApplicationEvent> applicationEventPublishSubject;
    final Subject<CalendarRefreshEvent> calendarRefreshEventPublishSubject;
    @Autowired
    public RaplaEventBus(CommandScheduler scheduler)
    {
        applicationEventPublishSubject = org.rapla.scheduler.Observables.createPublisher(scheduler.getExecutor());
        calendarRefreshEventPublishSubject = org.rapla.scheduler.Observables.createPublisher(scheduler.getExecutor());
    }

    @Override
    public void publish(ApplicationEvent event) {
        applicationEventPublishSubject.onNext(event);
    }

    @Override
    public void publish(CalendarRefreshEvent event) {
        calendarRefreshEventPublishSubject.onNext( event );
    }

    @Override
    public Observable<ApplicationEvent> getApplicationEventObservable() {
        return applicationEventPublishSubject;
    }

    @Override
    public Observable<CalendarRefreshEvent> getCalendarRefreshObservable() {
        return calendarRefreshEventPublishSubject;
    }

}
