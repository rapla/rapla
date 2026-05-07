package org.rapla.server.spring.web;

import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.endpoints.server.RaplaEventsRestPage;
import org.rapla.framework.RaplaException;
import org.rapla.server.RemoteSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@ConditionalOnBean(RemoteSession.class)
@RequestMapping("/events")
public class RaplaEventsController
{
    private final RaplaEventsRestPage page;

    public RaplaEventsController(RaplaEventsRestPage page)
    {
        this.page = page;
    }

    @GetMapping
    public List<ReservationImpl> list(@RequestParam(value = "start", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                       @RequestParam(value = "end", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                       @RequestParam(value = "resources", required = false) List<String> resources,
                                       @RequestParam(value = "owners", required = false) List<String> owners,
                                       @RequestParam(value = "eventTypes", required = false) List<String> eventTypes,
                                       @RequestParam(value = "attributeFilter", required = false) Map<String, String> attributeFilter) throws Exception
    {
        return page.list(
                start, end,
                resources != null ? resources : Collections.emptyList(),
                owners != null ? owners : Collections.emptyList(),
                eventTypes != null ? eventTypes : Collections.emptyList(),
                attributeFilter != null ? attributeFilter : Collections.emptyMap());
    }

    @GetMapping("/{id}")
    public ReservationImpl get(@PathVariable("id") String id) throws RaplaException
    {
        return page.get(id);
    }

    @PatchMapping("/{id}")
    public ReservationImpl patch(@PathVariable("id") String id, @RequestBody ReservationImpl event) throws Exception
    {
        return page.patch(id, event);
    }

    @PutMapping
    public ReservationImpl update(@RequestBody ReservationImpl event) throws RaplaException
    {
        return page.update(event);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable("id") String id) throws RaplaException
    {
        return page.delete(id);
    }

    @PostMapping
    public ReservationImpl create(@RequestBody ReservationImpl event) throws RaplaException
    {
        return page.create(event);
    }
}
