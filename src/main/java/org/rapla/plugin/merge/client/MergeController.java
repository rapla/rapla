package org.rapla.plugin.merge.client;

import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.merge.client.extensionpoints.MergeCheckExtension;
import org.rapla.plugin.merge.client.swing.MergeDialog;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.Set;

@Singleton
public class MergeController
{

    private final MergeDialog.MergeDialogFactory mergeDialogFactory;
    private final ClientFacade clientFacade;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final Set<MergeCheckExtension> checkers;

    @Inject
    public MergeController(MergeDialog.MergeDialogFactory mergeDialogFactory, final ClientFacade clientFacade, final DialogUiFactoryInterface dialogUiFactory,
            final Set<MergeCheckExtension> checkers)
    {
        this.mergeDialogFactory = mergeDialogFactory;
        this.clientFacade = clientFacade;
        this.dialogUiFactory = dialogUiFactory;
        this.checkers = checkers;
    }

    public <T extends Allocatable> void startMerge(Collection<T> entities)
    {
        try
        {
            for (MergeCheckExtension mergeCheckExtension : checkers)
            {
                mergeCheckExtension.precheckAllocatableSelection(entities);
            }
            String title = "merge";
//            final Component mainComponent = null;
//            final SwingPopupContext popupContext = new SwingPopupContext(mainComponent, null);
//            final MergeDialog<T> mergeDialog = mergeDialogFactory.create(this);
//            mergeDialog.start(entities, title, popupContext);
        }
        catch (RaplaException e)
        {
            dialogUiFactory.showWarning(e.getMessage(), null);
        }
    }

    public void doMerge(Allocatable selectedObject, Set<ReferenceInfo<Allocatable>> allocatableIds)
    {
        try
        {
            allocatableIds.remove(selectedObject.getReference());
            final RaplaFacade raplaFacade = clientFacade.getRaplaFacade();
            final User user = clientFacade.getUser();
            raplaFacade.doMerge(selectedObject, allocatableIds, user);
        }
        catch (RaplaException e)
        {
            dialogUiFactory.showWarning(e.getMessage(), null);
        }
    }

}
