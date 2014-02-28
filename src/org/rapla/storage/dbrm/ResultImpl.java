package org.rapla.storage.dbrm;

import org.rapla.framework.RaplaException;

import com.google.gwtjsonrpc.common.AsyncCallback;

public class ResultImpl<T> implements FutureResult<T>
{

	RaplaException ex;
	T result;
	public ResultImpl()
	{
	}
	
	public ResultImpl(T result) {
		this.result = result;
	}
	public ResultImpl(RaplaException ex)
	{
		this.ex = ex;
	}
	@Override
	public T get() throws RaplaException {
		if ( ex != null)
		{
			throw ex;
		}
		return result;
	}

	@Override
	public T get(long wait) throws RaplaException {
		if ( ex != null)
		{
			throw ex;
		}
		return result;
	}

	@Override
	public void get(AsyncCallback<T> callback) {
		if ( ex != null)
		{
			callback.onFailure( ex);
		}
		callback.onSuccess(result);
	}

}
