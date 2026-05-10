package org.rapla.plugin.externaleventimport;

import java.util.ArrayList;
import java.util.List;

public class ExternalEventImportResult
{
    private List<ImportItem> items = new ArrayList<>();

    public ExternalEventImportResult()
    {
    }

    public ExternalEventImportResult(List<ImportItem> items)
    {
        if (items != null) this.items = new ArrayList<>(items);
    }

    public List<ImportItem> getItems()
    {
        return items;
    }

    public void setItems(List<ImportItem> items)
    {
        this.items = items;
    }
}
