package org.rapla.client.internal;

import org.rapla.client.RaplaWidget;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.facade.ClassifiableFilter;
import org.rapla.framework.RaplaException;

import java.util.Collection;

public interface ResourceSelectionView extends RaplaWidget
{

    interface Presenter
    {
        void selectResource(Object focusedObject);

        void updateSelectedObjects(Collection<Object> elements);

        void mouseOverResourceSelection();

        void applyFilter();

        void treeSelectionChanged();

        void updateFilters(ClassificationFilter[] filters) throws RaplaException;
        
    }


    void update(ClassificationFilter[] filter, ClassifiableFilter model, Collection<Object> selectedObjects);

    void updateMenu(Collection<?> list, Object focusedObject) throws RaplaException;

    boolean hasFocus();

    void closeFilterButton();
    
    void setPresenter(Presenter presenter);
}
