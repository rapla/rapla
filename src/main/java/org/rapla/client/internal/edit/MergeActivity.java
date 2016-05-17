package org.rapla.client.internal.edit;

import java.util.ArrayList;
import java.util.Collection;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.rapla.client.EditApplicationEventContext;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.event.ApplicationEvent.ApplicationEventContext;
import org.rapla.client.event.TaskPresenter;
import org.rapla.client.RaplaWidget;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.RaplaFacade;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;
import org.rapla.plugin.merge.client.MergeController;
import org.rapla.scheduler.Promise;

@Singleton
@Extension(id = MergeActivity.ID, provides = TaskPresenter.class)
public class MergeActivity implements TaskPresenter
{
    final static public String ID = "merge";

    private final MergeController controller;

    private final Logger logger;

    private final RaplaFacade raplaFacade;

    @Inject
    public MergeActivity(MergeController mergeController, Logger logger, RaplaFacade raplaFacade)
    {
        this.controller = mergeController;
        this.logger = logger;
        this.raplaFacade = raplaFacade;
    }

    public <T> Promise<RaplaWidget> startActivity(ApplicationEvent activity)
    {
        final String applicationEventId = activity.getApplicationEventId();
        if (ID.equals(applicationEventId))
        {
            if (activity.isStop())
            {
                // TODO how to stop
            }
            else
            {
                final ApplicationEventContext context = activity.getContext();
                Collection<Allocatable> entities = new ArrayList<>();
                if (context != null && context instanceof EditApplicationEventContext)
                {
                    entities.addAll(((EditApplicationEventContext) context).getSelectedObjects());
                }
                else
                {
                    final String info = activity.getInfo();
                    if (info == null || info.isEmpty())
                    {
                        logger.warn("no info sent for merge " + info);
                    }
                    final String[] split = info.split(",");
                    for (String id : split)
                    {
                        final Allocatable resolve = raplaFacade.tryResolve(new ReferenceInfo<Allocatable>(id, Allocatable.class));
                        if(resolve != null)
                        {
                            entities.add(resolve);
                        }
                        else
                        {
                            logger.warn("");
                        }
                    }

                }
                // FIXME return widget
                controller.startMerge(entities);
            }
        }
        return null;
    }

    @Override
    public void updateView(ModificationEvent event)
    {

    }
}
