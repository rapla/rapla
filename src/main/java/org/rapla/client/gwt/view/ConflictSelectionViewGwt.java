package org.rapla.client.gwt.view;

import com.google.gwt.user.client.ui.IsWidget;
import org.rapla.client.PopupContext;
import org.rapla.client.internal.ConflictSelectionView;
import org.rapla.facade.Conflict;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import java.util.Collection;

@DefaultImplementation(context = InjectionContext.gwt, of = ConflictSelectionView.class)
public class ConflictSelectionViewGwt implements ConflictSelectionView<IsWidget>
{
    @Inject
    public ConflictSelectionViewGwt()
    {
    }

    @Override public void setPresenter(Presenter p)
    {

    }

    @Override public IsWidget getSummary()
    {
        return null;
    }

    @Override public void updateTree(Collection<Conflict> selectedConflicts, Collection<Conflict> conflicts)
    {

    }

    @Override public Collection<Object> getSelectedElements(boolean withChilds)
    {
        return null;
    }

    @Override public void redraw()
    {

    }

    @Override public void clearSelection()
    {

    }

    @Override public void showMenuPopup(PopupContext context, boolean enabledButtonEnabled, boolean disableButtonEnabled)
    {

    }

    @Override public IsWidget getComponent()
    {
        return null;
    }
}
