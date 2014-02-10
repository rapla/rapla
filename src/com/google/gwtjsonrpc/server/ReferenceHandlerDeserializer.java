package com.google.gwtjsonrpc.server;

import java.lang.reflect.Type;

import org.rapla.entities.storage.internal.ReferenceHandler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class ReferenceHandlerDeserializer  implements JsonSerializer<ReferenceHandler> {

	@Override
	public JsonElement serialize(ReferenceHandler src, Type typeOfSrc,
			JsonSerializationContext context) {
		JsonObject obj = new JsonObject();
		for (String key: src.getReferenceKeys())
		{
			JsonArray value = new JsonArray();
			for (Comparable id : src.getIds( key ))
			{
				value.add( new JsonPrimitive(id.toString()));
			}
			obj.add(key, value);
		}
		return obj;
	}
	
}
