package org.rapla.plugin.merge.client.swing;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.rapla.RaplaResources;
import org.rapla.client.ReservationController;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.EditComponent;
import org.rapla.client.swing.EditController;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.edit.EditDialog;
import org.rapla.entities.Entity;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;

public class MergeDialog<T extends Entity> extends EditDialog<T>
{

    public MergeDialog(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, Map<String, Provider<EditComponent>> editUiProvider,
            EditController editController, ReservationController reservationController, RaplaImages raplaImages, DialogUiFactoryInterface dialogUiFactory)
    {
        super(facade, i18n, raplaLocale, logger, editUiProvider, editController, reservationController, raplaImages, dialogUiFactory);
    }

    @Singleton
    public static class MergeDialogFactory
    {
        private final ClientFacade facade;
        private final RaplaResources i18n;
        private final RaplaLocale raplaLocale;
        private final Logger logger;
        private final Map<String, Provider<EditComponent>> editUiProvider;
        private final ReservationController reservationController;
        private final RaplaImages raplaImages;
        private final DialogUiFactoryInterface dialogUiFactory;

        @SuppressWarnings("rawtypes")
        @Inject
        public MergeDialogFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,
                Map<String, Provider<EditComponent>> editUiProvider, ReservationController reservationController, RaplaImages raplaImages,
                DialogUiFactoryInterface dialogUiFactory)
        {
            this.facade = facade;
            this.i18n = i18n;
            this.raplaLocale = raplaLocale;
            this.logger = logger;
            this.editUiProvider = editUiProvider;
            this.reservationController = reservationController;
            this.raplaImages = raplaImages;
            this.dialogUiFactory = dialogUiFactory;

        }

        public MergeDialog create(EditController editController)
        {
            return new MergeDialog(facade, i18n, raplaLocale, logger, editUiProvider, editController, reservationController, raplaImages, dialogUiFactory);
        }
    }

}
