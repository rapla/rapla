package org.rapla.client.swing.internal.common;

import org.rapla.client.internal.RaplaClipboard;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.facade.client.ClientFacade;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.function.Supplier;
import java.awt.datatransfer.StringSelection;
import java.security.AccessControlException;

@org.springframework.stereotype.Service
@org.springframework.context.annotation.Lazy
public class RaplaSwingClipboard extends RaplaClipboard
{

    Supplier<IOInterface> serviceProvider;
    @Autowired
    public RaplaSwingClipboard(ClientFacade facade, Supplier<IOInterface> serviceProvider)
    {
        super(facade);
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
