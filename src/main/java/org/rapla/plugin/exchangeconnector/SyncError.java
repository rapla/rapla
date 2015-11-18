package org.rapla.plugin.exchangeconnector;

public class SyncError    {
    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((appointmentDetail == null) ? 0 : appointmentDetail.hashCode());
        result = prime * result + ((errorMessage == null) ? 0 : errorMessage.hashCode());
        return result;
    }
    
    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SyncError other = (SyncError) obj;
        if (appointmentDetail == null)
        {
            if (other.appointmentDetail != null)
                return false;
        }
        else if (!appointmentDetail.equals(other.appointmentDetail))
            return false;
        if (errorMessage == null)
        {
            if (other.errorMessage != null)
                return false;
        }
        else if (!errorMessage.equals(other.errorMessage))
            return false;
        return true;
    }
    // Empty constructor for json serializer
    SyncError()
    {
    }
    public SyncError(String appointmentDetail, String errorMessage) {
        this.appointmentDetail = appointmentDetail;
        this.errorMessage = errorMessage;
    }
    String appointmentDetail;
    String errorMessage;
    public String getAppointmentDetail() {
        return appointmentDetail;
    }
    public String getErrorMessage() {
        return errorMessage;
    }
}