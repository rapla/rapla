package org.rapla.inject;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface ExtensionPoint
{
    InjectionContext[] context();
    String id();
}
