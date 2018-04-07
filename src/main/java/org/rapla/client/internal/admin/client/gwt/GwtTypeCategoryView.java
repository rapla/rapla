package org.rapla.client.internal.admin.client.gwt;

import io.reactivex.functions.BiFunction;
import org.rapla.client.RaplaWidget;
import org.rapla.client.internal.admin.client.AdminUserUserGroupsView;
import org.rapla.client.internal.admin.client.TypeCategoryView;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.scheduler.Promise;

@DefaultImplementation(of=TypeCategoryView.class,context = InjectionContext.gwt)
public class GwtTypeCategoryView implements TypeCategoryView {
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
