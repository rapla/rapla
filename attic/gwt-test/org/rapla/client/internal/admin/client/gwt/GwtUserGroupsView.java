package org.rapla.client.internal.admin.client.gwt;

import io.reactivex.functions.BiFunction;
import org.rapla.client.RaplaWidget;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.client.internal.admin.client.AdminUserUserGroupsView;
import org.rapla.scheduler.Promise;

import javax.inject.Inject;

@DefaultImplementation(of=AdminUserUserGroupsView.class,context = InjectionContext.gwt)
public class GwtUserGroupsView implements AdminUserUserGroupsView {
    @Inject
    public GwtUserGroupsView()
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
