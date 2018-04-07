package org.rapla.client.dialog.gwt.components;

import jsinterop.annotations.JsIgnore;
import jsinterop.annotations.JsType;

import java.util.ArrayList;
import java.util.List;

@JsType
public class VueList implements VueComponent {
  
  private List<VueListItem> items = new ArrayList<>();
  
  @Override
  public String name() {
    return "BList";
  }
  
  @JsIgnore
  public VueList item(VueListItem item) {
    items.add(item);
    return this;
  }
  
  @Override
  public VueComponent[] children() {
    return new VueComponent[0];
  }
  
  public VueListItem[] getItems() {
    return items.toArray(new VueListItem[] {});
  }
  
  @JsType
  public static class VueListItem {
    
    private final String id;
    private final String label;
    
    public VueListItem(final String id, final String label) {
      this.id = id;
      this.label = label;
    }
    
    public String getId() {
      return id;
    }
    
    public String getLabel() {
      return label;
    }
  }
}
