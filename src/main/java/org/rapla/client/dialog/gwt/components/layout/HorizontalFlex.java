package org.rapla.client.dialog.gwt.components.layout;

import jsinterop.annotations.JsIgnore;
import org.rapla.client.dialog.gwt.components.VueComponent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HorizontalFlex implements VueComponent {
  
  private final ArrayList<VueComponent> children;
  
  public HorizontalFlex() {
    this.children = new ArrayList<>();
  }
  
  @Override
  public String name() {
    return "HorizontalFlex";
  }
  
  @JsIgnore
  public HorizontalFlex addChild(VueComponent newChild) {
    children.add(newChild);
    return this;
  }
  
  @JsIgnore
  public <T> HorizontalFlex addChildren(Collection<T> newChildren, Function<T, VueComponent> toVue) {
    children.addAll(
      newChildren.stream().map(toVue).collect(Collectors.toList())
    );
    return this;
  }
  
  @Override
  public VueComponent[] children() {
    return children.toArray(new VueComponent[] {});
  }
}
