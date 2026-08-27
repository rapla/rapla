package org.rapla.server.spring.graphql;

import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.externaleventimport.server.ExternalEventWorklistAssembler;
import org.rapla.plugin.externaleventimport.server.WorklistView;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import graphql.schema.DataFetchingEnvironment;

/**
 * The Abgleich worklist over the external-event staging store.
 *
 * <p>Thin adapter: the §12 filtering lives in {@link ExternalEventWorklistAssembler} so it is
 * testable without a Spring context. The assembler is injected through an
 * {@link ObjectProvider} because staging is opt-in — a deployment without a snapshot provider
 * has no assembler bean, and the query then answers empty rather than failing. Authentication is
 * checked BEFORE that, so an anonymous caller is rejected identically whether or not the
 * deployment runs staging. A bean condition
 * would not work here: this controller is user config and is processed before the
 * autoconfiguration that defines the assembler.
 */
@Controller
public class ExternalEventWorklistGraphQLController
{
    private final ObjectProvider<ExternalEventWorklistAssembler> assemblers;
    private final ObjectProvider<org.rapla.plugin.externaleventimport.server.ExternalEventBinder> binders;
    private final ObjectProvider<org.rapla.plugin.externaleventimport.server.ExternalEventStagingMutator> mutators;
    private final StorageOperator operator;

    public ExternalEventWorklistGraphQLController(ObjectProvider<ExternalEventWorklistAssembler> assemblers,
            ObjectProvider<org.rapla.plugin.externaleventimport.server.ExternalEventBinder> binders,
            ObjectProvider<org.rapla.plugin.externaleventimport.server.ExternalEventStagingMutator> mutators,
            StorageOperator operator)
    {
        this.assemblers = assemblers;
        this.binders = binders;
        this.mutators = mutators;
        this.operator = operator;
    }

    @QueryMapping
    public java.util.List<org.rapla.plugin.externaleventimport.server.BindCandidate> bindCandidates(
            @Argument("sourceItemId") String sourceItemId, @Argument("from") String from,
            @Argument("to") String to, @Argument("limit") Integer limit, DataFetchingEnvironment env)
            throws RaplaException
    {
        final var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        final User caller = UnauthenticatedException.require(rc.caller());
        final var binder = binders.getIfAvailable();
        if (binder == null)
        {
            return java.util.List.of();
        }
        final PermissionController permissionController = rc.permissionController() != null ? rc.permissionController()
                : operator.getPermissionController();
        return binder.candidates(caller, permissionController, sourceItemId,
                java.time.LocalDateTime.parse(from), java.time.LocalDateTime.parse(to), limit);
    }

    @org.springframework.graphql.data.method.annotation.MutationMapping
    public int dismissStagedChanges(@Argument("sourceItemIds") java.util.List<String> sourceItemIds,
            DataFetchingEnvironment env) throws RaplaException
    {
        final var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        final User caller = UnauthenticatedException.require(rc.caller());
        final var mutator = mutators.getIfAvailable();
        if (mutator == null)
        {
            return 0;
        }
        final PermissionController permissionController = rc.permissionController() != null ? rc.permissionController()
                : operator.getPermissionController();
        return mutator.dismissChanges(caller, permissionController, sourceItemIds);
    }

    @org.springframework.graphql.data.method.annotation.MutationMapping
    public boolean bindStagedEvent(@Argument("sourceItemId") String sourceItemId,
            @Argument("reservationId") String reservationId, DataFetchingEnvironment env) throws RaplaException
    {
        final var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        final User caller = UnauthenticatedException.require(rc.caller());
        final var binder = binders.getIfAvailable();
        if (binder == null)
        {
            return false;
        }
        final PermissionController permissionController = rc.permissionController() != null ? rc.permissionController()
                : operator.getPermissionController();
        return binder.bind(caller, permissionController, sourceItemId, reservationId);
    }

    @QueryMapping
    public WorklistView externalEventWorklist(@Argument("allocatableIds") java.util.List<String> allocatableIds,
            @Argument("scopeKey") String scopeKey, DataFetchingEnvironment env) throws RaplaException
    {
        final var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        final User caller = UnauthenticatedException.require(rc.caller());
        final ExternalEventWorklistAssembler assembler = assemblers.getIfAvailable();
        if (assembler == null)
        {
            return WorklistView.empty();
        }
        final PermissionController permissionController = rc.permissionController() != null ? rc.permissionController()
                : operator.getPermissionController();
        return assembler.assemble(caller, permissionController, allocatableIds, scopeKey);
    }
}
