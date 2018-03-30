package org.rapla.client.menu.gwt;

import jsinterop.annotations.JsIgnore;
import jsinterop.annotations.JsType;
import org.rapla.client.RaplaWidget;
import org.rapla.client.menu.IdentifiableMenuEntry;
import org.rapla.client.menu.MenuInterface;
import org.rapla.components.i18n.I18nIcon;

import java.util.ArrayList;
import java.util.List;

@JsType
public class VueMenu implements MenuInterface {
  
  private List<IdentifiableMenuEntry> items = new ArrayList<>();
  private String icon;
  private String text;
  
  @Override
  public void addMenuItem(final IdentifiableMenuEntry newItem) {
    items.add(newItem);
  }
  
  @Override
  public void addSeparator() {
    // not yet implemented
  }
  
  private int indexById(String id) {
    for (int i = 0; i < items.size(); i++) {
      if (items.get(i).getId().equals(id))
        return i;
    }
    return -1;
  }
  
  @Override
  public void removeAll() {
    items.clear();
  }
  
  @Override
  public void removeAllBetween(final String startId, final String endId) {
    List<IdentifiableMenuEntry> newItems = new ArrayList<>();
    int start = indexById(startId);
    int end = indexById(endId);
    for (int i = 0; i < items.size(); i++)
      if (i < start || i > end)
        newItems.add(newItems.get(i));
    this.items = newItems;
  }
  
  @Override
  public void insertAfterId(final RaplaWidget component, final String id) {
    if (component instanceof IdentifiableMenuEntry) {
      IdentifiableMenuEntry entry = (IdentifiableMenuEntry) component;
      if (id == null)
        items.add(entry);
      else
        items.add(indexById(id) + 1, entry);
    }
  }
  
  @Override
  public void insertBeforeId(final RaplaWidget component, final String id) {
    if (component instanceof IdentifiableMenuEntry) {
      IdentifiableMenuEntry entry = (IdentifiableMenuEntry) component;
      if (id == null)
        items.add(entry);
      else
        items.add(indexById(id), entry);
    }
  }
  
  @Override
  public String getId() {
    return null;
  }
  
  @Override
  public Object getComponent() {
    return this;
  }
  
  public String getText() {
    return text;
  }
  
  public String getIcon() {
    return icon;
  }
  
  public IdentifiableMenuEntry[] getItems() {
    return items.toArray(IdentifiableMenuEntry.EMPTY_ARRAY);
  }
  
  @JsIgnore
  public VueMenu icon(final I18nIcon icon) {
    this.icon = icon.getId();
    return this;
  }
  
  @JsIgnore
  public VueMenu text(final String text) {
    this.text = text;
    return this;
  }
}
