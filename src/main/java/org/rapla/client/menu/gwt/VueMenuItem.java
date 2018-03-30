package org.rapla.client.menu.gwt;

import io.reactivex.functions.Consumer;
import jsinterop.annotations.JsIgnore;
import jsinterop.annotations.JsType;
import org.rapla.client.PopupContext;
import org.rapla.client.menu.IdentifiableMenuEntry;
import org.rapla.components.i18n.I18nIcon;

@JsType
public class VueMenuItem implements IdentifiableMenuEntry {
  
  private final String id;
  private String icon;
  private Consumer<PopupContext> action;
  
  VueMenuItem(final String id) {
    this.id = id;
  }
  
  @Override
  public String getId() {
    return id;
  }
  
  public String getIcon() {
    return icon;
  }
  
  @Override
  public Object getComponent() {
    return this;
  }
  
  public void fireAction(PopupContext context) throws Exception {
    action.accept(context);
  }
  
  @JsIgnore
  public VueMenuItem action(Consumer<PopupContext> action) {
    this.action = action;
    return this;
  }
  
  @JsIgnore
  public VueMenuItem icon(final I18nIcon icon) {
    this.icon = icon.getId();
    return this;
  }
}
