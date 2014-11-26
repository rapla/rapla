package org.rapla.rest.gwtjsonrpc.common;


public interface FutureResult<T> {
	public T get() throws Exception;
	public void get(AsyncCallback<T> callback);
}
