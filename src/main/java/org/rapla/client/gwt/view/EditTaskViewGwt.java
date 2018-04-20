package org.rapla.client.gwt.view;

import io.reactivex.functions.Consumer;
import org.rapla.client.dialog.gwt.components.VueComponent;
import org.rapla.client.dialog.gwt.components.VueLabel;
import org.rapla.client.internal.edit.EditTaskPresenter;
import org.rapla.client.internal.edit.EditTaskViewFactory;
import org.rapla.entities.Entity;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Map;

@DefaultImplementation(of = EditTaskViewFactory.class, context = InjectionContext.gwt)
public class EditTaskViewGwt implements EditTaskViewFactory<VueComponent> {

  private final Logger logger;

  @Inject
  public EditTaskViewGwt(Logger logger) {
    this.logger = logger;
  }

  @Override
  public <T extends Entity> EditTaskPresenter.EditTaskView<T, VueComponent> create(Map<T, T> editMap, boolean isMerge)
  throws RaplaException {
    logger.info(editMap.toString());
    return new EditTaskPresenter.EditTaskView<T, VueComponent>() {
      private VueLabel component;

      @Override
      public void start(final Consumer<Collection<T>> save, final Runnable close, final Runnable deleteCmd) {
        this.component = new VueLabel(editMap.toString());
      }

      @Override
      public Map<T, T> getEditMap() {
        return editMap;
      }

      @Override
      public boolean hasChanged() {
        return false;
      }

      @Override
      public VueComponent getComponent() {
        return new VueLabel("implementier mich");
//        return new VueDialog(component, new String[] {}) ;
      }
    };
  }
}
