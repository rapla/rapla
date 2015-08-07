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

import org.rapla.rest.gwtjsonrpc.client.ExceptionDeserializer;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.rpc.InvocationException;
import com.google.gwt.user.client.rpc.RpcRequestBuilder;
import com.google.gwt.user.client.rpc.ServiceDefTarget;

/**
 * Base class for generated RemoteJsonService implementations.
 * <p>
 * At runtime <code>GWT.create(Foo.class)</code> returns a subclass of this
 * class, implementing the Foo and {@link ServiceDefTarget} interfaces.
 */
public abstract class AbstractJsonProxy implements ServiceDefTarget {
  /** URL of the service implementation. */
  String url;
  private static String token;
  private static EntryPointFactory serviceEntryPointFactory; 
  private static ExceptionDeserializer exceptionDeserializer;
  
  public static EntryPointFactory getServiceEntryPointFactory() {
    return serviceEntryPointFactory;
  }

  public static void setServiceEntryPointFactory(EntryPointFactory serviceEntryPointFactory) {
      AbstractJsonProxy.serviceEntryPointFactory = serviceEntryPointFactory;
  }
  
	public static void setExceptionDeserializer(ExceptionDeserializer exceptionDeserializer) {
		AbstractJsonProxy.exceptionDeserializer = exceptionDeserializer;
	}  
	
	public ExceptionDeserializer getExceptionDeserializer() {
		return exceptionDeserializer;
	}

@Override
  public String getServiceEntryPoint() {
    return url;
  }

  @Override
  public void setServiceEntryPoint(final String address) {
    url = address;
  }

  @Override
  public String getSerializationPolicyName() {
    return "jsonrpc";
  }

  @Override
  public void setRpcRequestBuilder(RpcRequestBuilder builder) {
    if (builder != null)
      throw new UnsupportedOperationException(
          "A RemoteJsonService does not use the RpcRequestBuilder, so this method is unsupported.");
    /**
     * From the gwt docs:
     * 
     * Calling this method with a null value will reset any custom behavior to
     * the default implementation.
     * 
     * If builder == null, we just ignore this invocation.
     */
  }

  protected <T> void doInvoke(final String methodName, final String reqData,
      final ResultDeserializer<T> ser, final FutureResultImpl<T> cb)
      throws InvocationException {
    if ( url == null &&serviceEntryPointFactory != null)
    {
        url = serviceEntryPointFactory.getEntryPoint( getClass());
    }

    if (url == null) {
        throw new NoServiceEntryPointSpecifiedException();
        
    }
    JsonCall<T> newJsonCall = newJsonCall(this, methodName, reqData, ser);
	cb.setCall( newJsonCall);
	if ( token != null )
	{
	    newJsonCall.setToken( token );
	}
  }

  protected abstract <T> JsonCall<T> newJsonCall(AbstractJsonProxy proxy,
      final String methodName, final String reqData,
      final ResultDeserializer<T> ser);

  protected static native JavaScriptObject hostPageCacheGetOnce(String name)
  /*-{ var r = $wnd[name];$wnd[name] = null;return r ? {result: r} : null; }-*/;

  protected static native JavaScriptObject hostPageCacheGetMany(String name)
  /*-{ return $wnd[name] ? {result : $wnd[name]} : null; }-*/;

  public static void setAuthThoken(String token) {
      AbstractJsonProxy.token = token;
  }
}
