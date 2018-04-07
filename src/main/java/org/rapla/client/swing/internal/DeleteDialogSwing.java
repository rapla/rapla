package org.rapla.client.swing.internal;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DeleteDialogInterface;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.dialog.InfoFactory;
import org.rapla.client.internal.DeleteInfoUI;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.internal.view.ViewTable;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;

import javax.inject.Inject;

@DefaultImplementation(of=DeleteDialogInterface.class,context = InjectionContext.swing)
public class DeleteDialogSwing extends RaplaGUIComponent implements DeleteDialogInterface {

    final private DialogUiFactoryInterface dialogUiFactory;
    final private IOInterface ioInterface;
    final private InfoFactory infoFactory;

    @Inject
    public DeleteDialogSwing(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, DialogUiFactoryInterface dialogUiFactory, IOInterface ioInterface, InfoFactory infoFactory) {
        super(facade, i18n, raplaLocale, logger);
        this.dialogUiFactory = dialogUiFactory;
        this.ioInterface = ioInterface;
        this.infoFactory = infoFactory;
    }

    @Override
    public Promise<Boolean> showDeleteDialog(PopupContext context, Object[] deletables) {
        DialogInterface dlg ;
        try {
            dlg = createDeleteDialog(deletables, context);
        } catch (RaplaException ex) {
            return new ResolvedPromise<>(ex);
        }
        return dlg.start(true).thenApply(result->result== 0 ? Boolean.TRUE : Boolean.FALSE);

    }

    /* (non-Javadoc)
     * @see org.rapla.client.swing.gui.view.IInfoUIFactory#createDeleteDialog(java.lang.Object[], java.awt.ServerComponent)
     */
    public DialogInterface createDeleteDialog( Object[] deletables, PopupContext popupContext ) throws RaplaException {
        if ( popupContext == null)
        {
            popupContext = dialogUiFactory.createPopupContext( null);
        }

        ViewTable<Object[]> viewTable = new ViewTable<>(getClientFacade(), getI18n(), getRaplaLocale(), getLogger(), infoFactory, ioInterface, dialogUiFactory);
        DeleteInfoUI deleteView = new DeleteInfoUI(getI18n(), getRaplaLocale(), getFacade(), getLogger());
        DialogInterface dlg = dialogUiFactory.createContentDialog(popupContext
                ,
                viewTable.getComponent()
                ,new String[] {
                        getString( "delete.ok" )
                        ,getString( "delete.abort" )
                });
        dlg.setIcon( i18n.getIcon("icon.warning" ));
        dlg.getAction(0).setIcon(i18n.getIcon("icon.delete"));
        dlg.getAction(1).setIcon(i18n.getIcon("icon.abort"));
        dlg.setDefault(1);
        viewTable.updateInfo( deletables, deleteView );
        dlg.setTitle( viewTable.getDialogTitle() );
        return dlg;
    }
}
