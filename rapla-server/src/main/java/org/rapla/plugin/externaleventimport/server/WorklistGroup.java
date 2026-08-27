package org.rapla.plugin.externaleventimport.server;

import java.util.List;

public record WorklistGroup(String allocatableId, String name, List<WorklistItem> items, WorklistCounts counts)
{
}
