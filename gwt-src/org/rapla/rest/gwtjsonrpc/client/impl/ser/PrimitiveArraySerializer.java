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

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsonUtils;

public class PrimitiveArraySerializer {
  public static final PrimitiveArraySerializer INSTANCE =
      new PrimitiveArraySerializer();
  
  public static final javax.inject.Provider<PrimitiveArraySerializer> INSTANCE_PROVIDER = new javax.inject.Provider<PrimitiveArraySerializer>(){
      public PrimitiveArraySerializer get(){return INSTANCE;} 
  };


  private void printJsonWithToString(final StringBuilder sb, final Object[] o) {
    sb.append('[');
    for (int i = 0, n = o.length; i < n; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(o[i] != null ? o[i].toString() : JsonSerializer.JS_NULL);
    }
    sb.append(']');
  }

  // Serialisation of Boxed Primitives
  public void printJson(final StringBuilder sb, final Boolean[] o) {
    printJsonWithToString(sb, o);
  }

  public void printJson(final StringBuilder sb, final Byte[] o) {
    printJsonWithToString(sb, o);
  }

  public void printJson(final StringBuilder sb, final Character[] o) {
    sb.append('[');
    for (int i = 0, n = o.length; i < n; i++) {
      if (i > 0) {
        sb.append(',');
      }
      if (o[i] != null) {
        sb.append(JsonUtils.escapeValue(String.valueOf(o[i])));
      } else
        sb.append(JsonSerializer.JS_NULL);
    }
    sb.append(']');
  }

  public void printJson(final StringBuilder sb, final Double[] o) {
    printJsonWithToString(sb, o);
  }

  public void printJson(final StringBuilder sb, final Float[] o) {
    printJsonWithToString(sb, o);
  }

  public void printJson(final StringBuilder sb, final Integer[] o) {
    printJsonWithToString(sb, o);
  }

  public void printJson(final StringBuilder sb, final Short[] o) {
    printJsonWithToString(sb, o);
  }

  // Serialisation of Primitives
  public void printJson(final StringBuilder sb, final boolean[] o) {
    sb.append('[');
    for (int i = 0, n = o.length; i < n; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(o[i]);
    }
    sb.append(']');
  }

  public void printJson(final StringBuilder sb, final byte[] o) {
    sb.append('[');
    for (int i = 0, n = o.length; i < n; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(o[i]);
    }
    sb.append(']');
  }

  public void printJson(final StringBuilder sb, final char[] o) {
    sb.append('[');
    for (int i = 0, n = o.length; i < n; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(o[i]);
    }
    sb.append(']');
  }

  public void printJson(final StringBuilder sb, final double[] o) {
    sb.append('[');
    for (int i = 0, n = o.length; i < n; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(o[i]);
    }
    sb.append(']');
  }

  public void printJson(final StringBuilder sb, final float[] o) {
    sb.append('[');
    for (int i = 0, n = o.length; i < n; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(o[i]);
    }
    sb.append(']');
  }

  public void printJson(final StringBuilder sb, final int[] o) {
    sb.append('[');
    for (int i = 0, n = o.length; i < n; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(o[i]);
    }
    sb.append(']');
  }

  public void printJson(final StringBuilder sb, final short[] o) {
    sb.append('[');
    for (int i = 0, n = o.length; i < n; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(o[i]);
    }
    sb.append(']');
  }

  // DeSerialisation native getters
  private static final native boolean getBoolean(JavaScriptObject jso, int pos)
  /*-{ return jso[pos]; }-*/;

  private static final native byte getByte(JavaScriptObject jso, int pos)
  /*-{ return jso[pos]; }-*/;

  private static final native String getString(JavaScriptObject jso, int pos)
  /*-{ return jso[pos]; }-*/;

  private static final native double getDouble(JavaScriptObject jso, int pos)
  /*-{ return jso[pos]; }-*/;

  private static final native float getFloat(JavaScriptObject jso, int pos)
  /*-{ return jso[pos]; }-*/;

  private static final native int getInteger(JavaScriptObject jso, int pos)
  /*-{ return jso[pos]; }-*/;

  private static final native short getShort(JavaScriptObject jso, int pos)
  /*-{ return jso[pos]; }-*/;

  // DeSerialisation of boxed primitive arrays
  public void fromJson(final JavaScriptObject jso, final Boolean[] r) {
    for (int i = 0; i < r.length; i++) {
      r[i] = getBoolean(jso, i);
    }
  }

  public void fromJson(final JavaScriptObject jso, final Byte[] r) {
    for (int i = 0; i < r.length; i++) {
      r[i] = getByte(jso, i);
    }
  }

  public void fromJson(final JavaScriptObject jso, final Character[] r) {
    for (int i = 0; i < r.length; i++) {
      r[i] = JsonSerializer.toChar(getString(jso, i));
    }
  }

  public void fromJson(final JavaScriptObject jso, final Double[] r) {
    for (int i = 0; i < r.length; i++) {
      r[i] = getDouble(jso, i);
    }
  }

  public void fromJson(final JavaScriptObject jso, final Float[] r) {
    for (int i = 0; i < r.length; i++) {
      r[i] = getFloat(jso, i);
    }
  }

  public void fromJson(final JavaScriptObject jso, final Integer[] r) {
    for (int i = 0; i < r.length; i++) {
      r[i] = getInteger(jso, i);
    }
  }

  public void fromJson(final JavaScriptObject jso, final Short[] r) {
    for (int i = 0; i < r.length; i++) {
      r[i] = getShort(jso, i);
    }
  }

  // DeSerialisation of primitive arrays
  public void fromJson(final JavaScriptObject jso, final boolean[] r) {
    for (int i = 0; i < r.length; i++) {
      r[i] = getBoolean(jso, i);
    }
  }

  public void fromJson(final JavaScriptObject jso, final byte[] r) {
    for (int i = 0; i < r.length; i++) {
      r[i] = getByte(jso, i);
    }
  }

  public void fromJson(final JavaScriptObject jso, final char[] r) {
    for (int i = 0; i < r.length; i++) {
      r[i] = JsonSerializer.toChar(getString(jso, i));
    }
  }

  public void fromJson(final JavaScriptObject jso, final double[] r) {
    for (int i = 0; i < r.length; i++) {
      r[i] = getDouble(jso, i);
    }
  }

  public void fromJson(final JavaScriptObject jso, final float[] r) {
    for (int i = 0; i < r.length; i++) {
      r[i] = getFloat(jso, i);
    }
  }

  public void fromJson(final JavaScriptObject jso, final int[] r) {
    for (int i = 0; i < r.length; i++) {
      r[i] = getInteger(jso, i);
    }
  }

  public void fromJson(final JavaScriptObject jso, final short[] r) {
    for (int i = 0; i < r.length; i++) {
      r[i] = getShort(jso, i);
    }
  }
}
