package org.rapla.client.gwt.view;

import org.rapla.client.TreeFactory;
import org.rapla.client.internal.ResourceSelectionView;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.facade.ClassifiableFilter;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import java.util.Collection;

@DefaultImplementation(of = ResourceSelectionView.class, context = InjectionContext.gwt)
public class ResourceSelectionViewGwt implements ResourceSelectionView {

  private final TreeFactory treeFactory;

  @Inject
  public ResourceSelectionViewGwt(TreeFactory treeFactory) {
    this.treeFactory = treeFactory;
  }

  @Override
  public void update(ClassificationFilter[] filter, ClassifiableFilter model, Collection<Object> selectedObjects) {
    try {
      treeFactory.newRootNode().add(treeFactory.createAllocatableModel(filter).allocatableNode);
    } catch (RaplaException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void updateMenu(Collection<?> list, Object focusedObject) throws RaplaException {

  }

  @Override
  public boolean hasFocus() {
    return false;
  }

  @Override
  public void closeFilterButton() {

  }

  @Override
  public void setPresenter(Presenter presenter) {

  }

  @Override
  public Object getComponent() {
    return null;
  }
}
