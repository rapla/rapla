package org.rapla.client.internal.admin.client;

import io.reactivex.rxjava3.functions.BiFunction;
import org.rapla.client.RaplaWidget;
import org.rapla.scheduler.Promise;


public interface AdminUserUserGroupsView  extends  RaplaWidget {
    Promise<RaplaWidget> init(BiFunction<Object, Object, Promise<Void>> moveFunction, Runnable closeCmd);
    void updateView();
}
