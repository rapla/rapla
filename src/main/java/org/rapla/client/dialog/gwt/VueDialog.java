package org.rapla.client.dialog.gwt;

import jsinterop.annotations.JsType;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.gwt.components.VueComponent;
import org.rapla.client.gwt.RaplaVue;
import org.rapla.components.i18n.I18nIcon;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.UnsynchronizedPromise;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

@JsType
public class VueDialog implements DialogInterface  {

  private String icon;
  private String title;
  private VueComponent content;
  private String[] buttons;
  private DialogAction[] buttonActions;
  private Promise<Integer> promise = new UnsynchronizedPromise<>();
  private Runnable abortAction = () -> {};
  private Integer defaultAction;

  public VueDialog(final VueComponent content, String[] actions) {
    this.content = content;
    this.buttons = actions;
    this.buttonActions = IntStream.range(0, actions.length)
                                  .mapToObj(a -> new VueDialogAction())
                                  .collect(Collectors.toList())
                                  .toArray(new VueDialogAction[] {});
  }

  @Override
  public Promise<Integer> start(final boolean pack) {
    RaplaVue.emit("gwt-dialog-open", this);
    return promise;
  }

  public int getDefaultAction() {
    return defaultAction;
  }

  public VueComponent getContent() {
    return content;
  }

  public Promise<Integer> getPromise() {
    return promise;
  }

  public String getTitle() {
    return title;
  }

  @Override
  public void setTitle(final String title) {
    this.title = title;
  }

  public String getIcon() {
    return icon;
  }

  @Override
  public void setIcon(final I18nIcon iconKey) {
    this.icon = iconKey.getId();
  }

  public String[] getButtonStrings() {
    return buttons;
  }

  @Override
  public void busy(final String message) {

  }

  @Override
  public void idle() {

  }

  @Override
  public void close() {
    RaplaVue.emit("gwt-dialog-close");
  }

  /**
   * buttons
   */
  @Override
  public DialogAction getAction(final int commandIndex) {
    return this.buttonActions[commandIndex];
  }

  public Runnable getAbortAction() {
    return abortAction;
  }

  @Override
  public void setAbortAction(final Runnable abortAction) {
    this.abortAction = abortAction;
  }

  @Override
  public void setDefault(final int commandIndex) {
    this.defaultAction = commandIndex;
  }

}
