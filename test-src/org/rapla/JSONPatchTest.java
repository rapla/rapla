package org.rapla;

import org.rapla.rest.jsonpatch.mergepatch.server.JsonMergePatch;
import org.rapla.rest.jsonpatch.mergepatch.server.JsonPatchException;

import junit.framework.TestCase;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class JSONPatchTest extends TestCase {
	public void test() throws JsonPatchException
	{
		JsonParser parser = new JsonParser();
		String jsonOrig = new String("{'classification': {'type' :  'MyResourceTypeKey',   'data':   {'name' : ['New ResourceName'] } } }");
		String newName = "Room A1234";
		String jsonPatch = new String("{'classification': { 'data':   {'name' : ['"+newName+"'] } } }");
		JsonElement patchElement = parser.parse(jsonPatch);
		JsonElement orig = parser.parse(jsonOrig);
		final JsonMergePatch patch = JsonMergePatch.fromJson(patchElement);
		final JsonElement patched = patch.apply(orig);
		System.out.println("Original " +orig.toString());
		System.out.println("Patch    " +patchElement.toString());
		System.out.println("Patched  " +patched.toString());
		String jsonExpected = new String("{'classification': {'type' :  'MyResourceTypeKey',   'data':   {'name' : ['"+ newName +"'] } } }");
		JsonElement expected = parser.parse(jsonExpected);
		assertEquals( expected.toString(), patched.toString());
	}

}
