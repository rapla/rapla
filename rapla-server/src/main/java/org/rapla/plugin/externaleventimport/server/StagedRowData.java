package org.rapla.plugin.externaleventimport.server;

import org.rapla.plugin.externaleventimport.ImportItem;

public class StagedRowData
{
    /** The row's payload, or an empty state when the row carries none. Shared by the reader,
     *  the assembler and the reconciliation — one place that knows how a row deserializes. */
    static StagedRowData of(org.rapla.rest.JsonParserWrapper.JsonParser json,
            org.rapla.entities.storage.ExternalSyncEntity row)
    {
        final String data = row.getData();
        return data == null || data.isEmpty() ? new StagedRowData() : json.fromJson(data, StagedRowData.class);
    }

    private ImportItem source;
    private String scopeKey;
    private String ignoredSince;
    private String changedSince;

    public ImportItem getSource() { return source; }

    public void setSource(ImportItem source) { this.source = source; }

    public String getScopeKey() { return scopeKey; }

    public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }

    public String getIgnoredSince() { return ignoredSince; }

    public void setIgnoredSince(String ignoredSince) { this.ignoredSince = ignoredSince; }

    public String getChangedSince() { return changedSince; }

    public void setChangedSince(String changedSince) { this.changedSince = changedSince; }
}
