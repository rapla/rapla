package org.rapla.plugin.exchangeconnector.client.swing;

import java.awt.BorderLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorResources;

public class SyncResultDialog extends RaplaGUIComponent
{
    private final DialogUiFactoryInterface dialogUiFactory;
    private final ExchangeConnectorResources exchangeConnectorResources;

    public SyncResultDialog(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, ExchangeConnectorResources exchangeConnectorResources, DialogUiFactoryInterface dialogUiFactory) {
        super(facade, i18n, raplaLocale, logger);
        this.exchangeConnectorResources = exchangeConnectorResources;
//        setChildBundleName(exchangeConnectorResources);
        this.dialogUiFactory = dialogUiFactory;
    }

    public void showResultDialog() throws RaplaException {
        String title = exchangeConnectorResources.getString("synchronization") + " " + getString("appointment");
        JPanel content = new JPanel();
        content.setLayout( new BorderLayout() );
        final JLabel info = new JLabel(exchangeConnectorResources.getString("exchange.sync.mail"));
        content.add( info, BorderLayout.CENTER);
        DialogInterface dialog = dialogUiFactory.create(new SwingPopupContext(getMainComponent(), null),false, content, new String[] {getString("close")});
        dialog.setTitle( title);
        dialog.start(true);
    }
}