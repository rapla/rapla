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

package org.rapla.rest.gwtjsonrpc.common;

/**
 * Marker interface for JSON based RPC. Should be replaced with a marker annotation when generator supports annotations
 * <p>
 * Application service interfaces should extend this interface:
 *
 * <pre>
 * public interface FooService extends RemoteJsonService ...
 * </pre>
 * <p>
 * and declare each method as returning void and accepting {@link org.rapla.rest.gwtjsonrpc.common.AsyncCallback}
 * as the final parameter, with a concrete type specified as the result type:
 *
 * <pre>
 * public interface FooService extends RemoteJsonService {
 *   public void fooItUp(AsyncCallback&lt;ResultType&gt; callback);
 * }
 * </pre>
 * <p>
 * Instances of the interface can be obtained in the client and configured to
 * reference a particular JSON server:
 *
 * <pre>
 * FooService mysvc = GWT.create(FooService.class);
 * ((ServiceDefTarget) mysvc).setServiceEntryPoint(GWT.getModuleBaseURL()
 *     + &quot;FooService&quot;);
 *</pre>
 * <p>
 * Calling conventions match the JSON-RPC 1.1 working draft from 7 August 2006
 * (<a href="http://json-rpc.org/wd/JSON-RPC-1-1-WD-20060807.html">draft</a>).
 * Only positional parameters are supported.
 * <p>
 * JSON service callbacks may also be declared; see
 * {@link com.google.gwtjsonrpc.client.CallbackHandle}.
 */
public interface RemoteJsonService {
}
