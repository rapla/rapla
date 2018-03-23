package org.rapla.plugin.copyurl.gwt;

import org.rapla.components.iolayer.IOInterface;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.copyurl.URLCopyService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.security.AccessControlException;

@DefaultImplementation(of=URLCopyService.class,context = InjectionContext.gwt)
@Singleton
public class GwtURLCopyService implements URLCopyService
{

    @Inject
    public GwtURLCopyService()
    {

    }

    @Override
    public void copy(String link)
    {
        throw new UnsupportedOperationException();
    }

}
