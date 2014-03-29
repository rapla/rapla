/*
* Copyright (c) 2014, Francis Galiegue (fgaliegue@gmail.com)
*
* This software is dual-licensed under:
*
* - the Lesser General Public License (LGPL) version 3.0 or, at your option, any
* later version;
* - the Apache Software License (ASL) version 2.0.
*
* The text of both licenses is available under the src/resources/ directory of
* this project (under the names LGPL-3.0.txt and ASL-2.0.txt respectively).
*
* Direct link to the sources:
*
* - LGPL 3.0: https://www.gnu.org/licenses/lgpl-3.0.txt
* - ASL 2.0: http://www.apache.org/licenses/LICENSE-2.0.txt
*/

package org.rapla.rest.jsonpatch.mergepatch.server;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
* Merge patch for a JSON Object
*
* <p>This only takes care of the top level, and delegates to other {@link
* JsonMergePatch} instances for deeper levels.</p>
*/
final class ObjectMergePatch
    extends JsonMergePatch
{
    private final Map<String, JsonElement> fields;
    private final Set<String> removals;

    ObjectMergePatch(final JsonElement content)
    {
        super(content);
        fields = asMap(content);
        removals = new HashSet<String>();

        for (final Map.Entry<String, JsonElement> entry: fields.entrySet())
            if (entry.getValue() == null)
                removals.add(entry.getKey());

        fields.keySet().removeAll(removals);
    }

    @Override
    public JsonElement apply(final JsonElement input)     throws JsonPatchException
    {
        if (!input.isJsonObject())
            return mapToNode(fields);

        final Map<String, JsonElement> map = asMap(input);

        // Remove all entries which must be removed
        map.keySet().removeAll(removals);

        // Now cycle through what is left
        String memberName;
        JsonElement patchNode;

        for (final Map.Entry<String, JsonElement> entry: map.entrySet()) {
            memberName = entry.getKey();
            patchNode = fields.get(memberName);

            // Leave untouched if no mention in the patch
            if (patchNode == null)
                continue;

            // If the patch node is a primitive type, replace in the result.
            // Reminder: there cannot be a JSON null anymore
            if (patchNode.isJsonPrimitive()) {
                entry.setValue(patchNode); // no need for .deepCopy()
                continue;
            }

            final JsonMergePatch patch = JsonMergePatch.fromJson(patchNode);
            entry.setValue(patch.apply(entry.getValue()));
        }

        // Finally, if there are members in the patch not present in the input,
        // fill in members
        for (final String key: difference(fields.keySet(), map.keySet()))
            map.put(key, clearNulls(fields.get(key)));

        return mapToNode(map);
    }

    private Set<String> difference(Set<String> keySet, Set<String> keySet2) {
    	LinkedHashSet<String> result = new LinkedHashSet<String>();
    	for ( String key:keySet)
    	{
    		if ( !keySet2.contains(key))
    		{
    			result.add( key);
    		}
    	}
    	return result;
	}

	private Map<String, JsonElement> asMap(JsonElement input) {
    	Map<String,JsonElement> result = new LinkedHashMap<String,JsonElement>();
    	for ( Entry<String, JsonElement> entry :input.getAsJsonObject().entrySet())
    	{
    		JsonElement value = entry.getValue();
			String key = entry.getKey();
			result.put( key,value);
    	}
		return result;
	}

	private static JsonElement mapToNode(final Map<String, JsonElement> map)
    {
        final JsonObject ret = new JsonObject();
        for ( String key: map.keySet())
        {
        	JsonElement value = map.get( key);
			ret.add(key, value);
        }
        return ret;
    }
}
