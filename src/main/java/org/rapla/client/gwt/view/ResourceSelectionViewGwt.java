package org.rapla.client.gwt.view;

import org.rapla.client.internal.ResourceSelectionView;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.facade.ClassifiableFilter;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import java.util.Collection;

@DefaultImplementation(of=ResourceSelectionView.class,context = InjectionContext.gwt)
public class ResourceSelectionViewGwt implements ResourceSelectionView
{

    @Inject
    public ResourceSelectionViewGwt()
    {

    }

    @Override public void update(ClassificationFilter[] filter, ClassifiableFilter model, Collection<Object> selectedObjects)
    {

    }

    @Override public void updateMenu(Collection<?> list, Object focusedObject) throws RaplaException
    {

    }

    @Override public boolean hasFocus()
    {
        return false;
    }


    @Override public void closeFilterButton()
    {

    }

    @Override public void setPresenter(Presenter presenter)
    {

    }

    @Override public Object getComponent()
    {
        return null;
    }
}
