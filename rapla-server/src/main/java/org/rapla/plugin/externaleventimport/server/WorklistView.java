package org.rapla.plugin.externaleventimport.server;

import java.util.List;

public record WorklistView(List<WorklistGroup> groups, WorklistCounts counts, Long lastRefresh, String lastError)
{
    public static WorklistView empty()
    {
        return new WorklistView(List.of(), WorklistCounts.zero(), null, null);
    }
}
