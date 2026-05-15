package org.rapla.plugin.export2ical;

import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

@HttpExchange("/api/ical/timezones")
public interface ICalTimezones
{
    @GetExchange
    List<String> getICalTimezones();

    @GetExchange("/default")
    String getDefaultTimezone();
}