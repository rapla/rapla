package org.rapla.client.internal;

import org.rapla.framework.RaplaException;

public class CommandAbortedException extends RaplaException
{
    public CommandAbortedException(String commandoName)
    {
        super(commandoName);
    }
}
