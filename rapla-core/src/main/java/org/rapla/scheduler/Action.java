package org.rapla.scheduler;

@FunctionalInterface
public interface Action
{
    void run() throws Exception;
}
