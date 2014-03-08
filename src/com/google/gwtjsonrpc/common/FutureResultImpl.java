package com.google.gwtjsonrpc.common;

import org.rapla.framework.RaplaException;

public class FutureResultImpl<T> implements FutureResult<T> {
	
	RaplaException ex;
	T result;
	
	public T get() throws RaplaException
	{
		return result;
	}
	public T get(long wait) throws RaplaException
	{
		return result;
	}
	public void get(AsyncCallback<T> callback)
	{
		if ( ex != null)
		{
			callback.onFailure(ex);
		}
		else
		{
			callback.onSuccess( result);
		}
	}

	public void onFailure(Throwable invocationException) {
		ex = new RaplaException( invocationException);
	}
	
	public void onSuccess(T result)
	{
		this.result = result;
	}
}
