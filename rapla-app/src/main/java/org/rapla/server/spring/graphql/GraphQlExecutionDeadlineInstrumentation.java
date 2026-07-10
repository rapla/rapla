package org.rapla.server.spring.graphql;

import graphql.execution.AbortExecutionException;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetcher;
import org.springframework.stereotype.Component;

/**
 * security-audit §G — bounds the wall-clock time a single GraphQL execution may
 * spend. Each query gets a deadline ({@code now + rapla.graphql.execution-budget-millis})
 * captured at state-creation; every field fetch checks it first and, once blown,
 * aborts instead of doing more work. A runaway query therefore stops touching the
 * storage / permission layer shortly after the budget elapses rather than tying up a
 * worker thread (and the shared dispatch lock) for the full traversal.
 *
 * <p>Unlike a query-depth cap this also catches the shallow-but-wide queries (large
 * result fan-out over many rows) that run long without being deeply nested. It is a
 * cheap {@code System.nanoTime()} compare per field — negligible next to a real fetch.
 *
 * <p>Auto-discovered as an {@code Instrumentation} bean via the {@code ObjectProvider}
 * chain on {@link HotSwappableGraphQlSource} (same mechanism as
 * {@link RequestContextInstrumentation}); disabled when the budget is {@code <= 0}.
 */
@Component
public class GraphQlExecutionDeadlineInstrumentation extends SimplePerformantInstrumentation
{
    private final long budgetMillis;

    public GraphQlExecutionDeadlineInstrumentation(RaplaGraphqlProperties properties)
    {
        this.budgetMillis = properties.getExecutionBudgetMillis();
    }

    private static final class Deadline implements InstrumentationState
    {
        final long deadlineNanos;
        Deadline(long deadlineNanos) { this.deadlineNanos = deadlineNanos; }
    }

    @Override
    public InstrumentationState createState(InstrumentationCreateStateParameters parameters)
    {
        if (budgetMillis <= 0)
        {
            return null;
        }
        return new Deadline(System.nanoTime() + budgetMillis * 1_000_000L);
    }

    @Override
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher,
            InstrumentationFieldFetchParameters parameters, InstrumentationState state)
    {
        if (!(state instanceof Deadline deadline))
        {
            return dataFetcher;
        }
        return env -> {
            if (System.nanoTime() > deadline.deadlineNanos)
            {
                throw new AbortExecutionException(
                        "GraphQL execution exceeded the " + budgetMillis + "ms budget (rapla.graphql.execution-budget-millis)");
            }
            return dataFetcher.get(env);
        };
    }
}
