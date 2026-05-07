package org.rapla.client.dialog;

import org.rapla.client.RaplaWidget;
import org.rapla.scheduler.Observable;

import java.util.Collection;

public interface ListView<T> extends RaplaWidget
{
    void setObjects(Collection<T> object);
    T getSelected();
    Observable<T> doubleClicked();
    Observable<Collection<T>> selectionChanged();
    void setSelected(T object);
}
