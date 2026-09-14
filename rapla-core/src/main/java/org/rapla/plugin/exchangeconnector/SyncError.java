package org.rapla.plugin.exchangeconnector;

import java.util.Objects;

public class SyncError    {

    // Empty constructor for json serializer
    SyncError()
    {
    }
    public SyncError(String appointmentDetail, String errorMessage) {
        this.appointmentDetail = appointmentDetail;
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return errorMessage + " " + appointmentDetail;
    }

    String appointmentDetail;
    String errorMessage;
    public String getAppointmentDetail() {
        return appointmentDetail;
    }
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SyncError syncError = (SyncError) o;
        return Objects.equals(errorMessage, syncError.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(errorMessage);
    }
}