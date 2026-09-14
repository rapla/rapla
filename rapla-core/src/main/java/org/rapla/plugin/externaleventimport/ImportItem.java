package org.rapla.plugin.externaleventimport;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One row in the wizard's result table. Keys in {@link #hierarchy} match
 * {@code HierarchyLevel.key()}; keys in {@link #columns} match {@code ResultColumn.key()}.
 */
public class ImportItem
{
    private String sourceItemId;
    private Map<String, String> hierarchy = new LinkedHashMap<>();
    private Map<String, Object> columns = new LinkedHashMap<>();
    /** True if this source item is already imported (a matching entity exists). Generic flag,
     *  set by the deployment impl; lets the wizard disable "create" when only already-imported
     *  items are selected. */
    private boolean imported;
    /** Opaque raw source data (e.g. the Dualis event fields) the deployment impl needs to map this
     *  row into a reservation later. The generic client never reads it — it only relays the selected
     *  items back to {@code createReservations}, where the same impl maps them. Avoids a second
     *  source-system query and keeps the mapping server-side. */
    private Map<String, Object> sourceData = new LinkedHashMap<>();

    public ImportItem()
    {
    }

    public ImportItem(String sourceItemId, Map<String, String> hierarchy, Map<String, Object> columns)
    {
        this.sourceItemId = sourceItemId;
        if (hierarchy != null) this.hierarchy = new LinkedHashMap<>(hierarchy);
        if (columns != null) this.columns = new LinkedHashMap<>(columns);
    }

    public String getSourceItemId()
    {
        return sourceItemId;
    }

    public void setSourceItemId(String sourceItemId)
    {
        this.sourceItemId = sourceItemId;
    }

    public Map<String, String> getHierarchy()
    {
        return hierarchy;
    }

    public void setHierarchy(Map<String, String> hierarchy)
    {
        this.hierarchy = hierarchy;
    }

    public Map<String, Object> getColumns()
    {
        return columns;
    }

    public void setColumns(Map<String, Object> columns)
    {
        this.columns = columns;
    }

    public boolean isImported()
    {
        return imported;
    }

    public void setImported(boolean imported)
    {
        this.imported = imported;
    }

    public Map<String, Object> getSourceData()
    {
        return sourceData;
    }

    public void setSourceData(Map<String, Object> sourceData)
    {
        this.sourceData = sourceData;
    }
}
