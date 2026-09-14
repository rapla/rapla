package org.rapla.scheduler;

@FunctionalInterface
public interface Consumer<T>
{
    void accept(T t) throws Exception;
}
