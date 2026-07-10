package org.rapla.server.spring.graphql;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * PRD 074 — variable resolution for a stored view, shared by the two callers that execute one:
 * {@link StoredViewInterceptor} (the SPA's {@code /api/graphql} transport) and the PRD 097
 * document render pipeline. One implementation, so a document and the SPA agree on the window.
 */
public final class ViewVariables
{
    private static final DateTimeFormatter ISO_DT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private ViewVariables() {}

    /**
     * Merge variable defaults: stored defaults form the base, caller-supplied vars override them,
     * then the Monday→Monday+7 window fills any still-missing {@code filter.from}/{@code filter.to}.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> mergeDefaults(Map<String, Object> callerVars, String storedDefaultsJson)
    {
        Map<String, Object> vars = new LinkedHashMap<>(parseDefaults(storedDefaultsJson));
        if (callerVars != null) vars.putAll(callerVars);

        Map<String, Object> filter = vars.get("filter") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        if (filter.containsKey("from") && filter.containsKey("to")) return vars;

        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDateTime from = monday.atStartOfDay();
        LocalDateTime to = monday.plusDays(7).atStartOfDay();

        Map<String, Object> mergedFilter = new LinkedHashMap<>(filter);
        if (!filter.containsKey("from")) mergedFilter.put("from", from.format(ISO_DT));
        if (!filter.containsKey("to")) mergedFilter.put("to", to.format(ISO_DT));

        vars.put("filter", mergedFilter);
        return vars;
    }

    /** Parse a stored {@code defaultVariables} JSON object; unparseable or absent yields an empty map. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseDefaults(String json)
    {
        if (json == null || json.isBlank()) return Map.of();
        try
        {
            Object parsed = MAPPER.readValue(json, Object.class);
            return parsed instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        }
        catch (Exception e)
        {
            return Map.of();
        }
    }
}
