package org.rapla.inject;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value={ ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(DefaultImplementationRepeatable.class)
public @interface DefaultImplementation
{
    /** interface that is implmented*/
    Class of();
    /** context in which this implementation is used. server, client, gwt, swing*/
    InjectionContext[] context() default {};
    /**
     * true if you want to make the interface visible in the generated component interface (this is important for starter classes*/
    boolean export() default false;
}
