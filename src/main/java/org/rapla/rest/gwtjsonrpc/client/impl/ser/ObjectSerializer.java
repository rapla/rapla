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

package org.rapla.rest.gwtjsonrpc.client.impl.ser;

import org.rapla.rest.gwtjsonrpc.client.impl.JsonSerializer;
import org.rapla.rest.gwtjsonrpc.client.impl.ResultDeserializer;

import com.google.gwt.core.client.JavaScriptObject;

/** Base class for generated JsonSerializer implementations. */
public abstract class ObjectSerializer<T extends Object> extends
    JsonSerializer<T> implements ResultDeserializer<T> {
  @Override
  public void printJson(final StringBuilder sb, final Object o) {
    sb.append("{");
    printJsonImpl(0, sb, o);
    sb.append("}");
  }

  protected abstract int printJsonImpl(int field, StringBuilder sb, Object o);

  @Override
  public T fromResult(JavaScriptObject responseObject) {
    final JavaScriptObject result = objectResult(responseObject);
    return result == null ? null : fromJson(result);
  }

  static native JavaScriptObject objectResult(JavaScriptObject responseObject)
  /*-{ return responseObject.result; }-*/;
}
