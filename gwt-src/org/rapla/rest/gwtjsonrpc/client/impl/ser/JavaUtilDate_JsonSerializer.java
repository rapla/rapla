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

import java.util.Date;

import org.rapla.components.util.ParseDateException;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.rest.gwtjsonrpc.client.impl.JsonSerializer;
import org.rapla.rest.gwtjsonrpc.client.impl.ResultDeserializer;

import com.google.gwt.core.client.JavaScriptObject;

/** Default serialization for a {@link java.util.Date}. */
public final class JavaUtilDate_JsonSerializer extends
    JsonSerializer<java.util.Date> implements
    ResultDeserializer<java.util.Date> {
  public static final JavaUtilDate_JsonSerializer INSTANCE =
      new JavaUtilDate_JsonSerializer();
  public static final javax.inject.Provider<JavaUtilDate_JsonSerializer> INSTANCE_PROVIDER = new javax.inject.Provider<JavaUtilDate_JsonSerializer>(){
      public JavaUtilDate_JsonSerializer get(){return INSTANCE;} 
  };

  @Override
  public java.util.Date fromJson(final Object o) {
    if (o != null) {
    	Date date;
		try {
			date = SerializableDateTimeFormat.INSTANCE.parseTimestamp((String)o);
		} catch (ParseDateException e) {
			throw new IllegalArgumentException("Invalid date format: " +  o);
		}
    	return date;
    }
    return null;
  }

  @Override
  public void printJson(final StringBuilder sb, final java.util.Date o) {
    sb.append('"');
    String string = SerializableDateTimeFormat.INSTANCE.formatTimestamp( o);
    sb.append(string);
    sb.append('"');
  }


  @Override
  public Date fromResult(JavaScriptObject responseObject) {
    return fromJson(PrimitiveResultDeserializers.stringResult(responseObject));
  }
}
