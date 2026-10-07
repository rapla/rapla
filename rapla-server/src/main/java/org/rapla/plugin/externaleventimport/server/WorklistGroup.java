package org.rapla.plugin.externaleventimport.server;

import java.util.List;

public record WorklistGroup(String resourceId, String name, List<WorklistItem> items, WorklistCounts counts)
{
}
