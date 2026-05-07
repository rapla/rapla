package org.rapla.rest.gson;

import com.google.gson.*;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;


/*
* Modified original version of Francis Galiegue (fgaliegue@gmail.com)
* which software is dual-licensed under:
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

public abstract class JsonMergePatch
{
    protected final JsonElement origPatch;

    protected JsonMergePatch(final JsonElement node)
    {
        origPatch = node;
    }

    public abstract JsonElement apply(final JsonElement input);

    public static JsonMergePatch fromJson(final JsonElement input)
    {
        if ( input.isJsonPrimitive())
        {
            throw new IllegalArgumentException("Can't patch primitives. Only json containers are supported");
        }
        if (input.isJsonArray())
        {
            final JsonElement content = clearNulls(input);
            return new JsonMergePatch(input) {
                @Override
                public JsonElement apply(final JsonElement input)
                {
                    return content;
                }
            };
        }
        else
        {
            return new ObjectMergePatch(input);
        }
    }

    static final class ObjectMergePatch  extends JsonMergePatch
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
        public JsonElement apply(final JsonElement input)
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

                final JsonMergePatch patch = fromJson(patchNode);
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
            for ( Map.Entry<String, JsonElement> entry :input.getAsJsonObject().entrySet())
            {
                JsonElement value = entry.getValue();
                String key = entry.getKey();
                result.put( key,value);
            }
            return result;
        }


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