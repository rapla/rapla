package org.rapla.rest.gwtjsonrpc.client.impl;

import org.rapla.rest.gwtjsonrpc.common.AsyncCallback;
import org.rapla.rest.gwtjsonrpc.common.FutureResult;

public class FutureResultImpl<T> implements FutureResult<T> {
	
	JsonCall call;
	
	public T get() throws Exception
	{
		throw new UnsupportedOperationException();
	}
	public T get(long wait) throws Exception
	{
		throw new UnsupportedOperationException();
	}

	@SuppressWarnings("unchecked")
    public void get(AsyncCallback<T> callback)
	{
		call.send(callback);
	}
	
	public void setCall( JsonCall call)
	{
		this.call = call;
	}
}
