package org.rapla.client.internal.admin.client;

import org.rapla.client.PopupContext;
import org.rapla.client.menu.SelectionMenuContext;
import org.rapla.entities.Category;

public class CategoryMenuContext extends SelectionMenuContext{

    Category rootCategory;
    public CategoryMenuContext(Object focusedObject, PopupContext popupContext, Category rootCategory) {
        super(focusedObject, popupContext);
        this.rootCategory = rootCategory;
    }

    public Category getRootCategory() {
        return rootCategory;
    }
}
