package org.rapla.client.menu.gwt;

import io.reactivex.functions.Consumer;
import org.rapla.client.PopupContext;
import org.rapla.client.menu.IdentifiableMenuEntry;

public interface VueMenuItem extends IdentifiableMenuEntry {

  String getLabel();

  String getIcon();

  Consumer<PopupContext> getAction();

  void fireAction(PopupContext context) throws Exception;
}
