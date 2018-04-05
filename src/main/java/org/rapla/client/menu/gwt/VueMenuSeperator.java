package org.rapla.client.menu.gwt;

import io.reactivex.functions.Consumer;
import org.rapla.client.PopupContext;

public class VueMenuSeperator implements VueMenuItem {

  private static int SEPERATOR_SEQUENCE = 0;

  private final String id;

  public VueMenuSeperator() {
    this.id = "SEPERATOR-" + ++SEPERATOR_SEQUENCE;
  }

  @Override
  public String getLabel() {
    return "---";
  }

  @Override
  public String getIcon() {
    return "";
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public Object getComponent() {
    return null;
  }
}
