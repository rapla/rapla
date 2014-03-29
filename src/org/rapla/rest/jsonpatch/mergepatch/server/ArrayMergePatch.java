package org.rapla.rest.jsonpatch.mergepatch.server;

import com.google.gson.JsonElement;

final class ArrayMergePatch extends JsonMergePatch
{
	// Always an array
	private final JsonElement content;
	
	ArrayMergePatch(final JsonElement content)
	{
	    super(content);
	    this.content = clearNulls(content);
	}
	
	@Override
	public JsonElement apply(final JsonElement input)   throws JsonPatchException
	{
	    return content;
	}
	
}