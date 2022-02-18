package org.rapla.client.dialog.gwt.components;

import jsinterop.annotations.JsIgnore;
import jsinterop.annotations.JsType;
import org.rapla.client.RaplaTreeNode;

import java.util.ArrayList;
import java.util.List;

@JsType
public class VueTreeNode implements RaplaTreeNode {

  public final String label;
  public final Object userObject;
  private List<VueTreeNode> children = new ArrayList<>();

  public VueTreeNode(final String label, final Object userObject) {
    this.label = label;
    this.userObject = userObject;
  }

  public Object[] children() {
    return children.toArray();
  }

  @Override
  public Object getUserObject() {
    return userObject;
  }

  @JsIgnore
  @Override
  public int getChildCount() {
    return children.size();
  }

  @JsIgnore
  @Override
  public RaplaTreeNode getChild(final int index) {
    return children.get(index);
  }

  @JsIgnore
  @Override
  public void add(final RaplaTreeNode childNode) {
    if (childNode instanceof VueTreeNode) {
      children.add((VueTreeNode) childNode);
    } else
      throw new UnsupportedOperationException("VueTreeNode can only accept other VueTreeNodes as childs");
  }

  @JsIgnore
  @Override
  public void remove(final RaplaTreeNode childNode) {
    if (childNode instanceof VueTreeNode)
      children.remove(childNode);
  }

  @Override
  public String toString() {
    return "{\"_class\":\"VueTreeNode\", " +
      "\"label\":" + (label == null ? "null" : "\"" + label + "\"") + ", " +
      "\"userObject\":" + (userObject == null ? "null" : "\"" + userObject + "\"") + ", " +
      "\"children\":" + (children == null ? "null" : children.size()) + " children }";
  }
}
