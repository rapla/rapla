package org.rapla.server.spring.web;

import org.rapla.framework.RaplaException;
import org.rapla.plugin.externaleventimport.CreateReservationsRequest;
import org.rapla.plugin.externaleventimport.ExternalEventImportMetadata;
import org.rapla.plugin.externaleventimport.ExternalEventImportPlugin;
import org.rapla.plugin.externaleventimport.ExternalEventImportResult;
import org.rapla.plugin.externaleventimport.ExternalEventImportService;
import org.rapla.plugin.externaleventimport.ImportCriteria;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Server-side wrapper that exposes the {@link ExternalEventImportService}
 *  implementation (registered as a {@code @Service} bean by whichever
 *  deployment ships an impl, e.g. dhbwrapla's {@code DualisEventsLoaderImpl})
 *  as REST endpoints under {@code /externaleventimport/*}.
 *
 *  <p>Method-level mappings duplicate the {@code @HttpExchange}/{@code @GetExchange}/
 *  {@code @PostExchange} annotations on the service interface. Spring 7's
 *  client-side {@code HttpServiceProxyFactory} understands {@code @HttpExchange};
 *  server-side {@code DispatcherServlet} requires explicit {@code @GetMapping}/{@code @PostMapping}.
 *
 *  <p>Gated by {@code rapla.externalevents.enabled} so the controller loads
 *  iff an impl is also active (impls in this codebase carry the same
 *  {@code @ConditionalOnProperty} gate). */
@RestController
@RequestMapping("/externaleventimport")
@ConditionalOnProperty(ExternalEventImportPlugin.ENABLE_PROPERTY)
public class ExternalEventImportController
{
    private final ExternalEventImportService service;

    public ExternalEventImportController(ExternalEventImportService service)
    {
        this.service = service;
    }

    @GetMapping("/metadata")
    public ExternalEventImportMetadata getMetadata() throws RaplaException
    {
        return service.getMetadata();
    }

    @PostMapping("/loadEvents")
    public ExternalEventImportResult loadEvents(@RequestBody ImportCriteria criteria) throws RaplaException
    {
        return service.loadEvents(criteria);
    }

    @PostMapping("/uploadCsv")
    public ExternalEventImportResult uploadCsv(@RequestPart("file") MultipartFile file) throws RaplaException
    {
        return service.uploadCsv(file);
    }

    @PostMapping("/createReservations")
    public List<String> createReservations(@RequestBody CreateReservationsRequest request) throws RaplaException
    {
        return service.createReservations(request);
    }
}
