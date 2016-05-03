package org.rapla.client.internal;

import java.util.Collection;

import org.rapla.client.MenuContext;
import org.rapla.client.PopupContext;
import org.rapla.client.swing.SwingMenuContext;
import org.rapla.client.swing.toolkit.RaplaPopupMenu;
import org.rapla.client.RaplaWidget;
import org.rapla.entities.Category;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.facade.ClassifiableFilter;

public interface ResourceSelectionView<T> extends RaplaWidget<T>
{
    public interface Presenter
    {

        boolean moveCategory(Category categoryToMove, Category targetCategory);

        void selectResource(Object focusedObject);

        void updateSelectedObjects(Collection<Object> elements);

        void mouseOverResourceSelection();

        void showTreePopup(PopupContext popupContext, Object selectedObject, MenuContext menuContext);

        void applyFilter();

        void treeSelectionChanged();

        void updateFilters(ClassificationFilter[] filters);
        
    }

    void update(ClassificationFilter[] filter, ClassifiableFilter model, Collection<Object> selectedObjects);

    boolean hasFocus();

    void showMenu(RaplaPopupMenu menu, SwingMenuContext swingMenuContext);

    void closeFilterButton();
    
    void setPresenter(Presenter presenter);
}
