package org.rapla.client.event;

import org.rapla.client.RaplaWidget;
import org.rapla.facade.ModificationEvent;
import org.rapla.scheduler.Observable;
import org.rapla.scheduler.Promise;


public interface TaskPresenter
{
    <T> Promise<RaplaWidget> startActivity(ApplicationEvent activity);
    void updateView(ModificationEvent event);
    String getTitle(ApplicationEvent activity);
    Observable<String> getBusyIdleObservable();
    Promise<Void> processStop(ApplicationEvent event);
}
