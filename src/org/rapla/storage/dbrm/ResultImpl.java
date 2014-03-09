package org.rapla.storage.dbrm;

import org.rapla.framework.RaplaException;

import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtjsonrpc.common.FutureResult;

public class ResultImpl<T> implements FutureResult<T>
{

	RaplaException ex;
	T result;
	public static VoidResult VOID = new VoidResult();
	public static class VoidResult extends ResultImpl<VoidResult>
	{
		VoidResult() {
		
		}
		public VoidResult(RaplaException ex)
		{
			super( ex);
		}
	}
	public static class StringResult extends ResultImpl<String> 
	{
		StringResult() 
		{
			super();
		}
		public StringResult(RaplaException ex) {
			super(ex);
		}
		public StringResult(String s) {
			super(s);
		}
	}	

	protected ResultImpl()
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
