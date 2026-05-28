package org.rapla.server.spring.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import java.util.LinkedHashMap;
import java.util.Map;
import org.rapla.framework.RaplaException;
import org.rapla.server.spring.graphql.ReservationMutationController.ReservationMutationException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.stereotype.Component;

/**
 * PRD 056 — translates exceptions thrown by mutation resolvers into
 * GraphQL errors with the documented {@link ValidationError} shape:
 * {@code { code, path, message }} + extensions.
 *
 * <p>Two source types:
 * <ul>
 *   <li>{@link ReservationMutationException} — explicit business / §12 errors
 *       carry a typed code + path; mapped to the canonical error taxonomy
 *       in PRD 056.</li>
 *   <li>{@link RaplaException} — unexpected storage-layer failures; mapped
 *       to {@code STORAGE_ERROR} with the original message.</li>
 * </ul>
 *
 * <p>All other exceptions fall through to graphql-java's default error
 * handling (logged as INTERNAL_ERROR; message not leaked).
 */
@Component
public class MutationExceptionResolver extends DataFetcherExceptionResolverAdapter
{
    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env)
    {
        if (ex instanceof ReservationMutationException rme)
        {
            Map<String, Object> extensions = new LinkedHashMap<>();
            extensions.put("code", rme.code());
            extensions.put("path", rme.path());
            return GraphqlErrorBuilder.newError(env)
                    .message(rme.getMessage())
                    .errorType(graphql.ErrorType.ValidationError)
                    .extensions(extensions)
                    .build();
        }
        if (ex instanceof RaplaException re)
        {
            Map<String, Object> extensions = new LinkedHashMap<>();
            extensions.put("code", "STORAGE_ERROR");
            extensions.put("path", "");
            return GraphqlErrorBuilder.newError(env)
                    .message(re.getMessage())
                    .errorType(graphql.ErrorType.DataFetchingException)
                    .extensions(extensions)
                    .build();
        }
        if (ex instanceof IllegalArgumentException iae)
        {
            // Argument-validation errors from read-side resolvers (PRD 055
            // mandatory window + 365-day cap, etc.) and other resolver-level
            // precondition checks.
            Map<String, Object> extensions = new LinkedHashMap<>();
            extensions.put("code", "INVALID_VALUE");
            extensions.put("path", "");
            return GraphqlErrorBuilder.newError(env)
                    .message(iae.getMessage())
                    .errorType(graphql.ErrorType.ValidationError)
                    .extensions(extensions)
                    .build();
        }
        return null; // fall through to default handler
    }
}
