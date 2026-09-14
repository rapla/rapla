package org.rapla.client.event;

import org.rapla.scheduler.Observable;

public interface ApplicationEventBus {
    void publish(ApplicationEvent event);
    Observable<ApplicationEvent> getApplicationEventObservable();
}
