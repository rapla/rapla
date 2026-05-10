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
}
