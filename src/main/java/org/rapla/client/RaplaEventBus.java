package org.rapla.client;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;

@Singleton
@DefaultImplementation(of=EventBus.class,context = InjectionContext.client)
public class RaplaEventBus extends SimpleEventBus
{
    @Inject
    public RaplaEventBus()
    {
    }
}
