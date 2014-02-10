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

package com.google.gwtjsonrpc.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Declare an RPC call as caching its return value in the host page.
 * <p>
 * This is typically useful to allow the server to insert a JSON object into the
 * host page as the host page is sent to the client, permitting some of the
 * initial RPCs required by the module's <code>onModuleLoad()</code> function to
 * complete immediately, without waiting for a round-trip to the server.
 * <p>
 * By default <code>once = true</code>, causing the host page JSON object to be
 * deleted from the window during its first access. This allows subsequent RPC
 * calls to round-trip to the server.
 * <p>
 * If the host page variable is not defined by the server the call degrades into
 * a standard RPC. In some applications this may make it easier to debug in
 * hosted mode, where the static HTML is loaded by default, rather than through
 * a servlet which generates the hosted page on the fly.
 */
@Target(ElementType.METHOD)
public @interface HostPageCache {
  /** Name of the JavaScript global variable the value is cached in. */
  String name();

  /** True deletes the global variable after its first access. */
  boolean once() default true;
}
