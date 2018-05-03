package org.rapla.client.dialog.gwt.components;

public enum VuetifyColor {
  red, punk, purple, deepPurple, indigo, blue, lightBlue, cyan, teal, green, lightGreen, lime, yellow, amber, orange,
  deepOrange, brown, blueGrey;

  public String css() {
    return kebapName() + "--text";
  }

  private String kebapName() {
    return name().replaceAll("([A-Z])", "-$1").toLowerCase();
  }
}
