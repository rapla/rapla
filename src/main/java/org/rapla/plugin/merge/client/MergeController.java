package org.rapla.plugin.merge.client;

import java.awt.Component;
import java.util.Collection;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaFacade;
import org.rapla.plugin.merge.client.swing.MergeDialog;
import org.rapla.plugin.merge.client.swing.MergeDialog.MergeDialogFactory;

@Singleton
public class MergeController
{

    private final MergeDialogFactory mergeDialogFactory;
    private final ClientFacade clientFacade;

    @Inject
    public MergeController(MergeDialogFactory mergeDialogFactory, final ClientFacade clientFacade)
    {
        this.mergeDialogFactory = mergeDialogFactory;
        this.clientFacade = clientFacade;
    }

    public <T extends Allocatable> void startMerge(Collection<T> entities)
    {
        String title = "merge";
        final Component mainComponent = null;
        final SwingPopupContext popupContext = new SwingPopupContext(mainComponent, null);
        final MergeDialog<T> mergeDialog = mergeDialogFactory.create(this);
        mergeDialog.start(entities, title, popupContext, false, null);
    }

    public void doMerge(Allocatable selectedObject, Set<ReferenceInfo<Allocatable>> allocatableIds)
    {
        allocatableIds.remove(selectedObject.getReference());
        final RaplaFacade raplaFacade = clientFacade.getRaplaFacade();
        final User user = clientFacade.getUser();
        raplaFacade.doMerge(selectedObject, allocatableIds, user);
    }

}
