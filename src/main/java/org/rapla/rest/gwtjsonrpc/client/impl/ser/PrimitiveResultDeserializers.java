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

package org.rapla.rest.gwtjsonrpc.client.impl.ser;

import org.rapla.rest.gwtjsonrpc.client.impl.JsonSerializer;
import org.rapla.rest.gwtjsonrpc.client.impl.ResultDeserializer;

import com.google.gwt.core.client.JavaScriptObject;

public class PrimitiveResultDeserializers {
  static native boolean booleanResult(JavaScriptObject responseObject)
  /*-{
    return responseObject.result;
  }-*/;

  static native byte byteResult(JavaScriptObject responseObject)
  /*-{
    return responseObject.result;
  }-*/;

  static native String stringResult(JavaScriptObject responseObject)
  /*-{
    return responseObject.result;
  }-*/;

  static char charResult(JavaScriptObject responseObject) {
    return JsonSerializer.toChar(stringResult(responseObject));
  }

  static native double doubleResult(JavaScriptObject responseObject)
  /*-{
    return responseObject.result;
  }-*/;

  static native float floatResult(JavaScriptObject responseObject)
  /*-{
    return responseObject.result;
  }-*/;

  static native int intResult(JavaScriptObject responseObject)
  /*-{
    return responseObject.result;
  }-*/;

  static native short shortResult(JavaScriptObject responseObject)
  /*-{
    return responseObject.result;
  }-*/;

  public static final ResultDeserializer<Boolean> BOOLEAN_INSTANCE =
      new ResultDeserializer<Boolean>() {
        @Override
        public Boolean fromResult(JavaScriptObject responseObject) {
          return booleanResult(responseObject);
        }
      };
  public static final ResultDeserializer<Byte> BYTE_INSTANCE =
      new ResultDeserializer<Byte>() {
        @Override
        public Byte fromResult(JavaScriptObject responseObject) {
          return byteResult(responseObject);
        }
      };
  public static final ResultDeserializer<Character> CHARACTER_INSTANCE =
      new ResultDeserializer<Character>() {
        @Override
        public Character fromResult(JavaScriptObject responseObject) {
          return charResult(responseObject);
        }
      };
  public static final ResultDeserializer<Double> DOUBLE_INSTANCE =
      new ResultDeserializer<Double>() {
        @Override
        public Double fromResult(JavaScriptObject responseObject) {
          return doubleResult(responseObject);
        }
      };
  public static final ResultDeserializer<Float> FLOAT_INSTANCE =
      new ResultDeserializer<Float>() {
        @Override
        public Float fromResult(JavaScriptObject responseObject) {
          return floatResult(responseObject);
        }
      };
  public static final ResultDeserializer<Integer> INTEGER_INSTANCE =
      new ResultDeserializer<Integer>() {
        @Override
        public Integer fromResult(JavaScriptObject responseObject) {
          return intResult(responseObject);
        }
      };
  public static final ResultDeserializer<Short> SHORT_INSTANCE =
      new ResultDeserializer<Short>() {
        @Override
        public Short fromResult(JavaScriptObject responseObject) {
          return shortResult(responseObject);
        }
      };
}
