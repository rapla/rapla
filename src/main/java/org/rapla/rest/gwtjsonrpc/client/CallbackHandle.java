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

import org.rapla.rest.gwtjsonrpc.client.impl.ResultDeserializer;
import org.rapla.rest.gwtjsonrpc.common.AsyncCallback;

import com.google.gwt.core.client.JavaScriptObject;

/**
 * Global JavaScript function for JSON callbacks from non-RPC invocations.
 * <p>
 * To setup a callback declare one in your remote service interface:
 * 
 * <pre>
 * public interface MyActions extends RemoteJsonService {
 *   CallbackHandle&lt;MyResult&gt; forMyResult(AsyncCallback&lt;MyResult&gt; ac);
 * }
 * </pre>
 * <p>
 * Then in your application code create the callback handle and obtain its
 * function name. Pass the function name to the remote service, so the remote
 * service can execute:
 * 
 * <pre>
 * $functionName($result)
 * </pre>
 * <p>
 * where <code>$functionName</code> came from {@link #getFunctionName()} (you
 * may need to prefix <code>"parent."</code> if invoking within an iframe) and
 * <code>$result</code> is the native JSON encoding of the <code>MyResult</code>
 * object type.
 * <p>
 * When the callback is complete it will be automatically unregistered from the
 * window and your {@link AsyncCallback}'s <code>onSuccess</code> will be called
 * with the deserialized <code>MyResult</code> instance.
 * <p>
 * Each CallbackHandle uses a unique function name, permitting the application
 * to use multiple concurrent callbacks. Handles which are canceled before the
 * response is received will never be invoked.
 */
public class CallbackHandle<R> {
  private static int callbackId;

  static {
    nativeInit();
  }

  private static int nextFunction() {
    return ++callbackId;
  }

  private final ResultDeserializer<R> deserializer;
  private final AsyncCallback<R> callback;
  private int functionId;

  /**
   * Create a new callback instance.
   * <p>
   * Typically this should be done by a factory function inside of a
   * RemoteJsonService interface.
   * 
   * @param ser parses the JSON result once received back.
   * @param ac the application callback function to supply the result to. Only
   *        <code>onSuccess</code> will be invoked.
   */
  public CallbackHandle(final ResultDeserializer<R> ser,
      final AsyncCallback<R> ac) {
    deserializer = ser;
    callback = ac;
  }

  /**
   * Install this handle into the browser window and generate a unique name.
   * <p>
   * Does nothing if the callback has already been installed.
   * <p>
   * This method pins this callback object and the application supplied
   * {@link AsyncCallback} into the global browser memory, so it exists when the
   * JSON service returns with its result data. If the JSON service never
   * returns, the callback (and anything it reaches) may leak. Applications can
   * use {@link #cancel()} to explicitly remove this handle.
   */
  public void install() {
    if (functionId == 0) {
      functionId = nextFunction();
      nativeInstall(functionId, this);
    }
  }

  /**
   * Obtain the unique function name for this JSON callback.
   * <p>
   * Applications must call {@link #install()} first to ensure the function name
   * has been generated and installed into the window. The function name is only
   * valid until either {@link #cancel()} is called or the remote service has
   * returned the call.
   * 
   * @return name of the function the JSON service should call with the
   *         resulting data.
   */
  public String getFunctionName() {
    assert functionId > 0;
    return "__gwtjsonrpc_callbackhandle[" + functionId + "]";
  }

  /**
   * Delete this function from the browser so it can't be called.
   * <p>
   * Does nothing if the callback has already been canceled.
   * <p>
   * This method deletes the function, permitting <code>this</code> and the
   * application callback to be garbage collected. Automatically invoked when a
   * result is received.
   */
  public void cancel() {
    if (functionId > 0) {
      nativeDelete(functionId);
      functionId = 0;
    }
  }

  final void onResult(final JavaScriptObject rpcResult) {
    cancel();
    JsonUtil.invoke(deserializer, callback, rpcResult);
  }

  private static final native void nativeInit()
  /*-{ $wnd.__gwtjsonrpc_callbackhandle = new Array(); }-*/;

  private static final native void nativeDelete(int funid)
  /*-{ delete $wnd.__gwtjsonrpc_callbackhandle[funid]; }-*/;

  private static final native void nativeInstall(int funid,
      CallbackHandle<?> imp)
  /*-{ $wnd.__gwtjsonrpc_callbackhandle[funid] = function(r) { imp.@org.rapla.rest.gwtjsonrpc.client.CallbackHandle::onResult(Lcom/google/gwt/core/client/JavaScriptObject;)({result:r}); }; }-*/;
}
