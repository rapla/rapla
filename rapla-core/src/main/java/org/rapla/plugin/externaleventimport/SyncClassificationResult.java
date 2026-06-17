package org.rapla.plugin.externaleventimport;

import org.rapla.entities.dynamictype.internal.ClassificationImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * Response of {@code ExternalEventImportService.syncClassification}: the merged classification
 * (seeded from the shipped one, source values applied) plus the rapla ids of the allocatables
 * the source row references (e.g. Dualis courses/lecturers). Allocatables cross as ids, not
 * entities — the client resolves them from its permission-filtered cache; unresolvable ids are
 * silently skipped.
 */
public class SyncClassificationResult
{
    private ClassificationImpl classification;
    private List<String> allocatableIds = new ArrayList<>();

    public SyncClassificationResult()
    {
    }

    public ClassificationImpl getClassification()
    {
        return classification;
    }

    public void setClassification(ClassificationImpl classification)
    {
        this.classification = classification;
    }

    public List<String> getAllocatableIds()
    {
        return allocatableIds;
    }

    public void setAllocatableIds(List<String> allocatableIds)
    {
        this.allocatableIds = allocatableIds;
    }
}
