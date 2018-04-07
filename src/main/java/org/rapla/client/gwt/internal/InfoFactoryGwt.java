package org.rapla.client.gwt.internal;

import org.rapla.client.PopupContext;
import org.rapla.client.dialog.InfoFactory;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;

@DefaultImplementation(of=InfoFactory.class,context = InjectionContext.gwt)
public class InfoFactoryGwt implements InfoFactory{
    @Inject
    public InfoFactoryGwt()
    {

    }
    @Override
    public  String getToolTip(Object obj) {
        return obj.toString();
    }

    @Override
    public  String getToolTip(Object obj, boolean wrapHtml) {
        return obj.toString();
    }

    @Override
    public <T> void showInfoDialog(T object, PopupContext popupContext) throws RaplaException {

    }

}
