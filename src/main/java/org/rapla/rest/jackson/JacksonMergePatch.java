package org.rapla.rest.jackson;



import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;


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

public abstract class JacksonMergePatch
{
    static ObjectMapper mapper = new ObjectMapper();
    protected final JsonNode origPatch;

    protected JacksonMergePatch(final JsonNode node)
    {
        origPatch = node;
    }

    public abstract JsonNode apply(final JsonNode input);

    public static JacksonMergePatch fromJson(final JsonNode input)
    {
        if ( input.isValueNode())
        {
            throw new IllegalArgumentException("Can't patch primitives. Only json containers are supported");
        }
        if (input.isArray())
        {
            final JsonNode content = clearNulls(input);
            return new JacksonMergePatch(input) {
                @Override
                public JsonNode apply(final JsonNode input)
                {
                    return content;
                }
            };
        }
        else
        {
            return new ObjectMergePatch((ObjectNode)input);
        }
    }

    static final class ObjectMergePatch  extends JacksonMergePatch
    {
        private final Map<String, JsonNode> fields;
        private final Set<String> removals;

        ObjectMergePatch(final ObjectNode content)
        {
            super(content);
            fields = asMap(content);
            removals = new HashSet<String>();

            for (final Map.Entry<String, JsonNode> entry: fields.entrySet())
                if (entry.getValue() == null)
                    removals.add(entry.getKey());

            fields.keySet().removeAll(removals);
        }

        @Override
        public JsonNode apply(final JsonNode input)
        {
            if (!input.isContainerNode())
                return mapToNode(fields);


            final Map<String, JsonNode> map = asMap((ObjectNode) input);

            // Remove all entries which must be removed
            map.keySet().removeAll(removals);

            // Now cycle through what is left
            String memberName;
            JsonNode patchNode;

            for (final Map.Entry<String, JsonNode> entry: map.entrySet()) {
                memberName = entry.getKey();
                patchNode = fields.get(memberName);

                // Leave untouched if no mention in the patch
                if (patchNode == null)
                    continue;

                // If the patch node is a primitive type, replace in the result.
                // Reminder: there cannot be a JSON null anymore
                if (patchNode.isValueNode()) {
                    entry.setValue(patchNode); // no need for .deepCopy()
                    continue;
                }

                final JacksonMergePatch patch = fromJson(patchNode);
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

        private Map<String, JsonNode> asMap(ObjectNode input) {
            Map<String,JsonNode> result = new LinkedHashMap<String,JsonNode>();
            
            Iterator<Map.Entry<String, JsonNode>> fields1 = input.fields();
            while ( fields1.hasNext())
            {
                Map.Entry<String, JsonNode> entry = fields1.next();
                JsonNode value = entry.getValue();
                String key = entry.getKey();
                result.put( key,value);
            }
            return result;
        }


    }

    private static JsonNode mapToNode(final Map<String, JsonNode> map)
    {
        final ObjectNode ret = mapper.createObjectNode();
        for ( String key: map.keySet())
        {
            JsonNode value = map.get( key);
            ret.set(key, value);
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
    protected static JsonNode clearNulls(final JsonNode node)
    {
        if (node.isValueNode())
            return node;

        return node.isArray() ? clearNullsFromArray((ArrayNode)node)  : clearNullsFromObject((ObjectNode)node);
    }

    private static JsonNode clearNullsFromArray(final ArrayNode node)
    {
        final ArrayNode ret = mapper.createArrayNode();

	    /*
	* Cycle through array elements. If the element is a null node itself,
	* skip it. Otherwise, add a "cleaned up" element to the result.
	*/
        for (final JsonNode element: node)
            if (!element.isNull())
                ret.add(clearNulls(element));

        return ret;
    }

    private static ObjectNode clearNullsFromObject(final ObjectNode node)
    {
        final ObjectNode ret = mapper.createObjectNode();
        final Iterator<Map.Entry<String, JsonNode>> iterator
                = node.fields();

        Map.Entry<String, JsonNode> entry;
        JsonNode value;

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
                JsonNode clearNulls = clearNulls(value);
                ret.set(key, clearNulls);
            }
        }

        return ret;
    }

    public String toString()
    {
        return origPatch.toString();
    }

}