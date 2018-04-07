package org.rapla.client.dialog.gwt;

import org.rapla.client.dialog.DialogInterface;
import org.rapla.components.i18n.I18nIcon;

public class VueDialogAction implements DialogInterface.DialogAction {

  private boolean enabled;
  private Runnable runnable;
  private String icon;

  @Override
  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public void setRunnable(final Runnable runnable) {
    this.runnable = runnable;
  }

  @Override
  public void setIcon(final I18nIcon icon) {
    this.icon = icon.getId();
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Runnable getRunnable() {
    return runnable;
  }

  public String getIcon() {
    return icon;
  }

  @Override
  public void execute() {

  }
}
