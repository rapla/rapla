package org.rapla.client.internal.check.gwt;

import org.rapla.client.internal.check.CheckView;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;

@DefaultImplementation(of=CheckView.class,context = InjectionContext.gwt)
public class DefaultCheckViewGwt implements CheckView {

    private DefaultCheckViewGwtModel model = new DefaultCheckViewGwtModel();

    @Inject
    public DefaultCheckViewGwt() {

    }

    @Override
    public void addWarning(String warning) {
        model.add(warning);
    }

    @Override
    public boolean hasMessages() {
        return model.isNotEmpty();
    }

    @Override
    public Object getComponent()
    {
        return model.createComponent();
    }
}
