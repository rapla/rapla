package org.rapla.client.gwt.view;

import com.google.gwt.user.client.ui.IsWidget;
import org.rapla.client.PopupContext;
import org.rapla.client.RaplaTreeNode;
import org.rapla.client.TreeFactory;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.gwt.RaplaVue;
import org.rapla.client.gwt.VuePopupContext;
import org.rapla.client.internal.ConflictSelectionView;
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Collections;

@DefaultImplementation(context = InjectionContext.gwt, of = ConflictSelectionView.class)
public class ConflictSelectionViewGwt implements ConflictSelectionView<IsWidget>
{

    private final TreeFactory treeFactory;
    private final DialogUiFactoryInterface dialogUiFactory;

    @Inject
    public ConflictSelectionViewGwt(TreeFactory treeFactory, DialogUiFactoryInterface dialogUiFactory)
    {
        this.treeFactory = treeFactory;
        this.dialogUiFactory = dialogUiFactory;
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
        try {
          final RaplaTreeNode conflictModel = treeFactory.createConflictModel(conflicts);
          RaplaVue.emit("update-main-conflicts", conflictModel);
        } catch (RaplaException e) {
            dialogUiFactory.showException(e, new VuePopupContext());
        }
    }

    @Override public Collection<Object> getSelectedElements(boolean withChilds)
    {
        return Collections.emptyList();
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
