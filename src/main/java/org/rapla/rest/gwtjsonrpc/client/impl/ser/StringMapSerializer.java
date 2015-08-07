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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.inject.Provider;

import org.rapla.rest.gwtjsonrpc.client.impl.JsonSerializer;
import org.rapla.rest.gwtjsonrpc.client.impl.ResultDeserializer;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsonUtils;

/**
 * Serialization for a {@link java.util.Map} using only String keys.
 * <p>
 * The JSON representation is a JSON object, the map keys become the property
 * names of the JSON object and the map values are the property values.
 * <p>
 * When deserialized from JSON the Map implementation is always a
 * {@link HashMap}. When serializing to JSON any Map is permitted.
 */
public class StringMapSerializer<V> extends
    JsonSerializer<java.util.Map<String, V>> implements
    ResultDeserializer<java.util.Map<String, V>> {
  private final Provider<JsonSerializer<V>> valueSerializer;

  public StringMapSerializer(final JsonSerializer<V> v) {
    valueSerializer = new SimpleProvider<JsonSerializer<V>>(v);
  }
  
  public StringMapSerializer(final Provider<JsonSerializer<V>> v) {
      valueSerializer = v;
  }

  @Override
  public void printJson(final StringBuilder sb, final java.util.Map<String, V> o) {
    sb.append('{');
    boolean first = true;
    for (final Map.Entry<String, V> e : o.entrySet()) {
      if (first) {
        first = false;
      } else {
        sb.append(',');
      }
      sb.append(JsonUtils.escapeValue(e.getKey()));
      sb.append(':');
      encode(sb, valueSerializer.get(), e.getValue());
    }
    sb.append('}');
  }

  private static <T> void encode(final StringBuilder sb,
      final JsonSerializer<T> serializer, final T item) {
    if (item != null) {
      serializer.printJson(sb, item);
    } else {
      sb.append(JS_NULL);
    }
  }

  @Override
  public java.util.Map<String, V> fromJson(final Object o) {
    if (o == null) {
      return null;
    }

    final JavaScriptObject jso = (JavaScriptObject) o;
    final Map<String, V> r = new LinkedHashMap<String, V>();
    copy(r, jso);
    return r;
  }

  @Override
  public java.util.Map<String, V> fromResult(final JavaScriptObject response) {
    final JavaScriptObject result = ObjectSerializer.objectResult(response);
    return result == null ? null : fromJson(result);
  }

  private native void copy(Map<String, V> r, JavaScriptObject jsObject)
  /*-{
    for (var key in jsObject) {
      this.@org.rapla.rest.gwtjsonrpc.client.impl.ser.StringMapSerializer::copyOne(Ljava/util/Map;Ljava/lang/String;Ljava/lang/Object;)(r, key, jsObject[key]);
    }
  }-*/;

  void copyOne(final Map<String, V> r, final String k, final Object o) {
    r.put(k, valueSerializer.get().fromJson(o));
  }
}
