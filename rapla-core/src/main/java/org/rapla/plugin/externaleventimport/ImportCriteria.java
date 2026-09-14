package org.rapla.plugin.externaleventimport;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Filter criteria for {@code ExternalEventImportService.loadEvents}. Keys match
 * {@code HierarchyLevel.key()} from the metadata; values are the user-selected
 * value at each level. Map shape lets the contract stay stable when deployments
 * have different hierarchy depths.
 */
public class ImportCriteria
{
    private Map<String, String> selectedHierarchy = new LinkedHashMap<>();
    private java.util.List<String> selectedAllocatableIds = new java.util.ArrayList<>();

    public ImportCriteria()
    {
    }

    public ImportCriteria(Map<String, String> selectedHierarchy, java.util.List<String> selectedAllocatableIds)
    {
        if (selectedHierarchy != null) this.selectedHierarchy = new LinkedHashMap<>(selectedHierarchy);
        if (selectedAllocatableIds != null) this.selectedAllocatableIds = new java.util.ArrayList<>(selectedAllocatableIds);
    }

    public Map<String, String> getSelectedHierarchy()
    {
        return selectedHierarchy;
    }

    public void setSelectedHierarchy(Map<String, String> selectedHierarchy)
    {
        this.selectedHierarchy = selectedHierarchy;
    }

    public java.util.List<String> getSelectedAllocatableIds()
    {
        return selectedAllocatableIds;
    }

    public void setSelectedAllocatableIds(java.util.List<String> selectedAllocatableIds)
    {
        this.selectedAllocatableIds = selectedAllocatableIds;
    }
}
