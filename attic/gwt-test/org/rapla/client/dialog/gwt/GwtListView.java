package org.rapla.client.dialog.gwt;

import org.rapla.client.dialog.ListView;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.scheduler.Observable;

import javax.inject.Inject;
import java.util.Collection;

@DefaultImplementation(of=ListView.class,context = InjectionContext.gwt)
public class GwtListView implements ListView<Object>
{
    @Inject
    public GwtListView()
    {

    }
    @Override
    public void setObjects(Collection object)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getSelected()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Observable doubleClicked()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Observable<Collection<Object>> selectionChanged()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setSelected(Object object)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getComponent()
    {
        throw new UnsupportedOperationException();
    }
}
