package org.rapla.client.internal.admin.client;

import org.rapla.scheduler.BiFunction;
import org.rapla.client.RaplaWidget;
import org.rapla.scheduler.Promise;


public interface TypeCategoryView extends  RaplaWidget {
    Promise<RaplaWidget> init(BiFunction<Object, Object, Promise<Void>> moveFunction, Runnable closeCmd);
    void updateView();
}
