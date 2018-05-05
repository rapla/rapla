package org.rapla.client.dialog.gwt.components;

import jsinterop.annotations.JsIgnore;
import jsinterop.annotations.JsType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@JsType
public class VueLayout implements VueComponent {

  public enum Direction {
    ROW, COLUMN
  }

  public final boolean column;
  public boolean wrap = false;
  public boolean justifyCenter = true;
  private final List<VueComponent> children;

  public VueLayout(final Direction direction) {
    column = direction == Direction.COLUMN;
    children = new ArrayList<>();
  }

  @JsIgnore
  public VueLayout wrap() {
    wrap = true;
    return this;
  }

  @Override
  public String name() {
    return "vue-layout";
  }

  @JsIgnore
  public VueLayout addChild(VueComponent newChild) {
    children.add(newChild);
    return this;
  }

  @JsIgnore
  public <T> VueLayout addChildren(List<T> items, Function<T, ? extends VueComponent> itemToComponent) {
    children.addAll(
      items.stream()
           .map(itemToComponent)
           .collect(Collectors.toList())
    );
    return this;
  }

  @Override
  public VueComponent[] children() {
    return children.toArray(new VueComponent[] {});
  }
}
