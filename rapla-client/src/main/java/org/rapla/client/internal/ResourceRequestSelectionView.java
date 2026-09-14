package org.rapla.client.internal;

import org.rapla.client.PopupContext;
import org.rapla.client.RaplaWidget;
import org.rapla.entities.domain.Reservation;

import java.util.Collection;

public interface ResourceRequestSelectionView<T> extends RaplaWidget<T>
{

    interface Presenter
    {

        void showTreePopup(PopupContext context);

        void showRequests(PopupContext context);

        void treeSelectionChanged();
    }
    
    void setPresenter(Presenter p);

    T getSummary();

    void updateTree(Collection<Reservation> selectedReservations, Collection<Reservation> reservations);

    Collection<Object> getSelectedElements(boolean withChilds);

    void redraw();

    void clearSelection();

    void showMenuPopup(PopupContext context, boolean enabledButtonEnabled, boolean disableButtonEnabled);
    
}
