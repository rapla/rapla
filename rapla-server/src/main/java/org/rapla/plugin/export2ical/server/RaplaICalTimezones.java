package org.rapla.plugin.export2ical.server;

import net.fortuna.ical4j.model.TimeZone;
import org.rapla.plugin.export2ical.ICalTimezones;
import org.rapla.framework.TimeZoneConverter;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RaplaICalTimezones implements ICalTimezones
{

    List<String> availableIDs;
    @Autowired
    TimeZoneConverter converter;

    @Autowired
    public RaplaICalTimezones()
    {
        availableIDs = new ArrayList<>(Arrays.asList(TimeZone.getAvailableIDs()));
        Collections.sort(availableIDs, String.CASE_INSENSITIVE_ORDER);
    }

    public List<String> getICalTimezones()
    {
        List<String> result = new ArrayList<>();
        for (String id : availableIDs)
        {
            result.add(id);
        }
        return result;
    }

    //public static final String TIMEZONE = "timezone";

    public String getDefaultTimezone()
    {
        String id = converter.getImportExportTimeZone().getID();
        return id;
    }

}
