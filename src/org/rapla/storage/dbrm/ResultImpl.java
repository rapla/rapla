package org.rapla.storage.dbrm;

import org.rapla.framework.RaplaException;

import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtjsonrpc.common.FutureResult;
import com.google.gwtjsonrpc.common.VoidResult;

public class ResultImpl<T> implements FutureResult<T>
{

	RaplaException ex;
	T result;
	public static ResultImpl<VoidResult> VOID = new ResultImpl<VoidResult>();
	
	private ResultImpl()
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
		else
		{
			callback.onSuccess(result);
		}
	}

}
