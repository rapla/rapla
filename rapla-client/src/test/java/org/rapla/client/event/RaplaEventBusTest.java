package org.rapla.client.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.rapla.client.swing.SwingSchedulerImpl;
import org.rapla.logger.ConsoleLogger;
import org.rapla.scheduler.CommandScheduler;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

class RaplaEventBusTest
{
    private RaplaEventBus newBus()
    {
        CommandScheduler scheduler = new SwingSchedulerImpl(new ConsoleLogger());
        return new RaplaEventBus(scheduler);
    }

    /** Capture-only Subscriber — we only need onNext + onComplete signals for these assertions. */
    static final class CapturingSubscriber<T> implements Subscriber<T>
    {
        final AtomicInteger received = new AtomicInteger();
        final AtomicBoolean completed = new AtomicBoolean();
        final AtomicReference<T> lastEvent = new AtomicReference<>();

        @Override public void onSubscribe(Subscription s) { s.request(Long.MAX_VALUE); }
        @Override public void onNext(T t) { received.incrementAndGet(); lastEvent.set(t); }
        @Override public void onError(Throwable t) {}
        @Override public void onComplete() { completed.set(true); }
    }

    @Test
    void publishedEventReachesSubscriber()
    {
        RaplaEventBus bus = newBus();
        CapturingSubscriber<ApplicationEvent> sub = new CapturingSubscriber<>();
        bus.getApplicationEventObservable().subscribe(sub);

        ApplicationEvent event = new ApplicationEvent("t", "info", null, null);
        bus.publish(event);

        assertEquals(1, sub.received.get());
        assertEquals(event, sub.lastEvent.get());
    }

    @Test
    void resetCompletesExistingSubscribersAndIssuesFreshSubjects()
    {
        RaplaEventBus bus = newBus();
        CapturingSubscriber<ApplicationEvent> first = new CapturingSubscriber<>();

        // Capture the subject instance before reset so we can confirm it's replaced.
        Object subjectBefore = bus.getApplicationEventObservable();
        bus.getApplicationEventObservable().subscribe(first);

        bus.reset();

        assertTrue(first.completed.get(), "Existing subscriber should receive onComplete on reset");
        Object subjectAfter = bus.getApplicationEventObservable();
        assertNotSame(subjectBefore, subjectAfter, "reset() must hand out a fresh Observable to next session");

        // Subscribe again post-reset and confirm events still flow.
        CapturingSubscriber<ApplicationEvent> second = new CapturingSubscriber<>();
        bus.getApplicationEventObservable().subscribe(second);
        ApplicationEvent event = new ApplicationEvent("t2", "info", null, null);
        bus.publish(event);

        assertEquals(1, second.received.get(), "Subscriber on the post-reset bus should receive freshly-published events");
        assertEquals(event, second.lastEvent.get());
        assertEquals(0, first.received.get(), "Subscriber on the pre-reset bus should NOT receive events from the new subject");
    }

    @Test
    void destroyCompletesSubscribersWithoutRecreatingSubjects()
    {
        RaplaEventBus bus = newBus();
        CapturingSubscriber<ApplicationEvent> sub = new CapturingSubscriber<>();
        bus.getApplicationEventObservable().subscribe(sub);

        bus.destroy();

        assertTrue(sub.completed.get(), "DisposableBean.destroy() should complete the subject");
    }
}
