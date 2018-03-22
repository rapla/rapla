package org.rapla.client.internal.check.gwt;

import jsinterop.annotations.JsType;

@JsType
public class DefaultCheckViewGwtComponent {
  private final String[] warnings;
  
  public DefaultCheckViewGwtComponent(final String[] warnings) {
    this.warnings = warnings;
  }
  
  public String[] getWarnings() {
    return warnings;
  }
  
  public String getDialogType() {
    return "Text";
  }
}
