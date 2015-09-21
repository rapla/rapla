package org.rapla.gui.internal.common;

import java.awt.datatransfer.StringSelection;
import java.security.AccessControlException;

import javax.inject.Inject;
import javax.inject.Provider;

import org.rapla.components.iolayer.IOInterface;
import org.rapla.facade.ClientFacade;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

@DefaultImplementation(of=RaplaClipboard.class,context = InjectionContext.swing)
public class RaplaSwingClipboard extends RaplaClipboard
{

    Provider<IOInterface> serviceProvider;
    @Inject
    public RaplaSwingClipboard(ClientFacade facade, Provider<IOInterface> serviceProvider)
    {
        super(facade);
        this.serviceProvider = serviceProvider;;
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
