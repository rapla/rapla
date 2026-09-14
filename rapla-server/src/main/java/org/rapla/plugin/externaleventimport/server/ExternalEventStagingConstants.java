package org.rapla.plugin.externaleventimport.server;

public interface ExternalEventStagingConstants
{
    String CONTEXT_EVENT_STAGING = "EVENT_STAGING";

    String CONTEXT_EVENT_STAGING_META = "EVENT_STAGING_META";

    String ID_PREFIX = "staging:";

    String META_ID = ID_PREFIX + "__reconcile";

    static String idOf(String sourceItemId)
    {
        return ID_PREFIX + sourceItemId;
    }
}
