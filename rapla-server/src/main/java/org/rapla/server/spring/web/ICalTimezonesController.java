package org.rapla.server.spring.web;

import net.fortuna.ical4j.model.TimeZone;
import org.rapla.framework.TimeZoneConverter;
import org.rapla.plugin.export2ical.ICalTimezones;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RestController
public class ICalTimezonesController implements ICalTimezones
{
    private final TimeZoneConverter converter;
    private final List<String> availableIDs;

    public ICalTimezonesController(TimeZoneConverter converter)
    {
        this.converter = converter;
        this.availableIDs = new ArrayList<>(Arrays.asList(TimeZone.getAvailableIDs()));
        Collections.sort(this.availableIDs, String.CASE_INSENSITIVE_ORDER);
    }

    @Override
    public List<String> getICalTimezones()
    {
        return new ArrayList<>(availableIDs);
    }

    @Override
    public String getDefaultTimezone()
    {
        return converter.getImportExportTimeZone().getID();
    }
}
