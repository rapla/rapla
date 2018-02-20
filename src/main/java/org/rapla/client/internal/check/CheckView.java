package org.rapla.client.internal.check;

public interface CheckView
{
    void addWarning(String warning);
    boolean hasMessages();
}
