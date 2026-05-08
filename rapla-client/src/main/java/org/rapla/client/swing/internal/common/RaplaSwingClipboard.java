package org.rapla.client.swing.internal.common;

import org.rapla.client.internal.RaplaClipboard;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.facade.client.ClientFacade;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import java.awt.datatransfer.StringSelection;
import java.security.AccessControlException;

@Singleton
@org.springframework.stereotype.Service
@org.springframework.context.annotation.Lazy
public class RaplaSwingClipboard extends RaplaClipboard
{

    Provider<IOInterface> serviceProvider;
    @Autowired
    public RaplaSwingClipboard(ClientFacade facade, Provider<IOInterface> serviceProvider, Logger logger)
    {
        super(facade, logger);
        this.serviceProvider = serviceProvider;
    }

    @Override
    public void copyToSystemClipboard(String content)
    {
        try
        {
            IOInterface service ;
            try{
                service = serviceProvider.get();
            } catch (Exception e) {
                service = null;
            }
            if (service != null) {
                StringSelection transferable = new StringSelection(content);
                service.setContents(transferable, null);
            } 
        }
        catch (AccessControlException ex)
        {
        }   
    }

}
