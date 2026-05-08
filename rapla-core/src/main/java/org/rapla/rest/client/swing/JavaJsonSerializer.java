package org.rapla.rest.client.swing;

import org.rapla.rest.JsonParserWrapper;
import org.rapla.rest.SerializableExceptionInformation;
import org.rapla.rest.client.RemoteConnectException;
import org.rapla.rest.client.internal.isodate.ISODateTimeFormat;

import java.util.Date;

public class JavaJsonSerializer
{
    JsonParserWrapper.JsonParser parser;
    private final Class resultType;
    private final Class container;

    public JavaJsonSerializer(final Class resultType, final Class container)
    {
        this.resultType = resultType;
        this.container = container;
    }

    private synchronized JsonParserWrapper.JsonParser getParser()
    {
        if ( parser == null) {
            parser = JsonParserWrapper.defaultJson().get();
        }
        return parser;
    }

    public String serializeArgument(Object arg)
    {
        final String result;
        if (arg != null)
        {
            JsonParserWrapper.JsonParser p = getParser();
            result = p.toJson(arg);
        }
        else
        {
            result = "";
        }
        return result;
    }

    public String serializeDate(Date date)
    {
        if ( date != null)
        {
            return ISODateTimeFormat.INSTANCE.formatTimestamp(date);
        }
        else
        {
            return "";
        }
    }

    /** {@code LocalDateTime} variant. UTC. */
    public String serializeDate(java.time.LocalDateTime dateTime)
    {
        if ( dateTime == null) return "";
        return serializeDate(new Date(dateTime.toEpochSecond(java.time.ZoneOffset.UTC) * 1000L));
    }

    public Object deserializeResult(String unparsedResult) throws RemoteConnectException
    {
        final JsonParserWrapper.JsonParser jsonMapper = getParser();
        return jsonMapper.fromJson( unparsedResult, resultType, container);
    }

    public SerializableExceptionInformation deserializeException(String unparsedErrorString)
    {
        final JsonParserWrapper.JsonParser jsonMapper = getParser();
        return jsonMapper.fromJson(unparsedErrorString, SerializableExceptionInformation.class);
    }
}
