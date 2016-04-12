package org.rapla.client.event;

import org.rapla.client.swing.toolkit.RaplaWidget;
import org.rapla.facade.ModificationEvent;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

@ExtensionPoint(context={ InjectionContext.client},id = "activity")
public interface ActionPresenter
{
    <T> RaplaWidget<T> startActivity(Action activity);
    void updateView(ModificationEvent event);
}
