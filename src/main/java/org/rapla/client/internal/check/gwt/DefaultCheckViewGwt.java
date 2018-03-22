package org.rapla.client.internal.check.gwt;

import org.rapla.client.internal.check.CheckView;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;

@DefaultImplementation(of=CheckView.class,context = InjectionContext.gwt)
public class DefaultCheckViewGwt implements CheckView {

    @Inject
    public DefaultCheckViewGwt() {

    }


    @Override
    public void addWarning(String warning) {

    }

    @Override
    public boolean hasMessages() {
        return false;
    }

    @Override
    public Object getComponent()
    {
        return null;
    }
}
