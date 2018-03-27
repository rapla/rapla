package org.rapla.client.internal;

import org.rapla.client.PopupContext;
import org.rapla.client.RaplaWidget;
import org.rapla.facade.Conflict;

import java.util.Collection;

public interface ConflictSelectionView<T> extends RaplaWidget<T>
{

    interface Presenter
    {

        void showTreePopup(PopupContext context);

        void showConflicts(PopupContext context);
        
        void enableConflicts(PopupContext context);
        
        void disableConflicts(PopupContext context);
        
    }
    
    void setPresenter(Presenter p);

    T getSummary();

    void updateTree(Collection<Conflict> selectedConflicts, Collection<Conflict> conflicts);

    Collection<Object> getSelectedElements(boolean withChilds);

    void redraw();

    void clearSelection();

    void showMenuPopup(PopupContext context, boolean enabledButtonEnabled, boolean disableButtonEnabled);
    
}
