package org.rapla.rest;

import java.net.URL;

import org.rapla.ServletTestBase;
import org.rapla.entities.User;
import org.rapla.framework.RaplaContext;
import org.rapla.rest.gwtjsonrpc.common.FutureResult;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.dbrm.HTTPConnector;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class ResourcesTest extends ServletTestBase {

    public ResourcesTest(String name) {
        super(name);
    }

    public void testJson() throws Exception
    {
        RaplaContext context = getContainer().getContext();
        RaplaKeyStorage store = context.lookup(RaplaKeyStorage.class);
        StorageOperator operator = context.lookup( StorageOperator.class);
        User user = operator.getUser("homer");
        HTTPConnector connector = new HTTPConnector();
        String requestMethod = "GET";
        URL methodURL =new URL("http://localhost:8052/rapla/resources");
        JsonObject callObj = null;
        String authenticationToken = null;
        FutureResult<String> authExpiredCommand= null;
        JsonObject result = connector.sendCall(requestMethod, methodURL, callObj, authenticationToken, authExpiredCommand);
        JsonArray resourceArray = result.get("result").getAsJsonArray();
        assertTrue( resourceArray.size() > 1);
//        URL methodURL =new URL("http://localhost:8052/rapla/resources");
//        for (JsonElement element:resourceArray)
//        {
//            System.out.println(element);
//        }
    }
}
