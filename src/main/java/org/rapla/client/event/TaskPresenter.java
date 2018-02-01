package org.rapla.client.event;

import org.rapla.client.RaplaWidget;
import org.rapla.facade.ModificationEvent;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;
import org.rapla.scheduler.Observable;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.Subject;
import org.reactivestreams.Subscriber;

@ExtensionPoint(context={ InjectionContext.client},id = "activity")
public interface TaskPresenter
{
    <T> Promise<RaplaWidget> startActivity(ApplicationEvent activity);
    void updateView(ModificationEvent event);
    String getTitle(ApplicationEvent activity);
    Observable<String> getBusyIdleObservable();
    Promise<Void> processStop(ApplicationEvent event, RaplaWidget widget);
}
