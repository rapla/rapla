package org.rapla.client.gwt.components.util;

import java.util.Date;

import org.rapla.components.util.DateTools;
import org.rapla.components.util.ParseDateException;
import org.rapla.components.util.SerializableDateTimeFormat;

import com.google.gwt.i18n.client.DateTimeFormat;

public class GWTDateUtils
{
    public static Date gwtDateToRapla(Date date)
    {
        DateTimeFormat format = DateTimeFormat.getFormat("yyyy-MM-dd");
        final String string = format.format(date);
        try
        {
            final Date parsed = SerializableDateTimeFormat.INSTANCE.parseDate(string, false);
            return parsed;
        }
        catch (ParseDateException e)
        {
            throw new IllegalStateException(e);
        }
    }

    public static Date gwtDateTimeToRapla(Date date, Date time)
    {
        final Date parseTime = gwtTimeToRapla(time);
        final Date parseDate = gwtDateToRapla(date);
        final Date dateTime = DateTools.toDateTime(parseDate, parseTime);
        return dateTime;
    }

    public static Date gwtTimeToRapla(Date time)
    {
        DateTimeFormat format = DateTimeFormat.getFormat("HH:mm:ss");
        final String string = format.format(time);
        try
        {
            final Date parsed = SerializableDateTimeFormat.INSTANCE.parseTime(string);
            return parsed;
        }
        catch (ParseDateException e)
        {
            throw new IllegalStateException(e);
        }
    }

    public static Date raplaToGwtDate(Date date)
    {
        final String formatted = SerializableDateTimeFormat.INSTANCE.formatDate(date);
        DateTimeFormat format = DateTimeFormat.getFormat("yyyy-MM-dd");
        final Date gwtDate = format.parse(formatted);
        return gwtDate;
    }

    public static Date raplaToGwtTime(Date date)
    {
        final String formatTime = SerializableDateTimeFormat.INSTANCE.formatTime(date);
        final DateTimeFormat format = DateTimeFormat.getFormat("HH:mm:ss");
        final Date gwtTime = format.parse(formatTime);
        return gwtTime;
    }

    public static Date raplaToGwtDateTime(Date date)
    {
        final String formatDate = SerializableDateTimeFormat.INSTANCE.formatDate(date);
        final String formatTime = SerializableDateTimeFormat.INSTANCE.formatTime(date);
        final DateTimeFormat format = DateTimeFormat.getFormat("yyyy-MM-dd HH:mm:ss");
        final Date gwtDateTime = format.parse(formatDate + " " + formatTime);
        return gwtDateTime;
    }
}
