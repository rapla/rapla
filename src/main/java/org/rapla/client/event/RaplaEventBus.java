package org.rapla.client.event;


import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Observable;
import org.rapla.scheduler.Subject;

import javax.inject.Inject;
import javax.inject.Singleton;

@DefaultImplementation(of = ApplicationEventBus.class,context = InjectionContext.all)
@DefaultImplementation(of = CalendarEventBus.class,context = InjectionContext.all)
@Singleton
public class RaplaEventBus implements ApplicationEventBus, CalendarEventBus
{
    final Subject<ApplicationEvent> applicationEventPublishSubject;
    final Subject<CalendarRefreshEvent> calendarRefreshEventPublishSubject;
    final Subject<OwnReservationsEvent> preferencesObservable;
    @Inject
    public RaplaEventBus(CommandScheduler scheduler)
    {
        applicationEventPublishSubject = scheduler.createPublisher();
        calendarRefreshEventPublishSubject = scheduler.createPublisher();
        preferencesObservable = scheduler.createPublisher();
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
    public void publish(OwnReservationsEvent event) {
        preferencesObservable.onNext(event);
    }

    @Override
    public Observable<ApplicationEvent> getApplicationEventObservable() {
        return applicationEventPublishSubject;
    }

    @Override
    public Observable<CalendarRefreshEvent> getCalendarRefreshObservable() {
        return calendarRefreshEventPublishSubject;
    }

    @Override
    public Observable<OwnReservationsEvent> getCalendarPreferencesObservable() {
        return preferencesObservable;
    }
}
