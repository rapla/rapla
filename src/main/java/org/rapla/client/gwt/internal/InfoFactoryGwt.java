package org.rapla.client.gwt.internal;

import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.InfoFactory;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

@DefaultImplementation(of=InfoFactory.class,context = InjectionContext.gwt)
public class InfoFactoryGwt implements InfoFactory{
    @Override
    public <T> String getToolTip(T obj) {
        return obj.toString();
    }

    @Override
    public <T> String getToolTip(T obj, boolean wrapHtml) {
        return obj.toString();
    }

    @Override
    public <T> void showInfoDialog(T object, PopupContext popupContext) throws RaplaException {

    }

}
