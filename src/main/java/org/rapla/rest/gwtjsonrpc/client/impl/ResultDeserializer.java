// Copyright 2009 Google Inc.
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

import com.google.gwt.core.client.JavaScriptObject;

/**
 * Inteface class for deserializers of results from JSON RPC calls. Since
 * primitive and array results need to be handled specially, not all results can
 * be deserialized using the standard object serializers.
 * 
 * @param <T> the result type of an RPC call.
 */
public interface ResultDeserializer<T> {
  public T fromResult(JavaScriptObject responseObject);
}
