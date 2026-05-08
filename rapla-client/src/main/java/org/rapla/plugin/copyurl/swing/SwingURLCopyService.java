package org.rapla.plugin.copyurl.swing;

import org.rapla.components.iolayer.IOInterface;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.copyurl.URLCopyService;

import org.springframework.beans.factory.annotation.Autowired;
import jakarta.inject.Singleton;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.security.AccessControlException;

@Singleton
@org.springframework.stereotype.Service
public class SwingURLCopyService implements URLCopyService
{
    private final IOInterface ioInterface;

    @Autowired
    public SwingURLCopyService(IOInterface ioInterface)
    {
        this.ioInterface = ioInterface;
    }

    @Override
    public void copy(String link)
    {
        Transferable transferable = new StringSelection(link);
        try
        {
            if (ioInterface != null)
            {
                ioInterface.setContents(transferable, null);
            }
        }
        catch (AccessControlException ex)
        {
            //   clipboard.set( transferable);
        }

    }

}
