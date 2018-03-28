package org.rapla.client.menu.gwt;

import jsinterop.annotations.JsType;
import org.rapla.client.RaplaWidget;
import org.rapla.client.menu.IdentifiableMenuEntry;
import org.rapla.client.menu.MenuInterface;

@JsType(isNative = true)
public class VueMenu
  implements MenuInterface {
  
  @Override
  public native void addMenuItem(final IdentifiableMenuEntry newItem);
  
  @Override
  public native void addSeparator();
  
  @Override
  public native void removeAll();
  
  @Override
  public native void removeAllBetween(
    final String startId,
    final String endId
  );
  
  @Override
  public native void insertAfterId(
    final RaplaWidget component,
    final String id
  );
  
  @Override
  public native void insertBeforeId(
    final RaplaWidget component,
    final String id
  );
  
  @Override
  public native String getId();
  
  @Override
  public native Object getComponent();
}
