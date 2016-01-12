package org.rapla.plugin.exchangeconnector.client;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.ClientExtension;
import org.rapla.components.util.Command;
import org.rapla.components.util.CommandScheduler;
import org.rapla.components.util.ParseDateException;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.Extension;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorPlugin;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorRemote;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorResources;
import org.rapla.plugin.exchangeconnector.SynchronizationStatus;

import javax.inject.Inject;
import java.util.Date;

@Extension(id=ExchangeConnectorPlugin.PLUGIN_ID, provides=ClientExtension.class)
public class ExchangeClientError extends RaplaComponent implements ClientExtension, ModificationListener 
{
    CommandScheduler scheduler;
    Date changedDate;
    ExchangeConnectorRemote remote;
    
    TypedComponentRole<String> LAST_SYNC_ERROR_SHOWN = new TypedComponentRole<String>("org.rapla.plugin.exchangconnector.last_sync_error_shown");
    private final ExchangeConnectorResources exchangeConnectorResources;
    private final DialogUiFactoryInterface dialogUiFactory;
    
    
    @Inject
    public ExchangeClientError(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, final ExchangeConnectorRemote remote, final CommandScheduler scheduler, ExchangeConnectorResources exchangeConnectorResources, DialogUiFactoryInterface dialogUiFactory) throws RaplaException {
        super(facade, i18n, raplaLocale, logger);
        this.remote = remote;
        this.exchangeConnectorResources = exchangeConnectorResources;
        this.dialogUiFactory = dialogUiFactory;
        getClientFacade().addModificationListener( this );
        // wait a bit to so we don't interfere with startup time
        if ( changed())
        {
      
            int initialDelay = 3000;
            scheduler.schedule(new Command() {
                
                @Override
                public void execute() throws Exception {
                    showNotificationDialogOnError();
                }
            }, initialDelay);
        }
    }
    
    @Override
    public void dataChanged(ModificationEvent evt) throws RaplaException {
        if (changed())
        {
            showNotificationDialogOnError();
        }
    }
    
    private void showNotificationDialogOnError() throws RaplaException {
        SynchronizationStatus synchronizationStatus = remote.getSynchronizationStatus();
        if ( synchronizationStatus.synchronizationErrors.size() > 0)
        {
            SyncResultDialog dlg = new SyncResultDialog( getClientFacade(), getI18n(), getRaplaLocale()
                    ,getLogger(), exchangeConnectorResources, dialogUiFactory);
            dlg.showResultDialog( synchronizationStatus);
        }
    }
    
    Date lastShown;

    public boolean changed() throws RaplaException
    {
        Preferences preferences = getQuery().getPreferences();
        Date lastChanged = parseDate(preferences, ExchangeConnectorRemote.LAST_SYNC_ERROR_CHANGE);
        Date lastShown = this.lastShown;//parseDate(preferences, LAST_SYNC_ERROR_SHOWN);
        if ( lastChanged != null && ( lastShown == null  || lastChanged.after(lastShown))) 
        {
            this.lastShown = getClientFacade().getOperator().getCurrentTimestamp();
            return true;
        }
        return false;
    }

    private Date parseDate(Preferences preferences, TypedComponentRole<String> entryName) throws RaplaException {
        Date date = null;
        String entry = preferences.getEntryAsString( entryName, null);
        if (entry != null)
        {
            try {
                date = getRaplaLocale().getSerializableFormat().parseTimestamp( entry);
            } catch (ParseDateException e) {
                throw new RaplaException( e.getMessage() , e);
            }
        }
        return date;
    }

    @Override
    public void start()
    {
        
    }   
    
}
