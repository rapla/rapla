package org.rapla.rest.gwtjsonrpc.common;




public class ResultImpl<T> implements FutureResult<T>
{

	Exception ex;
	T result;
	public static VoidResultImpl VOID = new VoidResultImpl();
	public static class VoidResultImpl extends ResultImpl<org.rapla.rest.gwtjsonrpc.common.VoidResult>
	{
		VoidResultImpl() {
		
		}
		public VoidResultImpl(Exception ex)
		{
			super( ex);
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
