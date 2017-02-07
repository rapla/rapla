package org.rapla.client.event;

import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.inject.Singleton;

@DefaultImplementation(of = EventBus.class,context = InjectionContext.all)
@Singleton
public class RaplaEventBus extends SimpleEventBus
{
    @Inject
    public RaplaEventBus()
    {

    }
}
