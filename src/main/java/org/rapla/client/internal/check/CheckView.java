package org.rapla.client.internal.check;

import org.rapla.client.RaplaWidget;

public interface CheckView extends RaplaWidget
{
    void addWarning(String warning);
    boolean hasMessages();
}
