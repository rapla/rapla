package org.rapla.rest.jsonpatch.mergepatch.server;

import java.util.Iterator;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public abstract class JsonMergePatch 
{
	protected final JsonElement origPatch;
	
	/**
	* Protected constructor
	*
	* <p>Only necessary for serialization purposes. The patching process
	* itself never requires the full node to operate.</p>
	*
	* @param node the original patch node
	*/
	protected JsonMergePatch(final JsonElement node)
	{
	    origPatch = node;
	}
	
	public abstract JsonElement apply(final JsonElement input)	    throws JsonPatchException;
	
	public static JsonMergePatch fromJson(final JsonElement input)
	    throws JsonPatchException
	{
		if ( input.isJsonPrimitive())
		{
			throw new JsonPatchException("Only json containers are supported");
		}
	    return input.isJsonArray() ? new ArrayMergePatch(input)
	        : new ObjectMergePatch(input);
	}
	
	/**
	* Clear "null values" from a JSON value
	*
	* <p>Non container values are unchanged. For arrays, null elements are
	* removed. From objects, members whose values are null are removed.</p>
	*
	* <p>This method is recursive, therefore arrays within objects, or objects
	* within arrays, or arrays within arrays etc are also affected.</p>
	*
	* @param node the original JSON value
	* @return a JSON value without null values (see description)
	*/
	protected static JsonElement clearNulls(final JsonElement node)
	{
	    if (node.isJsonPrimitive())
	        return node;
	
	    return node.isJsonArray() ? clearNullsFromArray((JsonArray)node)  : clearNullsFromObject((JsonObject)node);
	}
	
	private static JsonElement clearNullsFromArray(final JsonArray node)
	{
	    final JsonArray ret = new JsonArray();
	
	    /*
	* Cycle through array elements. If the element is a null node itself,
	* skip it. Otherwise, add a "cleaned up" element to the result.
	*/
	    for (final JsonElement element: node)
	        if (!element.isJsonNull())
	            ret.add(clearNulls(element));
	
	    return ret;
	}
	
	private static JsonElement clearNullsFromObject(final JsonObject node)
	{
	    final JsonObject ret = new JsonObject();
	    final Iterator<Map.Entry<String, JsonElement>> iterator
	        = node.entrySet().iterator();
	
	    Map.Entry<String, JsonElement> entry;
	    JsonElement value;
	
	    /*
	* When faces with an object, cycle through this object's entries.
	*
	* If the value of the entry is a JSON null, don't include it in the
	* result. If not, include a "cleaned up" value for this key instead of
	* the original element.
	*/
	    while (iterator.hasNext()) {
	        entry = iterator.next();
	        value = entry.getValue();
	        if (value != null) {
				String key = entry.getKey();
				JsonElement clearNulls = clearNulls(value);
				ret.add(key, clearNulls);
			}
	    }
	
	    return ret;
	}
	
	public String toString()
	{
		return origPatch.toString();
	}

}