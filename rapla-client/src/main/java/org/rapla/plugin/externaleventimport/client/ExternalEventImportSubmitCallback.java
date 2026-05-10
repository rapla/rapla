package org.rapla.plugin.externaleventimport.client;

import java.util.List;

/**
 * Callback the wizard panel invokes when the user picks rows in the result table and
 * clicks "create" or "synchronize". The implementation is responsible for sending the
 * picked source-item IDs to {@code ExternalEventImportService.createReservations}.
 */
public interface ExternalEventImportSubmitCallback
{
    void submit(List<String> sourceItemIds);

    boolean isValidSelection(List<String> sourceItemIds);
}
