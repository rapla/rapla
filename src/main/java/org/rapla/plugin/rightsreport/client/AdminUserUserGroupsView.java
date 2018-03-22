package org.rapla.plugin.rightsreport.client;

import io.reactivex.functions.Action;
import io.reactivex.functions.BiFunction;
import org.rapla.client.RaplaWidget;
import org.rapla.scheduler.Promise;


public interface AdminUserUserGroupsView  extends  RaplaWidget {
    Promise<RaplaWidget> init(BiFunction<Object, Object, Promise<Void>> moveFunction, Runnable closeCmd);
    void updateView();
}
