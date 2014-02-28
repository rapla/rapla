package org.rapla.storage.dbrm;

import org.rapla.framework.RaplaException;

import com.google.gwtjsonrpc.common.AsyncCallback;

public interface FutureResult<T> {
	public T get() throws RaplaException;
	public T get(long wait) throws RaplaException;
	public void get(AsyncCallback<T> callback);
}
