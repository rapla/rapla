package org.rapla.client.gwt.plugin;

import org.rapla.client.gwt.Greeter;

import com.google.gwt.inject.client.GinModule;
import com.google.gwt.inject.client.binder.GinBinder;
import com.google.gwt.inject.client.multibindings.GinMultibinder;

public class TestGWTPlugin implements GinModule{
    @Override
    public void configure(GinBinder binder) {
        GinMultibinder<Greeter> uriBinder = GinMultibinder.newSetBinder(binder, Greeter.class);
        uriBinder.addBinding().to(MyGWTGreeter.class);
    }
    
}

