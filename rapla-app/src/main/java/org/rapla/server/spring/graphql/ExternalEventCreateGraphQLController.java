package org.rapla.server.spring.graphql;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.rapla.entities.User;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.storage.ExternalSyncEntity;
import org.rapla.entities.storage.ImportExportDirections;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.externaleventimport.ExternalEventCreateService;
import org.rapla.plugin.externaleventimport.ExternalEventImportService;
import org.rapla.plugin.externaleventimport.ExternalEventTemplateResolver;
import org.rapla.storage.CachableStorageOperator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import graphql.schema.DataFetchingEnvironment;

/**
 * PRD 104 v3 "Sync" — creates stored reservations from staged external events. Thin
 * adapter over the deployment's {@link ExternalEventCreateService}; injected via
 * {@link ObjectProvider} for the same reason as the worklist controller (staging is
 * opt-in and the deployment bean is defined by autoconfiguration). Unlike the worklist
 * (which degrades to empty), a CREATE against a deployment without the service is an
 * error — silently creating nothing would look like success.
 */
@Controller
public class ExternalEventCreateGraphQLController
{
    private final ObjectProvider<ExternalEventCreateService> services;
    private final ObjectProvider<ExternalEventTemplateResolver> templateResolvers;
    private final ObjectProvider<ExternalEventImportService> importServices;
    private final CachableStorageOperator operator;

    public ExternalEventCreateGraphQLController(ObjectProvider<ExternalEventCreateService> services,
            ObjectProvider<ExternalEventTemplateResolver> templateResolvers,
            ObjectProvider<ExternalEventImportService> importServices, CachableStorageOperator operator)
    {
        this.services = services;
        this.templateResolvers = templateResolvers;
        this.importServices = importServices;
        this.operator = operator;
    }

    /**
     * Of the given reservation ids, the subset durably bound to THE deployment's import
     * source. Discriminator = {@link ExternalSyncEntity} rows of the deployment's system id
     * ({@link ExternalEventImportService#getExternalSystemId()}) — the bare {@code externalid}
     * annotation is NOT enough, other importers (iCal) write it too without sync entities
     * (holidays-shown-as-verknüpft bug, 2026-08-12). §12: unknown and non-readable ids drop
     * silently and indistinguishably.
     */
    @QueryMapping
    public List<String> externalEventLinkedReservationIds(
            @Argument("reservationIds") List<String> reservationIds, DataFetchingEnvironment env)
            throws RaplaException
    {
        final var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        final User caller = UnauthenticatedException.require(rc.caller());
        final ExternalEventImportService importService = importServices.getIfAvailable();
        final String systemId = importService == null ? null : importService.getExternalSystemId();
        if (systemId == null || reservationIds == null || reservationIds.isEmpty())
        {
            return List.of();
        }
        final Set<String> bound = operator.getImportExportEntities(systemId, ImportExportDirections.IMPORT)
                .values().stream().map(ExternalSyncEntity::getRaplaId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        final var pc = operator.getPermissionController();
        final List<String> out = new ArrayList<>();
        for (String id : reservationIds)
        {
            if (id == null || !bound.contains(id)) continue;
            Reservation r;
            try
            {
                r = operator.tryResolve(new ReferenceInfo<>(id, Reservation.class));
            }
            catch (RuntimeException e)
            {
                continue;
            }
            if (r == null || !pc.canRead(r, caller)) continue;
            out.add(id);
        }
        return out;
    }

    @QueryMapping
    public ExternalEventTemplateResolver.DefaultTemplates externalEventDefaultTemplates(
            @Argument("groupIds") List<String> groupIds, DataFetchingEnvironment env) throws RaplaException
    {
        final var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        final User caller = UnauthenticatedException.require(rc.caller());
        final ExternalEventTemplateResolver resolver = templateResolvers.getIfAvailable();
        return resolver == null ? null : resolver.resolveDefaultTemplates(caller, groupIds);
    }

    @MutationMapping
    public List<String> createFromStagedEvents(@Argument("sourceItemIds") List<String> sourceItemIds,
            @Argument("lectureTemplateId") String lectureTemplateId,
            @Argument("examTemplateId") String examTemplateId,
            @Argument("defaultStart") String defaultStart, DataFetchingEnvironment env) throws RaplaException
    {
        final var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        final User caller = UnauthenticatedException.require(rc.caller());
        final ExternalEventCreateService service = services.getIfAvailable();
        if (service == null)
        {
            throw new RaplaException("No external event create service deployed");
        }
        return service.createFromStagedItems(caller, sourceItemIds, lectureTemplateId, examTemplateId,
                LocalDateTime.parse(defaultStart));
    }
}
