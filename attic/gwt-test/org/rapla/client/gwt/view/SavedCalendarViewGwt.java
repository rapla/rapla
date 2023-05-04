package org.rapla.client.gwt.view;

import org.rapla.client.internal.SavedCalendarInterface;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;

@DefaultImplementation(of=SavedCalendarInterface.class,context = InjectionContext.gwt)
public class SavedCalendarViewGwt implements SavedCalendarInterface
{
    @Inject
    public SavedCalendarViewGwt()
    {

    }

    @Override public void update() throws RaplaException
    {

    }

    @Override public Object getComponent()
    {
        return null;
    }
}
