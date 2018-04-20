package org.rapla.client.gwt;

import org.rapla.RaplaResources;
import org.rapla.client.ApplicationView;
import org.rapla.client.PopupContext;
import org.rapla.client.RaplaWidget;
import org.rapla.client.dialog.gwt.components.VueComponent;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.facade.ModificationEvent;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Observable;

import javax.inject.Inject;
import java.util.function.Function;

@DefaultImplementation(of = ApplicationView.class, context = InjectionContext.gwt)
public class GwtApplicationViewImpl implements ApplicationView<VueComponent> {

  private static final String MENU_ACTION = "RAPLA_MENU_ACTION";
  private final RaplaResources i18n;
  //    private final NavbarCollapse menu = new NavbarCollapse();
  //    private final NavbarNav navbarNav = new NavbarNav();
  //    private final Div applicationContent = new Div();
  private Logger logger;
  private Presenter presenter;

  @Inject
  public GwtApplicationViewImpl(final RaplaResources i18n, final Logger logger) throws RaplaInitializationException {
    this.i18n = i18n;
    this.logger = logger;
  }

  @Override
  public void setStatusMessage(final String message, boolean highlight) {
    logger.debug(message);
    RaplaVue.emit("gwt-show-status-message", message);
  }

  @Override
  public void updateView(ModificationEvent event) throws RaplaException {
    RaplaVue.emit("gwt-debug", "updateView(" + event + ")");
  }

  @Override
  public void updateMenu() {
    RaplaVue.emit("gwt-debug", "updateMenu()");
  }

  @Override
  public void close() {
    RaplaVue.emit("gwt-debug", "close()");
  }

  public void setPresenter(Presenter presenter) {
    this.presenter = presenter;
  }

  @Override
  public void updateContent(RaplaWidget<VueComponent> w) {
    RaplaVue.emit("gwt-debug", "updateContent("+w+")");
  }

  @Override
  public void init(boolean showToolTips, String windowTitle) {
    RaplaVue.emit("gwt-debug", "init("+String.join(",", String.valueOf(showToolTips), windowTitle)+")");
  }

  @Override
  public PopupContext createPopupContext() {
    return new GwtPopupContext(null);
  }

  @Override
  public void removeWindow(ApplicationEvent windowId) {
    RaplaVue.emit("gwt-debug", "removeWindow("+windowId+")");
  }

  @Override
  public boolean hasWindow(ApplicationEvent windowId) {
    RaplaVue.emit("gwt-debug", "hasWindow("+windowId+")");
    return false;
  }

  @Override
  public void openWindow(ApplicationEvent windowId,
                         PopupContext popupContext,
                         RaplaWidget<VueComponent> component,
                         String title,
                         Function<ApplicationEvent, Boolean> windowClosingFunction,
                         Observable<String> busyIdleObservable) {
    RaplaVue.emit("gwt-debug", "openWindow get component: "+component.getComponent());
    RaplaVue.emit("gwt-open-window", component.getComponent());
  }

  @Override
  public void requestFocus(ApplicationEvent windowId) {
    RaplaVue.emit("gwt-debug", "requestFocus("+windowId+")");
  }

}