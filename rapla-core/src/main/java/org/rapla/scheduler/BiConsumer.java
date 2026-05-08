package org.rapla.scheduler;

@FunctionalInterface
public interface BiConsumer<T, U>
{
    void accept(T t, U u) throws Exception;
}
