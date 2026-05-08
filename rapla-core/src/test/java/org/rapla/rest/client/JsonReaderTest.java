package org.rapla.rest.client;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
        ObjectMapper mapper = tools.jackson.databind.json.JsonMapper.builder()
                .enable(tools.jackson.core.json.JsonReadFeature.ALLOW_SINGLE_QUOTES)
                .build();
        final JsonNode parse = mapper.readTree(string);
        final JsonNode element = parse.get("attribute");
        final String asString = element.get(2).asText();
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
