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

import java.util.Arrays;
import java.util.List;

import org.rapla.rest.gwtjsonrpc.client.ExceptionDeserializer;
import org.rapla.rest.gwtjsonrpc.client.JsonUtil;
import org.rapla.rest.gwtjsonrpc.client.RemoteJsonException;
import org.rapla.rest.gwtjsonrpc.client.ServerUnavailableException;
import org.rapla.rest.gwtjsonrpc.client.event.RpcCompleteEvent;
import org.rapla.rest.gwtjsonrpc.client.event.RpcStartEvent;
import org.rapla.rest.gwtjsonrpc.common.AsyncCallback;
import org.rapla.rest.gwtjsonrpc.common.JsonConstants;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.user.client.rpc.InvocationException;
import com.google.gwt.user.client.rpc.StatusCodeException;
import com.google.gwt.xhr.client.XMLHttpRequest;

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
     * As GWT will undoubtedly introduce support for the native JSON parser in
     * the {@link com.google.gwt.json.client.JSONParser JSONParser} class, this
     * code should be reevaluated to possibly use the GWT parser reference.
     * 
     * @return a javascript function with the fastest available JSON parser
     * @see "http://wiki.ecmascript.org/doku.php?id=es3.1:json_support"
     */
    private static native JavaScriptObject selectJsonParser()
    /*-{
		if ($wnd.JSON && typeof $wnd.JSON.parse === 'function')
			return $wnd.JSON.parse;
		else
			return function(expr) {
				return eval('(' + expr + ')');
			};
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
            if (token != null)
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

    public static class SynchronousXHR extends XMLHttpRequest {

        protected SynchronousXHR() {
        }

        public native final void synchronousOpen(String method, String uri)
        /*-{
			this.open(method, uri, false);
        }-*/;
        
        public native final void setTimeout(int timeoutP)
        /*-{
            this.timeout = timeoutP;
        }-*/;
    }

    public T sendSynchronized() throws Exception
    {
        requestId = ++lastRequestId;
        final StringBuilder body = new StringBuilder();
        body.append("{\"jsonrpc\":\"2.0\",\"method\":\"");
        body.append(methodName);
        body.append("\",\"params\":");
        body.append(requestParams);
        body.append(",\"id\":").append(requestId);
        body.append("}");

        String url = proxy.getServiceEntryPoint();

        SynchronousXHR request = (SynchronousXHR) SynchronousXHR.create();
        //request.setTimeout( (int)wait);
        String httpMethod = "POST";
        request.synchronousOpen(httpMethod, url);
        // request.open(httpMethod, url);
        // open(request, "post", url);
        request.setRequestHeader("Content-Type", JsonConstants.JSONRPC20_REQ_CT);
        request.setRequestHeader("Accept", JsonConstants.JSONRPC20_ACCEPT_CTS);

        if (token != null)
        {
            request.setRequestHeader("Authorization", "Bearer " + token);
        }
        
        String requestData = body.toString();
        request.send(requestData);
        String contentType = request.getResponseHeader("Content-Type");
        String statusText = request.getStatusText();
        String responseText = request.getResponseText();
        int sc = request.getStatus();
        CallbackContainer callbackContainer = new CallbackContainer();
        this.callback = callbackContainer;
        processResponse(sc, responseText, statusText, contentType);
        if (callbackContainer.caught == null)
            return callbackContainer.result;
        if(callbackContainer.caught instanceof Exception) {
        	throw (Exception)callbackContainer.caught;
        }
        throw new Exception(callbackContainer.caught);
    }
    class CallbackContainer implements AsyncCallback<T>
    {
        Throwable caught;
        T result;
        @Override
        public void onFailure(Throwable caught) {
            this.caught = caught;
        }

        @Override
        public void onSuccess(T result) {
            this.result = result;
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

    protected static int lastRequestId = 0;

    protected int requestId;

    @Override
    public void onResponseReceived(final Request req, final Response rsp) {
        final int sc = rsp.getStatusCode();
        final String responseText = rsp.getText();
        final String statusText = rsp.getStatusText();
        String contentType = rsp.getHeader("Content-Type");
        processResponse(sc, responseText, statusText, contentType);
    }

    protected void processResponse(final int sc, final String responseText, final String statusText, String contentType) {
        if (isJsonBody(contentType)) {
            final RpcResult r;
            try {
                r = parse(jsonParser, responseText);
            } catch (RuntimeException e) {
                RpcCompleteEvent.fire(this);
                callback.onFailure(new InvocationException("Bad JSON response: " + e));
                return;
            }

            if (r.error() != null) {
            	Exception e = null;
            	final ExceptionDeserializer exceptionDeserializer = proxy.getExceptionDeserializer();
				if(exceptionDeserializer != null){
            		final RpcError error = r.error();
					final Data data = error.data();
					final String exception = data != null ? data.exception() : null;
					final String message = error.message();
					String[] paramObj = data.params();
					final List<String> parameter = paramObj != null ? Arrays.asList(paramObj) : null;
					e = exceptionDeserializer.deserialize(exception, message, parameter);
            	}
				if(e == null) {
					final String errmsg = r.error().message();
					e=new RemoteJsonException(errmsg, r.error().code(),
	                        new JSONObject(r.error()).get("data"));
				}
                RpcCompleteEvent.fire(this);
                callback.onFailure(e);
                return;
            }

            if (sc == Response.SC_OK) {
                RpcCompleteEvent.fire(this);
                JsonUtil.invoke(resultDeserializer, callback, r);
                return;
            }
        }

        if (sc == Response.SC_OK) {
            RpcCompleteEvent.fire(this);
            callback.onFailure(new InvocationException("No JSON response"));
        } else {
            RpcCompleteEvent.fire(this);

            callback.onFailure(new StatusCodeException(sc, statusText));
        }
    }

    protected static boolean isJsonBody(String type) {
        if (type == null) {
            return false;
        }
        int semi = type.indexOf(';');
        if (semi >= 0) {
            type = type.substring(0, semi).trim();
        }
        return JsonConstants.JSONRPC20_ACCEPT_CTS.contains(type);
    }

    /**
     * Call a JSON parser javascript function to parse an encoded JSON string.
     *
     * @param parser
     *            a javascript function
     * @param json
     *            encoded JSON text
     * @return the parsed data
     * @see #jsonParser
     */
    private static final native RpcResult parse(JavaScriptObject parserFunction,
            String json)
    /*-{
    return parserFunction(json);
  }-*/;

    private static class RpcResult extends JavaScriptObject {
        protected RpcResult() {
        }

        final native RpcError error()/*-{ return this.error; }-*/;

    }

    private static class RpcError extends JavaScriptObject {
        protected RpcError() {
        }

        final native String message()/*-{ return this.message; }-*/;

        final native int code()/*-{ return this.code; }-*/;
        
        final native Data data()/*-{ return this.data}-*/;
    }
    
    private static class Data extends JavaScriptObject{
    	
    	protected Data() {
    	}
    	
    	final native String exception()/*-{return this.exception}-*/;

		final native String[] params()/*-{return this.params}-*/;
    }
}
