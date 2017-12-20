package org.rapla.client.event;

import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.scheduler.Observable;
import org.rapla.scheduler.sync.JavaObservable;

import javax.inject.Inject;
import javax.inject.Singleton;

@DefaultImplementation(of = ApplicationEventBus.class,context = InjectionContext.all)
@DefaultImplementation(of = CalendarEventBus.class,context = InjectionContext.all)
@Singleton
public class RaplaEventBus implements ApplicationEventBus, CalendarEventBus
{
    final PublishSubject<ApplicationEvent> applicationEventPublishSubject = PublishSubject.create();
    final PublishSubject<CalendarRefreshEvent> calendarRefreshEventPublishSubject = PublishSubject.create();
    final PublishSubject<OwnReservationsEvent> preferencesObservable = PublishSubject.create();
    @Inject
    public RaplaEventBus()
    {
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
        return new JavaObservable<ApplicationEvent>(applicationEventPublishSubject);
    }



    @Override
    public Observable<CalendarRefreshEvent> getCalendarRefreshObservable() {
        return new JavaObservable<CalendarRefreshEvent>(calendarRefreshEventPublishSubject);
    }

    @Override
    public Observable<OwnReservationsEvent> getCalendarPreferencesObservable() {
        return new JavaObservable<OwnReservationsEvent>(preferencesObservable);
    }
}
