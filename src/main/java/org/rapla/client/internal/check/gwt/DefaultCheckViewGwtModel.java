package org.rapla.client.internal.check.gwt;

import java.util.ArrayList;
import java.util.List;

public class DefaultCheckViewGwtModel {
  
  private List<String> warnings = new ArrayList<>();
  
  public void add(String warning) {
    warnings.add(warning);
  }
  
  public boolean isNotEmpty() {
    return !warnings.isEmpty();
  }
  
  public Object createComponent() {
    return new DefaultCheckViewGwtComponent(
      warnings.toArray(new String[] {})
    );
  }
  
}
