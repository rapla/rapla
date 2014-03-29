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

package org.rapla.rest.gwtjsonrpc.client.impl;

import org.rapla.rest.gwtjsonrpc.client.ServerUnavailableException;
import org.rapla.rest.gwtjsonrpc.client.event.RpcCompleteEvent;
import org.rapla.rest.gwtjsonrpc.client.event.RpcStartEvent;
import org.rapla.rest.gwtjsonrpc.common.AsyncCallback;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;

public abstract class JsonCall<T> implements RequestCallback {
  protected static final JavaScriptObject jsonParser;
  

  static {
    jsonParser = selectJsonParser();
  }

  /**
   * Select the most efficient available JSON parser.
   * 
   * If we have a native JSON parser, present in modern browsers (FF 3.5 and
   * IE8, at time of writing), it is returned. If no native parser is found, a
   * parser function using <code>eval</code> is returned.
   * 
   * This is done dynamically, not with a GWT user.agent check, because FF3.5
   * does not have a specific user.agent associated with it. Introducing a new
   * property for the presence of an ES3.1 parser is not worth it, since the
   * check is only done once anyway, and will result in significantly longer
   * compile times.
   * 
   * As GWT will undoubtedly introduce support for the native JSON parser in the
   * {@link com.google.gwt.json.client.JSONParser JSONParser} class, this code
   * should be reevaluated to possibly use the GWT parser reference.
   * 
   * @return a javascript function with the fastest available JSON parser
   * @see "http://wiki.ecmascript.org/doku.php?id=es3.1:json_support"
   */
  private static native JavaScriptObject selectJsonParser()
  /*-{
    if ($wnd.JSON && typeof $wnd.JSON.parse === 'function')
      return $wnd.JSON.parse;
    else
      return function(expr) { return eval('(' + expr + ')'); };
  }-*/;

  protected final AbstractJsonProxy proxy;
  protected final String methodName;
  protected final String requestParams;
  protected final ResultDeserializer<T> resultDeserializer;
  protected int attempts;
  protected AsyncCallback<T> callback;

  private String token;


  protected JsonCall(final AbstractJsonProxy abstractJsonProxy,
      final String methodName, final String requestParams,
      final ResultDeserializer<T> resultDeserializer) {
    this.proxy = abstractJsonProxy;
    this.methodName = methodName;
    this.requestParams = requestParams;
    this.resultDeserializer = resultDeserializer;
  }

  public String getToken() {
      return token;
  }

  public void setToken(String token) {
      this.token = token;
  }

  public AbstractJsonProxy getProxy() {
    return proxy;
  }

  public String getMethodName() {
    return methodName;
  }

  protected abstract void send();
  
  public void send(AsyncCallback<T> callback)
  {
	  this.callback = callback;
	  send();
  }

  protected void send(RequestBuilder rb) {
    try {
        if ( token != null)
        {
            rb.setHeader("Authorization", "Bearer " + token);
        }
        attempts++;
        rb.send();
    } catch (RequestException e) {
        callback.onFailure(e);
        return;
    }

    if (attempts == 1) {
      RpcStartEvent.fire(this);
    }
  }

  @Override
  public void onError(final Request request, final Throwable exception) {
    RpcCompleteEvent.fire(this);
    if (exception.getClass() == RuntimeException.class
        && exception.getMessage().contains("XmlHttpRequest.status")) {
      // GWT's XMLHTTPRequest class gives us RuntimeException when the
      // status code is unreadable from the browser. This occurs when
      // the connection has failed, e.g. the host is down.
      //
      callback.onFailure(new ServerUnavailableException());
    } else {
      callback.onFailure(exception);
    }
  }
}
