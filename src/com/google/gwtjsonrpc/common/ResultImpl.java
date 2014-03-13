package com.google.gwtjsonrpc.common;




public class ResultImpl<T> implements FutureResult<T>
{

	Exception ex;
	T result;
	public static VoidResult VOID = new VoidResult();
	public static class VoidResult extends ResultImpl<com.google.gwtjsonrpc.common.VoidResult>
	{
		VoidResult() {
		
		}
		public VoidResult(Exception ex)
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
		public StringResult(Exception ex) {
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
	
	public ResultImpl(Exception ex)
	{
		this.ex = ex;
	}
	@Override
	public T get() throws Exception {
		if ( ex != null)
		{
			throw ex;
		}
		return result;
	}

	@Override
	public T get(long wait) throws Exception {
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
