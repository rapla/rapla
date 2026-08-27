package org.rapla.plugin.externaleventimport.server;

import java.util.List;

public record WorklistItem(String sourceItemId, String scopeKey, StagedEventState state, List<WorklistColumn> columns,
                           String changedSince, String ignoredSince,
                           String boundReservationId)
{
}
