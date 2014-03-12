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

package com.google.gwtjsonrpc.serializer;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class SqlDateDeserializer implements JsonDeserializer<java.sql.Date>,
    JsonSerializer<java.sql.Date> {
  @Override
  public java.sql.Date deserialize(final JsonElement json, final Type typeOfT,
      final JsonDeserializationContext context) throws JsonParseException {
    if (json.isJsonNull()) {
      return null;
    }
    if (!json.isJsonPrimitive()) {
      throw new JsonParseException("Expected string for date type");
    }
    final JsonPrimitive p = (JsonPrimitive) json;
    if (!p.isString()) {
      throw new JsonParseException("Expected string for date type");
    }
    try {
      return java.sql.Date.valueOf(p.getAsString());
    } catch (IllegalArgumentException e) {
      throw new JsonParseException("Not a date string");
    }
  }

  @Override
  public JsonElement serialize(final java.sql.Date src, final Type typeOfSrc,
      final JsonSerializationContext context) {
    if (src == null) {
      return JsonNull.INSTANCE;
    }
    return new JsonPrimitive(src.toString());
  }
}
