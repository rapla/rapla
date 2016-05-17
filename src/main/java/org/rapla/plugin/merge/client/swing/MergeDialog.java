package org.rapla.plugin.merge.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.ReservationController;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.EditComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.edit.AbstractDialog;
import org.rapla.client.swing.internal.edit.AllocatableMergeEditUI;
import org.rapla.entities.Entity;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.merge.client.MergeController;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MergeDialog<T extends Allocatable> extends AbstractDialog<T>
{
    private final Provider<AllocatableMergeEditUI> editUiProvider;
    private final MergeController mergeController;

    public MergeDialog(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, ReservationController reservationController,
            RaplaImages raplaImages, DialogUiFactoryInterface dialogUiFactory, Provider<AllocatableMergeEditUI> editUiProvider, MergeController mergeController)
    {
        super(facade, i18n, raplaLocale, logger, dialogUiFactory);
        this.editUiProvider = editUiProvider;
        this.mergeController = mergeController;
    }

    @Override
    public void start(Collection<T> editObjects, String title, PopupContext popupContext) throws RaplaException
    {
        start(editObjects, getI18n().getString(title), popupContext, getString("merge"), getString("cancel"), new MergeAction());
        setObjects(new ArrayList<T>(editObjects));
    }

    protected <U extends Entity> org.rapla.client.swing.EditComponent<U, javax.swing.JComponent> createUI(U obj) throws RaplaException
    {
        return (EditComponent<U, JComponent>) editUiProvider.get();
    };

    @Override
    protected void cleanupAfterClose()
    {

    }

    private class MergeAction implements Runnable
    {
        @Override
        public void run()
        {
            final List<Allocatable> selectedObject = (List<Allocatable>) ui.getObjects();
            final Allocatable selectedAllocatable = selectedObject.get(0);
            final List<Allocatable> allAllocatables = ((AllocatableMergeEditUI)ui).getAllAllocatables();
            final Set<ReferenceInfo<Allocatable>> allocatableIds = new LinkedHashSet<>();
            for (Allocatable allocatable : allAllocatables)
            {
                allocatableIds.add(allocatable.getReference());
            }
            mergeController.doMerge(selectedAllocatable, allocatableIds);
            dlg.close();
            try
            {
                getClientFacade().getRaplaFacade().refresh();
            }
            catch (RaplaException e)
            {
                dialogUiFactory.showException(e, null);
            }
        }
    }

    @Singleton
    public static class MergeDialogFactory
    {
        private final ClientFacade facade;
        private final RaplaResources i18n;
        private final RaplaLocale raplaLocale;
        private final Logger logger;
        private final ReservationController reservationController;
        private final RaplaImages raplaImages;
        private final DialogUiFactoryInterface dialogUiFactory;
        private final Provider<AllocatableMergeEditUI> editUiProvider;

        @Inject
        public MergeDialogFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, ReservationController reservationController,
                RaplaImages raplaImages, DialogUiFactoryInterface dialogUiFactory, Provider<AllocatableMergeEditUI> editUiProvider)
        {
            this.facade = facade;
            this.i18n = i18n;
            this.raplaLocale = raplaLocale;
            this.logger = logger;
            this.reservationController = reservationController;
            this.raplaImages = raplaImages;
            this.dialogUiFactory = dialogUiFactory;
            this.editUiProvider = editUiProvider;
        }

        public <T extends Allocatable> MergeDialog<T> create(MergeController mergeController)
        {
            return new MergeDialog<T>(facade, i18n, raplaLocale, logger, reservationController, raplaImages, dialogUiFactory, editUiProvider, mergeController);
        }
    }
}
