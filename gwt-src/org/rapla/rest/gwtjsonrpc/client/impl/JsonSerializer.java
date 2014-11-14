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

package org.rapla.rest.gwtjsonrpc.client.impl;

import com.google.gwt.core.client.JsonUtils;

/**
 * Converter between JSON and Java object representations.
 * <p>
 * Implementors must provide bidirectional conversion, typically using the GWT
 * JavaScriptObject and native JavaScript magic to read the JSON data structure.
 * <p>
 * Most implementations are generated automatically at compile-time by the
 * <code>RemoteJsonServiceProxyGenerator</code>.
 * 
 * @param <T> type of Java class this type works on.
 */
public abstract class JsonSerializer<T> {
  /** Magic constant in JSON to mean the same as Java null. */
  public static final String JS_NULL = "null";

  /**
   * Convert a Java object to JSON text.
   * <p>
   * Implementations should recursively call any nested object or collection at
   * the appropriate time to append the nested item's JSON text.
   * 
   * @param sb the output string buffer the JSON text should be appended onto
   *        the end of.
   * @param o the Java instance being converted. Never null.
   */
  public abstract void printJson(StringBuilder sb, T o);

  /**
   * Convert from JSON (stored as a JavaScriptObject) into a new Java instance.
   * 
   * @param o the JSON object instance; typically this should be downcast to
   *        JavaScriptObject. May be null, in which case null should be returned
   *        instead of an instance.
   * @return null if <code>o</code> was null; otherwise the new object instance
   *         with the data copied over form the JSON representation.
   */
  public abstract T fromJson(Object o);

  /**
   * Utility function to convert a String to safe JSON text.
   * <p>
   * For example, if <code>val = "b\nb"</code> this method returns the value
   * <code>"\"b\\nb\""</code>.
   * <p>
   * Typically called by {@link #printJson(StringBuilder, Object)}, or through
   * {@link JavaLangString_JsonSerializer}.
   * 
   * @param val string text requiring escaping support. Must not be null.
   * @return a JSON literal text value, surrounded with double quotes.
   * @deprecated Use {@link JsonUtils#escapeValue(String)}
   */
  @Deprecated
  public static final String escapeString(String val) {
    return JsonUtils.escapeValue(val);
  }

  /**
   * Escape a single character, without double quotes surrounding it.
   * 
   * @deprecated implementation depends on private method hack. Do not use.
   */
  @Deprecated
  public static final String escapeChar(final char c) {
    return escapeCharImpl(String.valueOf(c));
  }

  @Deprecated
  private static final native String escapeCharImpl(String c) /*-{ return @com.google.gwt.core.client.JsonUtils::escapeValue(Ljava/lang/String;)(c); }-*/;

  /** Return the first character of a string, or '\0' if the string is empty. */
  public static final char toChar(final String val) {
    return val.length() > 0 ? val.charAt(0) : '\0';
  }
}
