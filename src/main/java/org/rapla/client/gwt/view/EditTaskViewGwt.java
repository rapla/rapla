package org.rapla.client.gwt.view;

import io.reactivex.functions.Consumer;
import org.rapla.client.RaplaWidget;
import org.rapla.client.internal.edit.EditTaskPresenter;
import org.rapla.entities.Entity;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import java.util.Collection;

@DefaultImplementation(of= EditTaskPresenter.EditTaskViewFactory.class,context = InjectionContext.gwt)
public class EditTaskViewGwt implements EditTaskPresenter.EditTaskViewFactory
{
    @Inject
    public EditTaskViewGwt()
    {

    }


    @Override
    public EditTaskPresenter.EditTaskView create(Collection toEdit, boolean isMerge) throws RaplaException {
        return null;
    }
}
