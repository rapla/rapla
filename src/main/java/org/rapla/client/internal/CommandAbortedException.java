package org.rapla.client.internal;

import jsinterop.annotations.JsType;
import org.rapla.framework.RaplaException;

@JsType
public class CommandAbortedException extends RaplaException
{
    public CommandAbortedException(String commandoName)
    {
        super(commandoName);
    }
}
