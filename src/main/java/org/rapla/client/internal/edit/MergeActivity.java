package org.rapla.client.internal.edit;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.rapla.client.event.Action;
import org.rapla.client.event.ActionPresenter;
import org.rapla.client.swing.toolkit.RaplaWidget;
import org.rapla.facade.ModificationEvent;
import org.rapla.inject.Extension;
import org.rapla.plugin.merge.client.MergeController;

@Singleton
@Extension(id=MergeActivity.ID,provides = ActionPresenter.class)
public class MergeActivity implements ActionPresenter
{
    final static public String ID = "merge";

    private final MergeController controller;
    @Inject
    public MergeActivity(MergeController mergeController)
    {
        this.controller = mergeController;
    }

    public <T> RaplaWidget<T> startActivity(Action activity)
    {
        return null;
    }

    @Override public void updateView(ModificationEvent event)
    {

    }
}
