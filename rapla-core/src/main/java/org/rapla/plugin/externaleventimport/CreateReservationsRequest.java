package org.rapla.plugin.externaleventimport;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Request payload for {@code ExternalEventImportService.createReservations}. The wizard
 * sends the IDs of the rows the user picked from the result table; the server-side impl
 * resolves each ID, builds {@code Reservation} objects (deployment-specific mapping logic),
 * stores them via the facade, and returns the new reservation IDs so the client can open
 * an editor on them.
 *
 * <p>{@link #updateExistingReservation}/{@link #existingReservationId} cover the
 * sync-an-existing-reservation flow: only one source item is allowed, and its data is
 * folded into the named reservation rather than creating a new one.
 */
public class CreateReservationsRequest
{
    private List<String> sourceItemIds = new ArrayList<>();
    private String templateAllocatableId;
    private List<String> additionalAllocatableIds = new ArrayList<>();
    private LocalDateTime calendarStart;
    private LocalDateTime calendarEnd;
    private boolean updateExistingReservation;
    private String existingReservationId;

    public CreateReservationsRequest()
    {
    }

    public List<String> getSourceItemIds()
    {
        return sourceItemIds;
    }

    public void setSourceItemIds(List<String> sourceItemIds)
    {
        this.sourceItemIds = sourceItemIds;
    }

    public String getTemplateAllocatableId()
    {
        return templateAllocatableId;
    }

    public void setTemplateAllocatableId(String templateAllocatableId)
    {
        this.templateAllocatableId = templateAllocatableId;
    }

    public List<String> getAdditionalAllocatableIds()
    {
        return additionalAllocatableIds;
    }

    public void setAdditionalAllocatableIds(List<String> additionalAllocatableIds)
    {
        this.additionalAllocatableIds = additionalAllocatableIds;
    }

    public LocalDateTime getCalendarStart()
    {
        return calendarStart;
    }

    public void setCalendarStart(LocalDateTime calendarStart)
    {
        this.calendarStart = calendarStart;
    }

    public LocalDateTime getCalendarEnd()
    {
        return calendarEnd;
    }

    public void setCalendarEnd(LocalDateTime calendarEnd)
    {
        this.calendarEnd = calendarEnd;
    }

    public boolean isUpdateExistingReservation()
    {
        return updateExistingReservation;
    }

    public void setUpdateExistingReservation(boolean updateExistingReservation)
    {
        this.updateExistingReservation = updateExistingReservation;
    }

    public String getExistingReservationId()
    {
        return existingReservationId;
    }

    public void setExistingReservationId(String existingReservationId)
    {
        this.existingReservationId = existingReservationId;
    }
}
