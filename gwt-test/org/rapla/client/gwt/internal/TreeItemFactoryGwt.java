package org.rapla.client.gwt.internal;

import org.rapla.RaplaResources;
import org.rapla.client.RaplaTreeNode;
import org.rapla.client.dialog.gwt.components.VueTreeNode;
import org.rapla.client.internal.ConflictText;
import org.rapla.client.internal.TreeItemFactory;
import org.rapla.entities.Named;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import java.util.Locale;

@DefaultImplementation(of = TreeItemFactory.class, context = InjectionContext.gwt)
public class TreeItemFactoryGwt implements TreeItemFactory {

  private final RaplaLocale locale;
  private final Logger logger;
  private final RaplaResources i18n;

  @Inject
  public TreeItemFactoryGwt(RaplaLocale locale, Logger logger, RaplaResources i18n) {
    this.locale = locale;
    this.logger = logger;
    this.i18n = i18n;
  }

  @Override
  public RaplaTreeNode createNode(Object userObject) {
    return new VueTreeNode(getTitle(userObject), userObject);
  }

  public String getTitle(Object obj) {
    Locale javaLocale = this.locale.getLocale();
    if (obj != null) {
      if (obj instanceof Conflict) {
        return ConflictText.getConflictText((Conflict) obj, this.locale, i18n);
      } else if (obj instanceof Classifiable) {
        Classification classification = ((Classifiable) obj).getClassification();
        if (classification.getType().getAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT_PLANNING) != null) {
          return classification.format(javaLocale, DynamicTypeAnnotations.KEY_NAME_FORMAT_PLANNING);
        }
      }
      if (obj instanceof Named) {
        return ((Named) obj).getName(javaLocale);
      }
      return obj.toString();
    }
    return "null";
  }
}
