package org.rapla.client.gwt.window;

import io.reactivex.functions.Action;

public interface VueWindow {

    class WindowAction {
    public final String icon;
    public final String label;
    public final Action action;

    public WindowAction(final String icon, final String label, final Action action) {
      this.label = label;
      this.action = action;
      this.icon = icon;
    }
  }

  WindowAction[] getActions();
}
