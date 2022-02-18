package org.rapla.client.menu.gwt;

import jsinterop.annotations.JsIgnore;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsType;
import org.rapla.client.RaplaWidget;
import org.rapla.client.menu.IdentifiableMenuEntry;
import org.rapla.client.menu.MenuInterface;
import org.rapla.components.i18n.I18nIcon;

import java.util.ArrayList;
import java.util.List;

@JsType
public class VueMenu implements MenuInterface, VueMenuItem, IdentifiableMenuEntry {

  private List<VueMenuItem> items = new ArrayList<>();
  private String id;
  private String icon;
  private String label;
  private String title;
  private boolean enabled;

  public VueMenu() {

  }

  @Override
  public void addMenuItem(final IdentifiableMenuEntry newItem) {
    if (newItem instanceof VueMenuItem)
      items.add((VueMenuItem) newItem);
    else
      throw new RuntimeException("VueMenu.addMenuItem: " + newItem + " is not a VueMenuItem");
  }

  @Override
  public void addSeparator() {
    items.add(new VueMenuSeperator());
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
    List<VueMenuItem> newItems = new ArrayList<>();
    int start = indexById(startId);
    int end = indexById(endId);
    for (int i = 0; i < items.size(); i++)
      if (i < start || i > end)
        newItems.add(newItems.get(i));
    this.items = newItems;
  }

  @Override
  public void insertAfterId(RaplaWidget component, final String id) {
    component = (RaplaWidget) component.getComponent();
    if (component instanceof VueMenuItem) {
      VueMenuItem entry = (VueMenuItem) component;
      if (id == null)
        items.add(entry);
      else
        items.add(indexById(id) + 1, entry);
    } else {
      throw new RuntimeException(
        "VueMenu can only accept another VueMenu or a VueMenuItem, got " + component.getClass().getSimpleName() +
          " " + component.toString());
    }
  }

  @Override
  public void insertBeforeId(RaplaWidget component, final String id) {
    component = (RaplaWidget) component.getComponent();
    if (component instanceof VueMenuItem) {
      VueMenuItem entry = (VueMenuItem) component;
      if (id == null)
        items.add(entry);
      else
        items.add(indexById(id), entry);
    } else {
      throw new RuntimeException(
        "VueMenu can only accept another VueMenu or a VueMenuItem, got " + component.getClass().getSimpleName() +
          " " + component.toString());
    }
  }

  @Override
  public String getId() {
    return id == null ? label : id;
  }

  @JsIgnore
  public void setId(final String id) {
    this.id = id;
  }

  @JsMethod
  @Override
  public Object getComponent() {
    return this;
  }

  @Override
  public String getLabel() {
    return label;
  }

  @Override
  public String getIcon() {
    return icon;
  }

  public String getTitle() {
    return title;
  }

  public IdentifiableMenuEntry[] getItems() {
    return items.toArray(IdentifiableMenuEntry.EMPTY_ARRAY);
  }

  @JsIgnore
  public VueMenu icon(final I18nIcon icon) {
    this.icon = icon == null ? null : icon.getId();
    return this;
  }

  @JsIgnore
  public VueMenu label(final String text) {
    this.label = text;
    return this;
  }

  @Override
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public void setTitle(final String title) {
    this.title = title;
  }
}
