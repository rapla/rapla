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

package org.rapla.rest.gwtjsonrpc.server;

import java.lang.reflect.Type;

import com.google.gson.InstanceCreator;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

final class CallDeserializer implements
    JsonDeserializer<ActiveCall>, InstanceCreator<ActiveCall> {
  private final ActiveCall req;
  private final JsonServlet server;

  CallDeserializer(final ActiveCall call, final JsonServlet jsonServlet) {
    req = call;
    server = jsonServlet;
  }

  @Override
  public ActiveCall createInstance(final Type type) {
    return req;
  }

  @Override
  public ActiveCall deserialize(final JsonElement json, final Type typeOfT,
      final JsonDeserializationContext context) throws JsonParseException,
      NoSuchRemoteMethodException {
    if (!json.isJsonObject()) {
      throw new JsonParseException("Expected object");
    }

    final JsonObject in = json.getAsJsonObject();
    
    
    req.id = in.get("id");

    final JsonElement jsonrpc = in.get("jsonrpc");
    final JsonElement version = in.get("version");
    if (isString(jsonrpc) && version == null) {
      final String v = jsonrpc.getAsString();
      if ("2.0".equals(v)) {
        req.versionName = "jsonrpc";
        req.versionValue = jsonrpc;
      } else {
        throw new JsonParseException("Expected jsonrpc=2.0");
      }

    } else if (isString(version) && jsonrpc == null) {
      final String v = version.getAsString();
      if ("1.1".equals(v)) {
        req.versionName = "version";
        req.versionValue = version;
      } else {
        throw new JsonParseException("Expected version=1.1");
      }
    } else {
      throw new JsonParseException("Expected version=1.1 or jsonrpc=2.0");
    }

    final JsonElement method = in.get("method");
    if (!isString(method)) {
      throw new JsonParseException("Expected method name as string");
    }

    String asString = method.getAsString();
    req.method = server.lookupMethod(asString);
    if (req.method == null) {
      throw new NoSuchRemoteMethodException(server.getInterfaceClass() + "." + asString);
    }

    final Type[] paramTypes = req.method.getParamTypes();
    final JsonElement params = in.get("params");
    if (params != null) {
      if (!params.isJsonArray()) {
        throw new JsonParseException("Expected params array");
      }

      final JsonArray paramsArray = params.getAsJsonArray();
      if (paramsArray.size() != paramTypes.length) {
        throw new JsonParseException("Expected " + paramTypes.length
            + " parameter values in params array");
      }

      final Object[] r = new Object[paramTypes.length];
      for (int i = 0; i < r.length; i++) {
        final JsonElement v = paramsArray.get(i);
        if (v != null) {
          r[i] = context.deserialize(v, paramTypes[i]);
        }
      }
      req.params = r;
    } else {
      if (paramTypes.length != 0) {
        throw new JsonParseException("Expected params array");
      }
      req.params = JsonServlet.NO_PARAMS;
    }

    return req;
  }

  private static boolean isString(final JsonElement e) {
    return e != null && e.isJsonPrimitive()
        && e.getAsJsonPrimitive().isString();
  }
}
