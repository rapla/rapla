package org.rapla.rest.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.rapla.endpoints.client.HTTPJsonConnector;

import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;

public class RestAPIExample {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

        String authenticationToken = null;
        {
            URL methodURL =new URL(baseUrl,"login");
            ObjectNode callObj = MAPPER.createObjectNode();
            callObj.put("username", username);
            callObj.put("password", password);
            String emptyAuthenticationToken = null;
            ObjectNode resultBody = connector.sendPost(methodURL, callObj, emptyAuthenticationToken);
            assertNoError(resultBody);
            JsonNode resultObject = resultBody.get("result");
            authenticationToken = resultObject.get("accessToken").asText();
            String validity = resultObject.get("validUntil").asText();
            System.out.println("token valid until " + validity);
        }
        String resourceType = null;
        @SuppressWarnings("unused")
        String personType =null;
        String eventType =null;
        {
            URL methodURL =new URL(baseUrl,"dynamictypes?classificationType=resource");
            ObjectNode resultBody = connector.sendGet( methodURL,  authenticationToken);
            assertNoError(resultBody);
            JsonNode resultList = resultBody.get("result");
            assertTrue( resultList.size() > 0);
            for (JsonNode obj : resultList)
            {
                resourceType = obj.get("key").asText();
                break;
            }
        }
        {
            URL methodURL =new URL(baseUrl,"dynamictypes?classificationType=person");
            ObjectNode resultBody = connector.sendGet( methodURL,  authenticationToken);
            assertNoError(resultBody);
            JsonNode resultList = resultBody.get("result");
            assertTrue( resultList.size() > 0);
            for (JsonNode obj : resultList)
            {
                personType = obj.get("key").asText();
            }
        }
        {
            URL methodURL =new URL(baseUrl,"dynamictypes?classificationType=reservation");
            ObjectNode resultBody = connector.sendGet( methodURL,  authenticationToken);
            assertNoError(resultBody);
            JsonNode resultList = resultBody.get("result");
            assertTrue( resultList.size() > 0);
            for (JsonNode obj : resultList)
            {
                eventType = obj.get("key").asText();
            }
        }
        String resourceId = null;
        String resourceName;
        {
            resourceName = "Test Room";
            String objectName = resourceName;
            String dynamicType = resourceType;
            ObjectNode eventObject = MAPPER.createObjectNode();
            Map<String,String> keyValue = new LinkedHashMap<>();
            keyValue.put( "name", objectName);
            ObjectNode classificationObj = MAPPER.createObjectNode();
            classificationObj.put("type", dynamicType);
            patchClassification(keyValue, classificationObj);
            eventObject.set("classification", classificationObj);
            {
                URL methodURL =new URL(baseUrl,"resources");
                ObjectNode resultBody = connector.sendPost( methodURL, eventObject, authenticationToken);
                printAttributesAndAssertName(resultBody,  objectName);
                resourceId = resultBody.get("result").get("id").asText();
            }
            {
                URL methodURL =new URL(baseUrl,"resources/"+resourceId);
                ObjectNode resultBody = connector.sendGet( methodURL, authenticationToken);
                printAttributesAndAssertName(resultBody,  objectName);
            }
        }
        {
            String attributeFilter = URLEncoder.encode("{'name' :'"+ resourceName +"'}","UTF-8");
            String resourceTypes =URLEncoder.encode("['"+ resourceType +"']","UTF-8");
            URL methodURL =new URL(baseUrl,"resources?resourceTypes="+ resourceTypes+  "&attributeFilter="+attributeFilter) ;
            ObjectNode resultBody = connector.sendGet( methodURL,  authenticationToken);
            assertNoError(resultBody);
            JsonNode resultList = resultBody.get("result");
            System.out.println( resultList );
            assertTrue( resultList.size() > 0);
            for (JsonNode obj : resultList)
            {
                String id = obj.get("id").asText();
                JsonNode classification = obj.get("classification").get("data");
                String name = classification.get("name").get(0).asText();
                System.out.println("[" +id + "]" + name);
            }
        }

        String eventId = null;
        String eventName = null;
        {
            eventName ="event name";
            String objectName = eventName;
            String dynamicType =eventType;
            ObjectNode eventObject = MAPPER.createObjectNode();
            Map<String,String> keyValue = new LinkedHashMap<>();
            keyValue.put( "name", objectName);
            ObjectNode classificationObj = MAPPER.createObjectNode();
            classificationObj.put("type", dynamicType);
            patchClassification(keyValue, classificationObj);
            eventObject.set("classification", classificationObj);
            {
                ObjectNode appointmentObj = createAppointment("2015-01-01T10:00Z","2015-01-01T12:00Z");
                ArrayNode appoinmentArray = MAPPER.createArrayNode();
                appoinmentArray.add( appointmentObj);
                eventObject.set("appointments", appoinmentArray);
            }
            {
                ArrayNode resourceArray = MAPPER.createArrayNode();
                resourceArray.add( resourceId);
                ObjectNode linkMap = MAPPER.createObjectNode();
                linkMap.set("resources", resourceArray);
                eventObject.set("links", linkMap);
            }
            {
                URL methodURL =new URL(baseUrl,"events");
                System.out.println( eventObject );
                ObjectNode resultBody = connector.sendPost( methodURL, eventObject, authenticationToken);
                System.out.println( resultBody );
                printAttributesAndAssertName(resultBody,  objectName);
                eventId = resultBody.get("result").get("id").asText();
            }
            {
                URL methodURL =new URL(baseUrl,"events/"+eventId);
                ObjectNode resultBody = connector.sendGet( methodURL, authenticationToken);
                System.out.println( resultBody );
                printAttributesAndAssertName(resultBody,  objectName);
            }
        }
        {
            String start= URLEncoder.encode("2000-01-01","UTF-8");
            String end= URLEncoder.encode("2020-01-01T10:00Z","UTF-8");
            String resources = URLEncoder.encode("['"+ resourceId +"']","UTF-8");
            String eventTypes = URLEncoder.encode("['"+ eventType +"']","UTF-8");
            String attributeFilter = URLEncoder.encode("{'name' :'"+ eventName +"'}","UTF-8");
            URL methodURL =new URL(baseUrl,"events?start="+start + "&end="+end + "&resources="+resources +"&eventTypes=" + eventTypes +"&attributeFilter="+attributeFilter) ;
            ObjectNode resultBody = connector.sendGet( methodURL,  authenticationToken);

            assertNoError(resultBody);
            JsonNode resultList = resultBody.get("result");
            System.out.println( resultList );
            assertTrue( resultList.size() > 0);
            for (JsonNode obj : resultList)
            {
                String id = obj.get("id").asText();
                JsonNode classification = obj.get("classification").get("data");
                String name = classification.get("name").get(0).asText();
                System.out.println("[" +id + "]" + name);
            }
        }

        {
            String newReservationName ="changed event name";
            Map<String,String> keyValue = new LinkedHashMap<>();
            keyValue.put( "name", newReservationName);
            ObjectNode patchObject = MAPPER.createObjectNode();
            ObjectNode classificationObj = MAPPER.createObjectNode();
            patchClassification(keyValue, classificationObj);
            patchObject.set("classification", classificationObj);

            URL methodURL =new URL(baseUrl, "events/"+eventId);
            {
                ObjectNode resultBody = connector.sendPatch( methodURL, patchObject, authenticationToken);
                System.out.println( resultBody );
                printAttributesAndAssertName(resultBody,  newReservationName);
            }
            {
                ObjectNode resultBody = connector.sendGet( methodURL, authenticationToken);
                printAttributesAndAssertName(resultBody,  newReservationName);
            }
        }

    }

    private ObjectNode createAppointment(String start, String end)
    {
        ObjectNode app = MAPPER.createObjectNode();
        app.put("start", start);
        app.put("end", end);
        return app;
    }


    public void patchClassification(Map<String, String> keyValue,  ObjectNode classificationObj) {
        ObjectNode data = MAPPER.createObjectNode();
        classificationObj.set("data", data);
        for (Map.Entry<String, String> entry:keyValue.entrySet())
        {
            ArrayNode jsonArray = MAPPER.createArrayNode();
            jsonArray.add( entry.getValue());
            data.set(entry.getKey(), jsonArray);
        }
    }

    private void printAttributesAndAssertName(ObjectNode resultBody, String objectName) {
        assertNoError(resultBody);
        JsonNode event = resultBody.get("result");
        JsonNode classification = event.get("classification").get("data");
        System.out.println("Attributes for object id");
        java.util.Iterator<Map.Entry<String, JsonNode>> it = classification.fields();
        while (it.hasNext())
        {
            Map.Entry<String, JsonNode> entry = it.next();
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            System.out.println("  "  + key + "=" + value.toString());
            if ( key.equals("name"))
            {
                assertEquals(objectName, value.get(0).asText());
            }
        }
    }

    public void assertNoError(ObjectNode resultBody) {
        JsonNode error = resultBody.get("error");
        if (error!= null)
        {
            System.err.println(error);
            assertTrue( error == null );
        }
    }

    public static void main(String[] args) {
        try {
            URL baseUrl = new URL("http://localhost:8051/rapla/");
            String username = "admin";
            String password = "";
            new RestAPIExample().testRestApi(baseUrl, username, password);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
