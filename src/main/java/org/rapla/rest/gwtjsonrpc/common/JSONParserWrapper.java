package org.rapla.rest.gwtjsonrpc.common;

import java.lang.reflect.Type;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.entities.configuration.internal.RaplaMapImpl;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.FieldNamingStrategy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.ConstructorConstructor;
import com.google.gson.internal.Excluder;
import com.google.gson.internal.bind.MapTypeAdapterFactory;
import com.google.gson.internal.bind.ReflectiveTypeAdapterFactory;
import com.google.gson.reflect.TypeToken;

public class JSONParserWrapper {

	/** Create a default GsonBuilder with some extra types defined. */
	  public static GsonBuilder defaultGsonBuilder() {
	    final GsonBuilder gb = new GsonBuilder();
	    gb.registerTypeAdapter(java.util.Set.class,
	        new InstanceCreator<java.util.Set<Object>>() {
	          @Override
	          public Set<Object> createInstance(final Type arg0) {
	            return new LinkedHashSet<Object>();
	          }
	        });
	    Map<Type, InstanceCreator<?>> instanceCreators = new LinkedHashMap<Type,InstanceCreator<?>>();
	    instanceCreators.put(Map.class, new InstanceCreator<Map>() {
            public Map createInstance(Type type) {
                return new LinkedHashMap();
            }
        });
		ConstructorConstructor constructorConstructor = new ConstructorConstructor(instanceCreators);
	    FieldNamingStrategy fieldNamingPolicy = FieldNamingPolicy.IDENTITY;
	    Excluder excluder = Excluder.DEFAULT;
	    final ReflectiveTypeAdapterFactory reflectiveTypeAdapterFactory = new ReflectiveTypeAdapterFactory(constructorConstructor, fieldNamingPolicy, excluder);
	    gb.registerTypeAdapterFactory(new MapTypeAdapterFactory(constructorConstructor, false));
	    gb.registerTypeAdapterFactory(new MyAdaptorFactory(reflectiveTypeAdapterFactory));
	    gb.registerTypeAdapter(java.util.Date.class, new GmtDateTypeAdapter());
	   
	    GsonBuilder configured = gb.disableHtmlEscaping().setPrettyPrinting();
	    return configured;
	  }
	  
	  
	  public static class GmtDateTypeAdapter implements JsonSerializer<Date>, JsonDeserializer<Date> {
			
			private GmtDateTypeAdapter() {
			}

			@Override
			public synchronized JsonElement serialize(Date date, Type type,	JsonSerializationContext jsonSerializationContext) {
				String timestamp = SerializableDateTimeFormat.INSTANCE.formatTimestamp(date);
				return new JsonPrimitive(timestamp);
			}

			@Override
			public synchronized Date deserialize(JsonElement jsonElement, Type type,JsonDeserializationContext jsonDeserializationContext) {
				String asString = jsonElement.getAsString();
				try {
					Date timestamp = SerializableDateTimeFormat.INSTANCE.parseTimestamp(asString);
					return timestamp;
				} catch (Exception e) {
					throw new JsonSyntaxException(asString, e);
				}
			}
		}

	  public static class MyAdaptorFactory implements TypeAdapterFactory
	    {
	    	ReflectiveTypeAdapterFactory reflectiveTypeAdapterFactory;
	    	public MyAdaptorFactory(ReflectiveTypeAdapterFactory reflectiveTypeAdapterFactory) {
	    		this.reflectiveTypeAdapterFactory = reflectiveTypeAdapterFactory;
	    	}

			public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
				Class<? super T> raw = type.getRawType();
				if (!RaplaMapImpl.class.isAssignableFrom(raw)) {
				      return null; // it's a primitive!
				}
				return reflectiveTypeAdapterFactory.create(gson, type);
			}
	    }
	  
	

}



