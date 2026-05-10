package org.rapla.plugin.externaleventimport.client;

import org.rapla.client.RaplaWidget;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.event.TaskPresenter;
import org.rapla.facade.ModificationEvent;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Observable;
import org.rapla.scheduler.Observables;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

/**
 * Stub TaskPresenter for the import flow — kept for parity with the legacy
 * dhbw plumbing in case the wizard later spawns long-running background tasks.
 * Active only when the property gate is on.
 */
@Service(ExternalEventSyncTaskPresenter.ID)
@Conditional(ExternalEventImportEnabledCondition.class)
public class ExternalEventSyncTaskPresenter implements TaskPresenter
{
    public static final String ID = "org.rapla.client.externaleventimport.SyncTaskPresenter";

    private final Subject<String> publisher;

    @Autowired
    public ExternalEventSyncTaskPresenter(CommandScheduler scheduler)
    {
        this.publisher = Observables.createPublisher(scheduler.getExecutor());
    }

    @Override
    public <T> Promise<RaplaWidget> startActivity(ApplicationEvent activity)
    {
        return null;
    }

    @Override
    public void updateView(ModificationEvent event)
    {
    }

    @Override
    public String getTitle(ApplicationEvent activity)
    {
        return null;
    }

    @Override
    public Observable<String> getBusyIdleObservable()
    {
        return publisher;
    }

    @Override
    public Promise<Void> processStop(ApplicationEvent event)
    {
        return null;
    }
}
