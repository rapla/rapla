package org.rapla.client.internal.edit;

import org.rapla.entities.Entity;
import org.rapla.framework.RaplaException;

import java.util.Map;

public interface EditTaskViewFactory<C>
{
    <T  extends Entity> EditTaskPresenter.EditTaskView<T,C> create(Map<T, T> toEdit, boolean isMerge) throws RaplaException;
}
