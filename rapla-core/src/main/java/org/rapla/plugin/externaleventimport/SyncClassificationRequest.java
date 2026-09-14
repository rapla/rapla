package org.rapla.plugin.externaleventimport;

import org.rapla.entities.dynamictype.internal.ClassificationImpl;

/**
 * Request payload for {@code ExternalEventImportService.syncClassification} (PRD 068 sync flow).
 * Carries the one selected source row (with its {@code sourceData}) plus the classification of
 * the reservation currently open in the editor — the server merges the source values into it
 * via {@code newClassificationFrom} and returns the result without storing anything.
 *
 * <p>The classification field is the concrete {@link ClassificationImpl} (the {@code UpdateEvent}
 * convention): Jackson never hits an abstract type and springdoc generates a real model.
 */
public class SyncClassificationRequest
{
    private ImportItem selectedItem;
    private ClassificationImpl classification;

    public SyncClassificationRequest()
    {
    }

    public ImportItem getSelectedItem()
    {
        return selectedItem;
    }

    public void setSelectedItem(ImportItem selectedItem)
    {
        this.selectedItem = selectedItem;
    }

    public ClassificationImpl getClassification()
    {
        return classification;
    }

    public void setClassification(ClassificationImpl classification)
    {
        this.classification = classification;
    }
}
