package org.rapla.client.dialog.gwt.components;

public enum BulmaTextColor {
  WHITE, BLACK, LIGHT, DARK, PRIMARY, LINK, INFO, SUCCESS, WARNING, DANGER;
  
  public String css() {
    return "has-text-" + this.name().toLowerCase();
  }
}
