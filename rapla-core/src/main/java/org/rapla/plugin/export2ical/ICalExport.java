package org.rapla.plugin.export2ical;

import org.rapla.framework.RaplaException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Set;

@HttpExchange("/ical/export")
public interface ICalExport {
    @PostExchange
	String export(@RequestBody Set<String> appointmentIds) throws RaplaException;
}
