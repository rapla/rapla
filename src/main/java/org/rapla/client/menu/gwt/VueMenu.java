package org.rapla.client.menu.gwt;

import jsinterop.annotations.JsType;
import org.rapla.client.RaplaWidget;
import org.rapla.client.menu.IdentifiableMenuEntry;
import org.rapla.client.menu.MenuInterface;

import java.util.ArrayList;
import java.util.List;

@JsType
public class VueMenu implements MenuInterface {
  
  private List<IdentifiableMenuEntry> items = new ArrayList<>();
  
  @Override
  public void addMenuItem(final IdentifiableMenuEntry newItem) {
    items.add(newItem);
  }
  
  public List<IdentifiableMenuEntry> getItems() {
    return items;
  }
  
  @Override
  public void addSeparator() {
  
  }
  
  @Override
  public void removeAll() {
  
  }
  
  @Override
  public void removeAllBetween(
    final String startId,
    final String endId
  ) {
  
  }
  
  @Override
  public void insertAfterId(
    final RaplaWidget component,
    final String id
  ) {
  
  }
  
  @Override
  public void insertBeforeId(
    final RaplaWidget component,
    final String id
  ) {
  
  }
  
  @Override
  public String getId() {
    return null;
  }
  
  @Override
  public Object getComponent() {
    return null;
  }
}
