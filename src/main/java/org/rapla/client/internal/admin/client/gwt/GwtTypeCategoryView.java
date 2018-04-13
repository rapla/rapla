package org.rapla.client.internal.admin.client.gwt;

import io.reactivex.functions.BiFunction;
import org.rapla.client.RaplaWidget;
import org.rapla.client.internal.admin.client.AdminUserUserGroupsView;
import org.rapla.client.internal.admin.client.TypeCategoryView;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.scheduler.Promise;

import javax.inject.Inject;

@DefaultImplementation(of=TypeCategoryView.class,context = InjectionContext.gwt)
public class GwtTypeCategoryView implements TypeCategoryView {

    @Inject
    public GwtTypeCategoryView()
    {

    }
    @Override
    public Promise<RaplaWidget> init(BiFunction<Object, Object, Promise<Void>> moveFunction, Runnable closeCmd) {
        return null;
    }

    @Override
    public void updateView() {

    }

    @Override
    public Object getComponent() {
        return null;
    }
}
