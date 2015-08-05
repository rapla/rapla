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
import com.google.gwt.core.client.JsonUtils;

/** Default serialization for a String. */
public final class JavaLangString_JsonSerializer extends
    JsonSerializer<java.lang.String> implements
    ResultDeserializer<java.lang.String> {
  public static final JavaLangString_JsonSerializer INSTANCE =
      new JavaLangString_JsonSerializer();
  public static final javax.inject.Provider<JsonSerializer<java.lang.String>> INSTANCE_PROVIDER = new javax.inject.Provider<JsonSerializer<java.lang.String>>(){
      public JsonSerializer<java.lang.String> get(){return INSTANCE;} 
  };

  @Override
  public java.lang.String fromJson(final Object o) {
    return (String) o;
  }

  @Override
  public void printJson(final StringBuilder sb, final java.lang.String o) {
    sb.append(JsonUtils.escapeValue(o));
  }

  @Override
  public String fromResult(JavaScriptObject responseObject) {
    return PrimitiveResultDeserializers.stringResult(responseObject);
  }
}
