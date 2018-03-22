package org.rapla.client.gwt.view;

import org.rapla.client.internal.edit.EditTaskPresenter;
import org.rapla.client.internal.edit.EditTaskViewFactory;
import org.rapla.entities.Entity;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import java.util.Map;

@DefaultImplementation(of= EditTaskViewFactory.class,context = InjectionContext.gwt)
public class EditTaskViewGwt implements EditTaskViewFactory<Object>
{
    @Inject
    public EditTaskViewGwt()
    {

    }

    @Override
    public <T extends Entity> EditTaskPresenter.EditTaskView<T,Object> create(Map<T,T> editMap, boolean isMerge) throws RaplaException {
        return null;
    }
}
