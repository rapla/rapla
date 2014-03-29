package org.rapla.rest.gwtjsonrpc.common;

@java.lang.annotation.Retention(value=java.lang.annotation.RetentionPolicy.RUNTIME)
@java.lang.annotation.Target(value={java.lang.annotation.ElementType.METHOD})
public @interface ResultType {

	Class value();
	Class container() default Object.class;
}
