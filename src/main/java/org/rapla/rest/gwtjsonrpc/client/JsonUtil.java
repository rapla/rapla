// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.rapla.rest.gwtjsonrpc.client;

import org.rapla.rest.gwtjsonrpc.client.event.BaseRpcEvent;
import org.rapla.rest.gwtjsonrpc.client.event.RpcCompleteEvent;
import org.rapla.rest.gwtjsonrpc.client.event.RpcCompleteHandler;
import org.rapla.rest.gwtjsonrpc.client.event.RpcStartEvent;
import org.rapla.rest.gwtjsonrpc.client.event.RpcStartHandler;
import org.rapla.rest.gwtjsonrpc.client.impl.ResultDeserializer;
import org.rapla.rest.gwtjsonrpc.common.AsyncCallback;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.rpc.InvocationException;
import com.google.gwt.user.client.rpc.ServiceDefTarget;

/** Client side utility functions. */
public class JsonUtil {
  private static final HandlerManager globalHandlers = new HandlerManager(null);

  /**
   * Bind a RemoteJsonService proxy to its server URL.
   *
   * @param <T> type of the service interface.
   * @param imp the service instance, returned by <code>GWT.create</code>.
   * @param path the path of the service, relative to the GWT module.
   * @return always <code>imp</code>.
   * @see com.google.gwt.user.client.rpc.RemoteServiceRelativePath
   */
  public static <T> T bind(final T imp,
      final String path) {
    assert GWT.isClient();
    assert imp instanceof ServiceDefTarget;
    final String base = GWT.getModuleBaseURL();
    ((ServiceDefTarget) imp).setServiceEntryPoint(base + path);
    return imp;
  }

  /** Register a handler for RPC start events. */
  public static HandlerRegistration addRpcStartHandler(RpcStartHandler h) {
    return globalHandlers.addHandler(RpcStartEvent.getType(), h);
  }

  /** Register a handler for RPC completion events. */
  public static HandlerRegistration addRpcCompleteHandler(RpcCompleteHandler h) {
    return globalHandlers.addHandler(RpcCompleteEvent.getType(), h);
  }

  public static void fireEvent(BaseRpcEvent<?> event) {
    globalHandlers.fireEvent(event);
  }

  public static <T> void invoke(final ResultDeserializer<T> resultDeserializer,
      final AsyncCallback<T> callback, final JavaScriptObject rpcResult) {
    final T result;
    try {
      result = resultDeserializer.fromResult(rpcResult);
    } catch (RuntimeException e) {
      callback.onFailure(new InvocationException("Invalid JSON Response", e));
      return;
    }
    callback.onSuccess(result);
  }

  private JsonUtil() {
  }
}
