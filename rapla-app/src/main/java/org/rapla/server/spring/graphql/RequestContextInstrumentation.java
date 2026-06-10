package org.rapla.server.spring.graphql;

import graphql.GraphQLContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import java.util.Locale;
import org.rapla.entities.User;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * PRD 035 Cut C — per-query request-scoped state cache. Resolves
 * caller/permission-controller/locale ONCE per GraphQL query at
 * {@link #beginExecution} and stores them in the per-query
 * {@link GraphQLContext}. Hot-path DataFetchers read these from the context
 * via {@link #from(GraphQLContext)} instead of re-resolving on every field
 * dispatch.
 *
 * <p><b>Why this matters:</b> the pre-fix profile (2026-05-27, 42k Persons ×
 * 11 typed fields = 462k field dispatches) showed {@code resolveCaller()} +
 * {@code Locale.getDefault()} being re-evaluated per field per row. Caching
 * once at query begin eliminates 462k × (SecurityContextHolder read + JWT
 * claim extract + operator.getUser + LocaleContextHolder read) — each cheap
 * individually, costly in aggregate.
 *
 * <p>Spring auto-discovers {@code Instrumentation} beans through the
 * {@link org.springframework.beans.factory.ObjectProvider} chain on
 * {@link HotSwappableGraphQlSource}. No explicit registration needed.
 */
@Component
public class RequestContextInstrumentation extends SimplePerformantInstrumentation
{
    private final StorageOperator operator;
    private final org.rapla.server.spring.JwtUserResolver jwtUserResolver;

    public RequestContextInstrumentation(StorageOperator operator,
            org.rapla.server.spring.JwtUserResolver jwtUserResolver)
    {
        this.operator = operator;
        this.jwtUserResolver = jwtUserResolver;
    }

    @Override
    public InstrumentationContext<graphql.ExecutionResult> beginExecution(
            InstrumentationExecutionParameters parameters,
            InstrumentationState state)
    {
        // SecurityContextHolder is still bound to the request thread at this
        // point (Spring's filter chain ran first); LocaleContextHolder too.
        GraphQLContext ctx = parameters.getGraphQLContext();
        User caller = jwtUserResolver.resolveCurrentUserOrNull();
        PermissionController pc = operator.getPermissionController();
        Locale locale = LocaleContextHolder.getLocale();
        ctx.put(RequestCtx.KEY, new RequestCtx(caller, pc, locale));
        return SimpleInstrumentationContext.noOp();
    }

    /**
     * Read the cached per-query context. Returns a non-null record with
     * possibly-null caller (anonymous queries) and never-null
     * {@link PermissionController} + {@link Locale} (resolved at begin).
     */
    public static RequestCtx from(GraphQLContext ctx)
    {
        RequestCtx rc = ctx.get(RequestCtx.KEY);
        return rc != null ? rc : RequestCtx.EMPTY;
    }

    /**
     * Per-query state. {@code caller} is null for anonymous queries.
     * {@code permissionController} is always non-null (operator's singleton).
     * {@code locale} defaults to the request's resolved locale or the JVM default.
     */
    public record RequestCtx(User caller, PermissionController permissionController, Locale locale)
    {
        static final String KEY = "rapla.requestCtx";

        /** Used when no instrumentation ran (e.g. unit-test paths that bypass
         *  the standard execution chain). Caller is null; PC is null too —
         *  fetchers must handle the null-PC case (it means "trust no one"). */
        static final RequestCtx EMPTY = new RequestCtx(null, null, Locale.getDefault());
    }
}
