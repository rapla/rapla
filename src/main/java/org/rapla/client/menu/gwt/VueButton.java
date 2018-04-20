package org.rapla.client.menu.gwt;

import io.reactivex.functions.Consumer;
import jsinterop.annotations.JsIgnore;
import jsinterop.annotations.JsType;
import org.rapla.client.dialog.gwt.components.VueComponent;
import org.rapla.client.gwt.VuePopupContext;

@JsType
public class VueButton implements VueComponent, Selectable {

  public final String label;
  public final String icon;
  public boolean iconRight;
  private Consumer<VuePopupContext> action;

  @JsIgnore
  public VueButton(final String label) {
    this(label, "", false);
  }

  @JsIgnore
  public VueButton(final String label, final String icon, final boolean iconRight) {
    this.label = label;
    this.icon = icon;
    this.iconRight = iconRight;
  }

  @JsIgnore
  public VueButton action(Consumer<VuePopupContext> action) {
    this.action = action;
    return this;
  }

  @Override
  public void onSelect() throws Exception {
    action.accept(null);
  }

  @Override
  public String name() {
    return "BButton";
  }

  @Override
  public VueComponent[] children() {
    return new VueComponent[0];
  }
}
