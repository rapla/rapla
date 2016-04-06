package org.rapla.client.internal.edit;

import org.rapla.client.event.Activity;
import org.rapla.client.event.ActivityPresenter;
import org.rapla.client.swing.toolkit.RaplaWidget;
import org.rapla.facade.ModificationEvent;
import org.rapla.inject.Extension;
import org.rapla.plugin.merge.client.MergeController;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@Extension(id=MergeActivity.ID,provides = ActivityPresenter.class)
public class MergeActivity implements ActivityPresenter
{
    final static public String ID = "merge";

    private final MergeController controller;
    @Inject
    public MergeActivity(MergeController mergeController)
    {
        this.controller = mergeController;
    }

    public <T> RaplaWidget<T> startActivity(Activity activity)
    {
        return null;
    }

    @Override public void updateView(ModificationEvent event)
    {

    }
}
