package com.google.gwtjsonrpc.common;

import org.rapla.framework.RaplaException;

public interface FutureResult<T> {
	public T get() throws RaplaException;
	public T get(long wait) throws RaplaException;
	public void get(AsyncCallback<T> callback);
}
