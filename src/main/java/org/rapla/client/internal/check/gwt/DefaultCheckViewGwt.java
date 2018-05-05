package org.rapla.client.internal.check.gwt;

import org.rapla.client.dialog.gwt.components.VueLayout;
import org.rapla.client.dialog.gwt.components.VuetifyColor;
import org.rapla.client.dialog.gwt.components.VueComponent;
import org.rapla.client.dialog.gwt.components.VueLabel;
import org.rapla.client.internal.check.CheckView;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@DefaultImplementation(of = CheckView.class, context = InjectionContext.gwt)
public class DefaultCheckViewGwt implements CheckView {

  private final Logger logger;
  private final List<String> warnings = new ArrayList<>();

  @Inject
  public DefaultCheckViewGwt(Logger logger) {
    this.logger = logger;
  }

  @Override
  public void addWarning(String warning) {
    warnings.add(warning);
  }

  @Override
  public boolean hasMessages() {
    return !warnings.isEmpty();
  }

  @Override
  public VueComponent getComponent() {
    logger.debug(warnings.toString());
    return new VueLayout(VueLayout.Direction.COLUMN)
      .addChildren(warnings, text -> new VueLabel(text).color(VuetifyColor.red));
  }
}
