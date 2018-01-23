package org.rapla.client.swing.internal.common;

import org.rapla.client.internal.RaplaClipboard;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.facade.client.ClientFacade;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.awt.datatransfer.StringSelection;
import java.security.AccessControlException;

@DefaultImplementation(of=RaplaClipboard.class,context = InjectionContext.swing)
@Singleton
public class RaplaSwingClipboard extends RaplaClipboard
{

    Provider<IOInterface> serviceProvider;
    @Inject
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
