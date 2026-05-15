package org.rapla.server.spring.web;

import org.rapla.plugin.export2ical.ICalTimezones;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/ical/timezones", produces = "application/json")
public class ICalTimezonesController
{
    private final ICalTimezones service;

    public ICalTimezonesController(ICalTimezones service)
    {
        this.service = service;
    }

    @GetMapping
    public List<String> getICalTimezones()
    {
        return service.getICalTimezones();
    }

    @GetMapping("/default")
    public String getDefaultTimezone()
    {
        return service.getDefaultTimezone();
    }
}
