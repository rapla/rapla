package org.rapla.server.spring.graphql;

import graphql.execution.AbortExecutionException;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.schema.DataFetcher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * security-audit §G — the per-execution wall-clock deadline. A field fetch after the
 * budget elapses must abort; one within budget must pass through unchanged; and a
 * non-positive budget disables the check entirely (fetcher returned untouched).
 */
class GraphQlExecutionDeadlineInstrumentationTest
{
    private static GraphQlExecutionDeadlineInstrumentation withBudget(long millis)
    {
        RaplaGraphqlProperties props = new RaplaGraphqlProperties();
        props.setExecutionBudgetMillis(millis);
        return new GraphQlExecutionDeadlineInstrumentation(props);
    }

    @Test
    void fetchAfterBudgetElapsedAborts() throws Exception
    {
        GraphQlExecutionDeadlineInstrumentation instr = withBudget(1);   // 1ms budget
        InstrumentationState state = instr.createState(null);
        DataFetcher<?> wrapped = instr.instrumentDataFetcher(env -> "ok", null, state);
        Thread.sleep(25);   // well past the 1ms deadline
        assertThrows(AbortExecutionException.class, () -> wrapped.get(null));
    }

    @Test
    void fetchWithinBudgetPassesThrough() throws Exception
    {
        GraphQlExecutionDeadlineInstrumentation instr = withBudget(60_000);   // 60s budget
        InstrumentationState state = instr.createState(null);
        DataFetcher<?> wrapped = instr.instrumentDataFetcher(env -> "ok", null, state);
        assertEquals("ok", wrapped.get(null));
    }

    @Test
    void nonPositiveBudgetDisablesTheCheck()
    {
        GraphQlExecutionDeadlineInstrumentation instr = withBudget(0);
        InstrumentationState state = instr.createState(null);
        assertNull(state, "no deadline state when disabled");
        DataFetcher<?> original = env -> "ok";
        // With no Deadline state the original fetcher is returned untouched (zero overhead).
        assertSame(original, instr.instrumentDataFetcher(original, null, state));
    }
}
