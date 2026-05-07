package org.rapla.rest.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.components.util.IOUtil;

import java.io.InputStream;

@RunWith(JUnit4.class)
public class JsonReaderTest
{
    @Test
    public void testJson() throws Exception
    {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("json.txt");
        final String string = IOUtil.readString(stream,"UTF-8");
        JsonParser jsonParser = new JsonParser();
        final JsonElement parse = jsonParser.parse(string);
        final JsonElement element = parse.getAsJsonObject().get("attribute");
        final String asString = element.getAsJsonArray().get(2).getAsString();
        Assert.assertEquals("öffnen",asString);
    }

    public static void main(String[] args)
    {
        try
        {
            new JsonReaderTest().testJson();
        }
        catch ( Exception ex)
        {
            ex.printStackTrace();
        }
    }
}
