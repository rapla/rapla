package org.rapla.plugin.exchangeconnector.client;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import java.util.TreeSet;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.rapla.RaplaResources;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.toolkit.DialogUI;
import org.rapla.client.swing.toolkit.DialogUI.DialogUiFactory;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorResources;
import org.rapla.plugin.exchangeconnector.SyncError;
import org.rapla.plugin.exchangeconnector.SynchronizationStatus;
import org.rapla.plugin.exchangeconnector.SynchronizeResult;

public class SyncResultDialog extends RaplaGUIComponent
{
    private final DialogUiFactory dialogUiFactory;
    private final ExchangeConnectorResources exchangeConnectorResources;

    public SyncResultDialog(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, ExchangeConnectorResources exchangeConnectorResources, DialogUiFactory dialogUiFactory) {
        super(facade, i18n, raplaLocale, logger);
        this.exchangeConnectorResources = exchangeConnectorResources;
//        setChildBundleName(exchangeConnectorResources);
        this.dialogUiFactory = dialogUiFactory;
    }

    public void showResultDialog(SynchronizeResult result) throws RaplaException {
        String header = "Aktualisiert: "   + result.changed + " Geloescht: " + result.removed + " Fehler: " + result.open ;
        List<SyncError> errorMessages = result.errorMessages;
        showResultDialog(header, errorMessages);
    }

    public void showResultDialog(SynchronizationStatus result) throws RaplaException {
        List<SyncError> errorMessages = result.synchronizationErrors;
        String header = "Synchronisierungsfehler: "   + errorMessages.size() ;
        showResultDialog(header, errorMessages);
    }

    private void showResultDialog(String header, List<SyncError> errorMessages) throws RaplaException {
        String title = exchangeConnectorResources.getString("synchronization") + " " + getString("appointment");
        JPanel content = new JPanel();
        content.setLayout( new BorderLayout() );
        JTextArea area = new JTextArea();
        area.setColumns( 30);
        area.setEditable( false);
        area.setLineWrap( true );
        JScrollPane pane = new JScrollPane(area);
        StringBuilder text = new StringBuilder();
        if ( errorMessages.isEmpty())
        {
            text.append("Keine Synchronisierungsfehler");
        }
        else
        {
            text.append("Fehlermeldungen: \n");
            TreeSet<String> set = new TreeSet<String>();
            // Group errors
            for ( SyncError error:errorMessages)
            {
                set.add( error.getErrorMessage());
            }
            for ( String error:set)
            {
                text.append( error );
                text.append( "\n");
            }
            text.append("\nStimmen ihre Exchange-Zugangsdaten noch?\nDie Passworteinstellungen finden Sie unter Bearbeiten/Einstellungen/Exchange");
            
            text.append("\n\nUnsynchronisierte Termine: \n");
            for ( SyncError error:errorMessages)
            {
                String appointmentDetail = error.getAppointmentDetail();
                if ( appointmentDetail != null && !appointmentDetail.isEmpty())
                {
                    text.append( "- " + appointmentDetail + "\n");
                }
            }
        }
        area.setText( text.toString());
        pane.setPreferredSize( new Dimension(500,300));
        content.add( new JLabel(header), BorderLayout.NORTH);
        content.add( pane, BorderLayout.CENTER);
        DialogUI dialog = dialogUiFactory.create(getMainComponent(),false, content, new String[] {getString("close")});
        dialog.setTitle( title);
        dialog.start();
    }
}