package org.rapla.rest.gwtjsonrpc.client.impl;

import org.rapla.rest.gwtjsonrpc.common.AsyncCallback;
import org.rapla.rest.gwtjsonrpc.common.FutureResult;

public class FutureResultImpl<T> implements FutureResult<T> {
	
	private static final long DEFAULT_TIMEOUT = 15000l;
    JsonCall<T> call;
	
	public T get() throws Exception
	{
	    final T result = get(DEFAULT_TIMEOUT);
        return result;
	}
	public T get(long wait) throws Exception
	{
	    final T result = call.sendSynchronized( wait);
	    return result;
	}

	@SuppressWarnings("unchecked")
    public void get(AsyncCallback<T> callback)
	{
		call.send(callback);
	}
	
	public void setCall( JsonCall<T> call)
	{
		this.call = call;
	}
}
