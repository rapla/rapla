package org.rapla.plugin.externaleventimport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Self-description of an {@link ExternalEventImportService} impl, returned by
 * {@code getMetadata()}. The wizard uses it to render labels, columns and
 * affordances entirely from server-supplied data — vanilla rapla carries no
 * domain-specific strings.
 *
 * <p>{@link #uiMessageOverrides} is the optional escape hatch: when a generic
 * parameterized message in the bundle won't fit a deployment's wording, the
 * server supplies the fully-rendered message under the bundle key; the wizard
 * uses the override verbatim instead of formatting the template.
 */
public class ExternalEventImportMetadata
{
    private String sourceName;
    private List<HierarchyLevel> hierarchyLevels = List.of();
    private List<ResultColumn> resultColumns = List.of();
    /** Column keys (subset of {@link #resultColumns}) the wizard renders a value-filter dropdown for,
     *  letting the user narrow the result table (e.g. by Studiengang / Semester / Kurs). */
    private List<String> filterColumns = List.of();
    private boolean supportsCsvImport;
    private String csvFileFilterLabel;
    private Map<String, String> uiMessageOverrides = new LinkedHashMap<>();
    /** rapla {@code DynamicType} key whose {@code Allocatable}s are presentable as the
     *  "items to import for" in the wizard (e.g. dhbw uses {@code "course"}). When
     *  the calling user has Allocatables of this type pre-selected in the calendar,
     *  the wizard skips its own selection dialog. Null/empty means the wizard does
     *  not show an Allocatable-selection step at all. */
    private String selectableAllocatableTypeKey;
    /** Maximum number of source items the user may pick at once for createReservations.
     *  0 means "no limit"; the wizard uses {@code too_many_items_selected} when exceeded. */
    private int maxSelectableItems;

    public ExternalEventImportMetadata()
    {
    }

    public ExternalEventImportMetadata(String sourceName, List<HierarchyLevel> hierarchyLevels, List<ResultColumn> resultColumns, boolean supportsCsvImport,
            String csvFileFilterLabel, Map<String, String> uiMessageOverrides, String selectableAllocatableTypeKey, int maxSelectableItems)
    {
        this.sourceName = sourceName;
        if (hierarchyLevels != null) this.hierarchyLevels = List.copyOf(hierarchyLevels);
        if (resultColumns != null) this.resultColumns = List.copyOf(resultColumns);
        this.supportsCsvImport = supportsCsvImport;
        this.csvFileFilterLabel = csvFileFilterLabel;
        if (uiMessageOverrides != null) this.uiMessageOverrides = new LinkedHashMap<>(uiMessageOverrides);
        this.selectableAllocatableTypeKey = selectableAllocatableTypeKey;
        this.maxSelectableItems = maxSelectableItems;
    }

    public String getSourceName()
    {
        return sourceName;
    }

    public void setSourceName(String sourceName)
    {
        this.sourceName = sourceName;
    }

    public List<HierarchyLevel> getHierarchyLevels()
    {
        return hierarchyLevels;
    }

    public void setHierarchyLevels(List<HierarchyLevel> hierarchyLevels)
    {
        this.hierarchyLevels = hierarchyLevels;
    }

    public List<ResultColumn> getResultColumns()
    {
        return resultColumns;
    }

    public void setResultColumns(List<ResultColumn> resultColumns)
    {
        this.resultColumns = resultColumns;
    }

    public List<String> getFilterColumns()
    {
        return filterColumns;
    }

    public void setFilterColumns(List<String> filterColumns)
    {
        this.filterColumns = filterColumns;
    }

    public boolean isSupportsCsvImport()
    {
        return supportsCsvImport;
    }

    public void setSupportsCsvImport(boolean supportsCsvImport)
    {
        this.supportsCsvImport = supportsCsvImport;
    }

    public String getCsvFileFilterLabel()
    {
        return csvFileFilterLabel;
    }

    public void setCsvFileFilterLabel(String csvFileFilterLabel)
    {
        this.csvFileFilterLabel = csvFileFilterLabel;
    }

    public Map<String, String> getUiMessageOverrides()
    {
        return uiMessageOverrides;
    }

    public void setUiMessageOverrides(Map<String, String> uiMessageOverrides)
    {
        this.uiMessageOverrides = uiMessageOverrides;
    }

    public String getSelectableAllocatableTypeKey()
    {
        return selectableAllocatableTypeKey;
    }

    public void setSelectableAllocatableTypeKey(String selectableAllocatableTypeKey)
    {
        this.selectableAllocatableTypeKey = selectableAllocatableTypeKey;
    }

    public int getMaxSelectableItems()
    {
        return maxSelectableItems;
    }

    public void setMaxSelectableItems(int maxSelectableItems)
    {
        this.maxSelectableItems = maxSelectableItems;
    }
}
