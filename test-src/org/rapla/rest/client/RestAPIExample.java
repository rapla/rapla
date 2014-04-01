package org.rapla.rest.client;

import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.rapla.rest.client.HTTPJsonConnector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class RestAPIExample {

    protected void assertTrue( boolean condition)
    {
        if (!condition)
        {
            throw new IllegalStateException("Assertion failed");
        }
    }
    
    protected void assertEquals( Object o1, Object o2)
    {
        if ( !o1.equals( o2))
        {
            throw new IllegalStateException("Assertion failed. Expected " + o1 + " but was " + o2);
        }
    }
    
    public void testRestApi(URL baseUrl, String username,String password) throws Exception
    {
        HTTPJsonConnector connector = new HTTPJsonConnector();
        
        // first we login using the auth method
        String authenticationToken = null;
        {
            URL methodURL =new URL(baseUrl,"auth");
            JsonObject callObj = new JsonObject();
            callObj.addProperty("username", username);
            callObj.addProperty("password", password);
            String emptyAuthenticationToken = null;
            JsonObject resultBody = connector.sendPost(methodURL, callObj, emptyAuthenticationToken);
            assertNoError(resultBody);
            JsonObject resultObject = resultBody.get("result").getAsJsonObject();
            authenticationToken = resultObject.get("accessToken").getAsString();
            String validity = resultObject.get("validUntil").getAsString();
            System.out.println("token valid until " + validity);
        }
        //  we get all the different resource,person and event types 
        String resourceType = null;
        @SuppressWarnings("unused")
        String personType =null;
        String eventType =null;
        {
            URL methodURL =new URL(baseUrl,"dynamictypes?classificationType=resource");
            JsonObject resultBody = connector.sendGet( methodURL,  authenticationToken);
            assertNoError(resultBody);
            JsonArray resultList = resultBody.get("result").getAsJsonArray();
            assertTrue( resultList.size() > 0);
            for (JsonElement obj:resultList)
            {
                JsonObject casted = (JsonObject) obj;
                resourceType =casted.get("key").getAsString();
            }
        }
        {
            URL methodURL =new URL(baseUrl,"dynamictypes?classificationType=person");
            JsonObject resultBody = connector.sendGet( methodURL,  authenticationToken);
            assertNoError(resultBody);
            JsonArray resultList = resultBody.get("result").getAsJsonArray();
            assertTrue( resultList.size() > 0);
            for (JsonElement obj:resultList)
            {
                JsonObject casted = (JsonObject) obj;
                personType =casted.get("key").getAsString();
            }
        }
        {
            URL methodURL =new URL(baseUrl,"dynamictypes?classificationType=reservation");
            JsonObject resultBody = connector.sendGet( methodURL,  authenticationToken);
            assertNoError(resultBody);
            JsonArray resultList = resultBody.get("result").getAsJsonArray();
            assertTrue( resultList.size() > 0);
            for (JsonElement obj:resultList)
            {
                JsonObject casted = (JsonObject) obj;
                eventType =casted.get("key").getAsString();
            }
        }
        //  we create a new resource
        String resourceId = null;
        String resourceName;
        {
            resourceName = "Test Room";
            String objectName = resourceName;
            String dynamicType = resourceType;
            JsonObject eventObject = new JsonObject();
            Map<String,String> keyValue = new LinkedHashMap<String,String>();
            keyValue.put( "name", objectName);
            JsonObject classificationObj = new JsonObject();
            classificationObj.add("type", new JsonPrimitive(dynamicType));
            patchClassification(keyValue, classificationObj);
            eventObject.add("classification", classificationObj);
            {
                URL methodURL =new URL(baseUrl,"resources");
                JsonObject resultBody = connector.sendPost( methodURL, eventObject, authenticationToken);
                // we test if the new resource has the name and extract the id for later testing
                printAttributesAndAssertName(resultBody,  objectName);
                resourceId = resultBody.get("result").getAsJsonObject().get("id").getAsString();
            }
            // now we test again if the new resource is created  by using the get method
            {
                URL methodURL =new URL(baseUrl,"resources/"+resourceId);
                JsonObject resultBody = connector.sendGet( methodURL, authenticationToken);
                printAttributesAndAssertName(resultBody,  objectName);
            }
        }
        // we use a get list on the resources
        {
            String attributeFilter = URLEncoder.encode("{'name' :'"+ resourceName +"'}","UTF-8");
            String resourceTypes =URLEncoder.encode("['"+ resourceType +"']","UTF-8");
            URL methodURL =new URL(baseUrl,"resources?resourceTypes="+ resourceTypes+  "&attributeFilter="+attributeFilter) ;
            JsonObject resultBody = connector.sendGet( methodURL,  authenticationToken);
            assertNoError(resultBody);
            JsonArray resultList = resultBody.get("result").getAsJsonArray();
            assertTrue( resultList.size() > 0);
            for (JsonElement obj:resultList)
            {
                JsonObject casted = (JsonObject) obj;
                String id = casted.get("id").getAsString();
                JsonObject classification = casted.get("classification").getAsJsonObject().get("data").getAsJsonObject();
                String name = classification.get("name").getAsJsonArray().get(0).getAsString();
                System.out.println("[" +id + "]" + name);
            }
        }
        
        // we create a new event for the resource
        String eventId = null;
        String eventName = null;
        {
            eventName ="event name";
            String objectName = eventName;
            String dynamicType =eventType;
            JsonObject eventObject = new JsonObject();
            Map<String,String> keyValue = new LinkedHashMap<String,String>();
            keyValue.put( "name", objectName);
            JsonObject classificationObj = new JsonObject();
            classificationObj.add("type", new JsonPrimitive(dynamicType));
            patchClassification(keyValue, classificationObj);
            eventObject.add("classification", classificationObj);
            // add appointments
            {
                // IS0 8061 format is required. Always add the dates in UTC Timezone 
                // Rapla doesn't support multiple timezone. All internal dates are stored in UTC
                // So store 10:00 local time as 10:00Z
                JsonObject appointmentObj = createAppointment("2015-01-01T10:00Z","2015-01-01T12:00Z");
                JsonArray appoinmentArray = new JsonArray();
                appoinmentArray.add( appointmentObj);
                eventObject.add("appointments", appoinmentArray);
            }
            // add resources
            {
                JsonArray resourceArray = new JsonArray();
                resourceArray.add( new JsonPrimitive(resourceId));
                JsonObject linkMap = new JsonObject();
                linkMap.add("resources", resourceArray);
                eventObject.add("links", linkMap);
            }
            {
                URL methodURL =new URL(baseUrl,"events");
                JsonObject resultBody = connector.sendPost( methodURL, eventObject, authenticationToken);
                // we test if the new event has the name and extract the id for later testing
                printAttributesAndAssertName(resultBody,  objectName);
                eventId = resultBody.get("result").getAsJsonObject().get("id").getAsString();
            }
            // now we test again if the new event is created  by using the get method
            {
                URL methodURL =new URL(baseUrl,"events/"+eventId);
                JsonObject resultBody = connector.sendGet( methodURL, authenticationToken);
                printAttributesAndAssertName(resultBody,  objectName);
            }
        }
        // now we query a list of events
        {
            // we can use startDate without time 
            String start= URLEncoder.encode("2000-01-01","UTF-8");
            // or with time information.
            String end= URLEncoder.encode("2020-01-01T10:00Z","UTF-8");
            String resources = URLEncoder.encode("['"+ resourceId +"']","UTF-8");
            String eventTypes = URLEncoder.encode("['"+ eventType +"']","UTF-8");
            String attributeFilter = URLEncoder.encode("{'name' :'"+ eventName +"'}","UTF-8");
            URL methodURL =new URL(baseUrl,"events?start="+start + "&end="+end + "&resources="+resources +"&eventTypes=" + eventTypes +"&attributeFilter="+attributeFilter) ;
            JsonObject resultBody = connector.sendGet( methodURL,  authenticationToken);
            assertNoError(resultBody);
            JsonArray resultList = resultBody.get("result").getAsJsonArray();
            assertTrue( resultList.size() > 0);
            for (JsonElement obj:resultList)
            {
                JsonObject casted = (JsonObject) obj;
                String id = casted.get("id").getAsString();
                JsonObject classification = casted.get("classification").getAsJsonObject().get("data").getAsJsonObject();
                String name = classification.get("name").getAsJsonArray().get(0).getAsString();
                System.out.println("[" +id + "]" + name);
            }
        }
       
        // we test a patch
        {
            String newReservationName ="changed event name";
            Map<String,String> keyValue = new LinkedHashMap<String,String>();
            keyValue.put( "name", newReservationName);
            JsonObject patchObject = new JsonObject();
            JsonObject classificationObj = new JsonObject();
            patchClassification(keyValue, classificationObj);
            patchObject.add("classification", classificationObj);
           
            // you can also use the string syntax and parse to get the patch object
            //String patchString ="{'classification': { 'data':   {'"+ key + "' : ['"+value+"'] } } }";
            //JsonObject callObj = new JsonParser().parse(patchString).getAsJsonObject();
            URL methodURL =new URL(baseUrl, "events/"+eventId);
            {
                JsonObject resultBody = connector.sendPatch( methodURL, patchObject, authenticationToken);
                // we test if the new event is in the patched result
                printAttributesAndAssertName(resultBody,  newReservationName);
            }
            // now we test again if the new event has the new name by using the get method
            {
                JsonObject resultBody = connector.sendGet( methodURL, authenticationToken);
                printAttributesAndAssertName(resultBody,  newReservationName);
            }
        }
       
    }

    private JsonObject createAppointment(String start, String end) 
    {
        JsonObject app = new JsonObject();
        app.add("start", new JsonPrimitive(start));
        app.add("end", new JsonPrimitive(end));
        return app;
    }

   
    public void patchClassification(Map<String, String> keyValue,  JsonObject classificationObj) {
        JsonObject data = new JsonObject();
        classificationObj.add("data", data);
        for (Map.Entry<String, String> entry:keyValue.entrySet())
        {
            JsonArray jsonArray = new JsonArray();
            jsonArray.add( new JsonPrimitive(entry.getValue()));
            data.add(entry.getKey(), jsonArray);
        }
    }

    private void printAttributesAndAssertName(JsonObject resultBody, String objectName) {
        assertNoError(resultBody);
        JsonObject event = resultBody.get("result").getAsJsonObject();
        JsonObject classification = event.get("classification").getAsJsonObject().get("data").getAsJsonObject();
        System.out.println("Attributes for object id");
        for (Entry<String, JsonElement> entry:classification.entrySet())
        {
            String key =entry.getKey();
            JsonArray value= entry.getValue().getAsJsonArray();
            System.out.println("  "  + key + "=" + value.toString());
            if ( key.equals("name"))
            {
               assertEquals(objectName, value.get(0).getAsString()); 
            }
        }
    }

    public void assertNoError(JsonObject resultBody) {
        JsonElement error = resultBody.get("error");
        if (error!= null)
        {
            System.err.println(error);
            assertTrue( error == null );
        }
    }
    
    public static void main(String[] args) {
        try {
            // The base url points to the rapla servlet not the webcontext.
            // If your rapla context is not running under root webappcontext you need to add the context path.
            // Example if you deploy the rapla.war in tomcat the default would be
            // http://host:8051/rapla/rapla/
            URL baseUrl = new URL("http://localhost:8051/rapla/");
            String username = "admin";
            String password = "";
            new RestAPIExample().testRestApi(baseUrl, username, password);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }
    
   
}
