package org.rapla.server.spring.graphql;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.scalars.ExtendedScalars;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * PRD 035: registers GraphQL scalar types not in graphql-java's built-in set.
 *
 * <ul>
 *   <li>{@code DateTime} — ISO {@link java.time.OffsetDateTime}. From
 *       graphql-java-extended-scalars. Used for instant-in-time fields
 *       (e.g. {@code Query.serverTime}).</li>
 *   <li>{@code LocalDateTime} — ISO {@link java.time.LocalDateTime}, wall-time
 *       without offset. Custom (extended-scalars doesn't ship one). rapla's
 *       domain time fields are LocalDateTime per PRD 014 — appointments,
 *       periods, etc. are wall-time semantics (a 10:00 lecture happens at
 *       10:00 local time, not at a particular UTC instant).</li>
 * </ul>
 */
@Configuration
public class GraphQlScalarConfig
{
    /**
     * Custom {@code LocalDateTime} scalar — serializes as ISO_LOCAL_DATE_TIME
     * ({@code 2026-05-25T14:30:00}). Accepts the same format on input,
     * either as a JSON string (parseValue) or a GraphQL literal (parseLiteral).
     */
    public static final GraphQLScalarType LOCAL_DATE_TIME = GraphQLScalarType.newScalar()
            .name("LocalDateTime")
            .description("ISO-8601 wall-time LocalDateTime — no offset / timezone. "
                    + "Format: 2026-05-25T14:30:00. rapla domain time fields per PRD 014.")
            .coercing(new Coercing<LocalDateTime, String>()
            {
                private final DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

                @Override
                public String serialize(Object dataFetcherResult, GraphQLContext ctx, Locale locale)
                        throws CoercingSerializeException
                {
                    if (dataFetcherResult instanceof LocalDateTime ldt) return ldt.format(fmt);
                    throw new CoercingSerializeException(
                            "Expected LocalDateTime, got " + (dataFetcherResult == null
                                    ? "null"
                                    : dataFetcherResult.getClass().getName()));
                }

                @Override
                public LocalDateTime parseValue(Object input, GraphQLContext ctx, Locale locale)
                        throws CoercingParseValueException
                {
                    if (input instanceof LocalDateTime ldt) return ldt;
                    if (input instanceof String s)
                    {
                        try { return LocalDateTime.parse(s, fmt); }
                        catch (DateTimeParseException e)
                        {
                            throw new CoercingParseValueException("Invalid LocalDateTime: " + s, e);
                        }
                    }
                    throw new CoercingParseValueException(
                            "Expected String or LocalDateTime, got " + (input == null
                                    ? "null"
                                    : input.getClass().getName()));
                }

                @Override
                public LocalDateTime parseLiteral(Value<?> input, CoercedVariables variables,
                        GraphQLContext ctx, Locale locale) throws CoercingParseLiteralException
                {
                    if (input instanceof StringValue sv)
                    {
                        try { return LocalDateTime.parse(sv.getValue(), fmt); }
                        catch (DateTimeParseException e)
                        {
                            throw new CoercingParseLiteralException("Invalid LocalDateTime: " + sv.getValue(), e);
                        }
                    }
                    throw new CoercingParseLiteralException(
                            "Expected StringValue literal, got " + input.getClass().getName());
                }
            })
            .build();

    @Bean
    public RuntimeWiringConfigurer scalarConfigurer()
    {
        return wiringBuilder -> wiringBuilder
                .scalar(ExtendedScalars.DateTime)
                .scalar(LOCAL_DATE_TIME)
                // PRD 055 reservation read types — RepeatingRule.exceptions is LocalDate
                .scalar(ExtendedScalars.Date);
        // Duration scalar removed (PRD 101) — move/copyReservations now use the typed
        // Target (@oneOf day|dateTime) transpose anchor instead of an ISO-8601 dateShift.
    }
}
