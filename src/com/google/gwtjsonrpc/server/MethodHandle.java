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

package com.google.gwtjsonrpc.server;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import javax.jws.WebParam;

import com.google.gwtjsonrpc.common.AllowCrossSiteRequest;
import com.google.gwtjsonrpc.common.RemoteJsonService;

/**
 * Pairing of a specific {@link RemoteJsonService} implementation and method.
 */
public class MethodHandle {
  private final RemoteJsonService imp;
  private final Method method;
  private final Type[] parameterTypes;
  private final boolean allowXsrf;
  private String[] parameterNames;
 
  /**
   * Create a new handle for a specific service implementation and method.
   * 
   * @param imp instance of the service all calls will be made on.
   * @param method Java method to invoke on <code>imp</code>. The last parameter
   *        of the method must accept an {@link com.google.gwtjsonrpc.common.AsyncCallback}
   *        and the method must return void.
   */
  MethodHandle(final RemoteJsonService imp, final Method method) {
    this.imp = imp;
    this.method = method;
    this.allowXsrf = method.getAnnotation(AllowCrossSiteRequest.class) != null;

    final Type[] args = method.getGenericParameterTypes();
    Annotation[][] parameterAnnotations = method.getParameterAnnotations();
    parameterNames = new String[args.length - 1];
    for (int i=0;i<args.length -1;i++)
    {
    	Annotation[] annot= parameterAnnotations[i];
		String paramterName = null;
    	for ( Annotation a:annot)
    	{
    		Class<? extends Annotation> annotationType = a.annotationType();
			if ( annotationType.equals( WebParam.class))
    		{
    			paramterName = ((WebParam)a).name();
    		}
    	}
    	if ( paramterName != null)
    	{
    		parameterNames[i] = paramterName;
    	}
    }
    parameterTypes = new Type[args.length - 1];
   
    System.arraycopy(args, 0, parameterTypes, 0, parameterTypes.length);
  }

  /**
   * @return unique name of the method within the service.
   */
  public String getName() {
    return method.getName();
  }

  /** @return an annotation attached to the method's description. */
  public <T extends Annotation> T getAnnotation(final Class<T> t) {
    return method.getAnnotation(t);
  }

  /**
   * @return true if this method requires positional arguments.
   */
  public Type[] getParamTypes() {
    return parameterTypes;
  }
  
  public String[] getParamNames()
  {
  	return parameterNames;
  }

  /** @return true if the method can be called cross-site. */
  public boolean allowCrossSiteRequest() {
    return allowXsrf;
  }

  /**
   * Invoke this method with the specified arguments, updating the callback.
   * 
   * @param arguments arguments to the method. May be the empty array if no
   *        parameters are declared beyond the AsyncCallback, but must not be
   *        null.
   * @param callback the callback the implementation will invoke onSuccess or
   *        onFailure on as it performs its work. Only the last onSuccess or
   *        onFailure invocation matters.
   */
  public void invoke(final Object[] arguments, final ActiveCall callback) {
    try {
      final Object[] p = new Object[arguments.length + 1];
      System.arraycopy(arguments, 0, p, 0, arguments.length);
      p[p.length - 1] = callback;
      method.invoke(imp, p);
    } catch (InvocationTargetException e) {
      final Throwable c = e.getCause();
      if (c != null) {
        callback.onInternalFailure(c);
      } else {
        callback.onInternalFailure(e);
      }
    } catch (IllegalAccessException e) {
      callback.onInternalFailure(e);
    } catch (RuntimeException e) {
      callback.onInternalFailure(e);
    } catch (Error e) {
      callback.onInternalFailure(e);
    }
  }
}
