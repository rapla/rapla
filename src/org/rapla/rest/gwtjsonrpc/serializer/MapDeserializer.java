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

package org.rapla.rest.gwtjsonrpc.serializer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.InstanceCreator;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class MapDeserializer implements JsonDeserializer<Map<Object, Object>>,
    JsonSerializer<Map<Object, Object>>, InstanceCreator<Map<Object, Object>> {
  @Override
  public Map<Object, Object> createInstance(final Type type) {
    return new HashMap<Object, Object>();
  }

  @Override
  public Map<Object, Object> deserialize(final JsonElement json,
      final Type typeOfT, final JsonDeserializationContext context)
      throws JsonParseException {
    final Type kt = ((ParameterizedType) typeOfT).getActualTypeArguments()[0];
    final Type vt = ((ParameterizedType) typeOfT).getActualTypeArguments()[1];

    if (json.isJsonNull()) {
      return null;
    }

    if (kt == String.class) {
      if (!json.isJsonObject()) {
        throw new JsonParseException("Expected object for map type");
      }
      final JsonObject p = (JsonObject) json;
      final Map<Object, Object> r = createInstance(typeOfT);
      for (final Map.Entry<String, JsonElement> e : p.entrySet()) {
        final Object v = context.deserialize(e.getValue(), vt);
        r.put(e.getKey(), v);
      }
      return r;
    } else {
      if (!json.isJsonArray()) {
        throw new JsonParseException("Expected array for map type");
      }

      final JsonArray p = (JsonArray) json;
      final Map<Object, Object> r = createInstance(typeOfT);
      for (int n = 0; n < p.size();) {
        final Object k = context.deserialize(p.get(n++), kt);
        final Object v = context.deserialize(p.get(n++), vt);
        r.put(k, v);
      }
      return r;
    }
  }

  @Override
  public JsonElement serialize(final Map<Object, Object> src,
      final Type typeOfSrc, final JsonSerializationContext context) {
    final Type kt = ((ParameterizedType) typeOfSrc).getActualTypeArguments()[0];
    final Type vt = ((ParameterizedType) typeOfSrc).getActualTypeArguments()[1];

    if (src == null) {
      return JsonNull.INSTANCE;
    }

    if (kt == String.class) {
      final JsonObject r = new JsonObject();
      for (final Map.Entry<Object, Object> e : src.entrySet()) {
        r.add(e.getKey().toString(), context.serialize(e.getValue(), vt));
      }
      return r;
    } else {
      final JsonArray r = new JsonArray();
      for (final Map.Entry<Object, Object> e : src.entrySet()) {
        r.add(context.serialize(e.getKey(), kt));
        r.add(context.serialize(e.getValue(), vt));
      }
      return r;
    }
  }
}
