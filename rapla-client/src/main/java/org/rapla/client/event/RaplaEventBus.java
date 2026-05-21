package org.rapla.client.event;


import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Observable;
import org.rapla.scheduler.Subject;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Autowired;

@Service
public class RaplaEventBus implements ApplicationEventBus, CalendarEventBus, DisposableBean
{
    private final CommandScheduler scheduler;
    private volatile Subject<ApplicationEvent> applicationEventPublishSubject;
    private volatile Subject<CalendarRefreshEvent> calendarRefreshEventPublishSubject;

    @Autowired
    public RaplaEventBus(CommandScheduler scheduler)
    {
        this.scheduler = scheduler;
        recreateSubjects();
    }

    private void recreateSubjects()
    {
        applicationEventPublishSubject = org.rapla.scheduler.Observables.createPublisher(scheduler.getExecutor());
        calendarRefreshEventPublishSubject = org.rapla.scheduler.Observables.createPublisher(scheduler.getExecutor());
    }

    @Override
    public synchronized void publish(ApplicationEvent event) {
        applicationEventPublishSubject.onNext(event);
    }

    @Override
    public synchronized void publish(CalendarRefreshEvent event) {
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

    /**
     * Logout hook: completes the existing subjects (well-behaved subscribers
     * auto-dispose on the terminal event, freeing the observer-list entries the
     * subjects retain) and creates fresh subjects for the next session.
     *
     * <p>Becomes redundant when PRD 052 Phase 2 lands (the whole context — and
     * with it this bean — is rebuilt per session, so the {@link #destroy()}
     * hook below fires instead).
     */
    public synchronized void reset()
    {
        try { applicationEventPublishSubject.onComplete(); } catch (Throwable ignored) {}
        try { calendarRefreshEventPublishSubject.onComplete(); } catch (Throwable ignored) {}
        recreateSubjects();
    }

    /** Fires when the Spring context closes (Phase 2 onwards). */
    @Override
    public synchronized void destroy()
    {
        try { applicationEventPublishSubject.onComplete(); } catch (Throwable ignored) {}
        try { calendarRefreshEventPublishSubject.onComplete(); } catch (Throwable ignored) {}
    }
}
