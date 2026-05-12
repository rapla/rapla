package org.rapla.plugin.reservationedit;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pins the wire shape of {@link ReservationEditService} (PRD 024 Phase 1).
 */
class ReservationEditServiceContractTest
{
    @Test
    void rootPathIsEdit()
    {
        HttpExchange root = ReservationEditService.class.getAnnotation(HttpExchange.class);
        assertNotNull(root);
        assertEquals("/edit", root.value());
    }

    @Test
    void validateRecurrenceIsPostExchange()
    {
        Method m = methodNamed("validateRecurrence");
        PostExchange pe = m.getAnnotation(PostExchange.class);
        assertNotNull(pe);
        assertEquals("/validate-recurrence", pe.value());
        assertEquals(RecurrenceValidation.class, m.getReturnType());
        assertEquals(1, m.getParameterCount());
        Parameter p = m.getParameters()[0];
        assertEquals(RecurrenceRule.class, p.getType());
        assertNotNull(p.getAnnotation(RequestBody.class));
    }

    @Test
    void recurrenceRuleComponentsAreStable()
    {
        assertRecordComponents(RecurrenceRule.class,
                "type", "interval", "weekdays", "endingMode",
                "endDate", "repeatCount", "appointmentStart");
    }

    @Test
    void recurrenceValidationComponentsAreStable()
    {
        assertRecordComponents(RecurrenceValidation.class, "valid", "issues");
        assertRecordComponents(RecurrenceValidation.Issue.class, "code", "detail");
    }

    @Test
    void checkConflictsIsPostExchange()
    {
        Method m = methodNamed("checkConflicts");
        PostExchange pe = m.getAnnotation(PostExchange.class);
        assertNotNull(pe);
        assertEquals("/check-conflicts", pe.value());
        assertEquals(ConflictReport.class, m.getReturnType());
        assertEquals(1, m.getParameterCount());
        Parameter p = m.getParameters()[0];
        assertEquals(ConflictCheckRequest.class, p.getType());
        assertNotNull(p.getAnnotation(RequestBody.class));
    }

    @Test
    void conflictCheckRequestComponentsAreStable()
    {
        assertRecordComponents(ConflictCheckRequest.class,
                "allocatableIds", "appointments", "today");
    }

    @Test
    void appointmentSpecComponentsAreStable()
    {
        assertRecordComponents(AppointmentSpec.class, "start", "end", "recurrence");
    }

    @Test
    void allocationOutcomeComponentsAreStable()
    {
        assertRecordComponents(AllocationOutcomeDto.class,
                "allocatableId", "conflictingAppointments",
                "conflictCount", "permissionConflictCount",
                "aggregateRequestStatus");
    }

    @Test
    void conflictReportComponentsAreStable()
    {
        assertRecordComponents(ConflictReport.class, "outcomes");
    }

    @Test
    void expandBlocksIsPostExchange()
    {
        Method m = methodNamed("expandBlocks");
        PostExchange pe = m.getAnnotation(PostExchange.class);
        assertNotNull(pe);
        assertEquals("/expand-blocks", pe.value());
        assertEquals(java.util.List.class, m.getReturnType());
        assertEquals(1, m.getParameterCount());
        Parameter p = m.getParameters()[0];
        assertEquals(ExpandBlocksRequest.class, p.getType());
        assertNotNull(p.getAnnotation(RequestBody.class));
    }

    @Test
    void expandBlocksRequestComponentsAreStable()
    {
        assertRecordComponents(ExpandBlocksRequest.class,
                "appointment", "windowStart", "windowEnd", "excludeExceptions");
    }

    @Test
    void appointmentBlockDtoComponentsAreStable()
    {
        assertRecordComponents(AppointmentBlockDto.class, "start", "end");
    }

    private static void assertRecordComponents(Class<?> recordClass, String... expected)
    {
        if (!recordClass.isRecord()) fail(recordClass.getName() + " must be a record");
        RecordComponent[] comps = recordClass.getRecordComponents();
        String[] actual = Arrays.stream(comps).map(RecordComponent::getName).toArray(String[]::new);
        assertTrue(Arrays.equals(expected, actual),
                "record components mismatch for " + recordClass.getSimpleName()
                + ":\n  expected " + Arrays.toString(expected)
                + "\n  actual   " + Arrays.toString(actual));
    }

    private static Method methodNamed(String name)
    {
        return Arrays.stream(ReservationEditService.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElseGet(() -> { fail("no method named " + name); return null; });
    }
}
