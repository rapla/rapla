package org.rapla.client.internal.admin.client;

import org.rapla.client.PopupContext;
import org.rapla.client.menu.SelectionMenuContext;

public class DynamicTypeMenuContext extends SelectionMenuContext{
    String classificationType;

    public DynamicTypeMenuContext(Object focusedObject, PopupContext popupContext) {
        super(focusedObject, popupContext);
    }

    public String getClassificationType() {
        return classificationType;
    }

    public DynamicTypeMenuContext setClassificationType(String classificationType) {
        this.classificationType = classificationType;
        return this;
    }






}
