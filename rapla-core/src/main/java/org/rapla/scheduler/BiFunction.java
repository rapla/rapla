package org.rapla.scheduler;

@FunctionalInterface
public interface BiFunction<T, U, R>
{
    R apply(T t, U u) throws Exception;
}
