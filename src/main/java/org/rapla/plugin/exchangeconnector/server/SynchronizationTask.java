package org.rapla.plugin.exchangeconnector.server;

import java.io.Serializable;
import java.util.Date;

import org.rapla.entities.User;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.storage.ReferenceInfo;

public class SynchronizationTask implements Serializable
{
	public enum SyncStatus implements Serializable
	{
		toUpdate(true)
		,toDelete(true)
		,synched(false)
		,deleted(false);
		boolean unsynchronized;
		SyncStatus(boolean unszynchronized)
		{
			this.unsynchronized =unszynchronized;
		}
		
		public boolean isUnsynchronized() {
			return unsynchronized;
		}
	}

	private static final long serialVersionUID = 219323872273312836L;
	String userId;
	String appointmentId;
	Date lastRetry;
	private int retries = -1;
	String lastError;
	
	//TimeInterval syncInterval;
	SyncStatus status;
	private String persistantId;
	
	public SynchronizationTask(ReferenceInfo<Appointment> appointmentId, ReferenceInfo<User> userId, int retries, Date lastRetry, String lastError) {
		this.userId = userId.getId();
		this.appointmentId = appointmentId.getId();
		status = SyncStatus.toUpdate;
		this.retries = retries;
		this.lastRetry = lastRetry;
		this.lastError = lastError;
	}
	
	public void increaseRetries(String lastError)
	{
        this.lastError = lastError;
        retries++;
		this.lastRetry = new Date();
	}
	
    public String getLastError() 
    {
        return lastError;
    }

	public String getUserId() {
		return userId;
	}

	public ReferenceInfo<User> getUserRef()
	{
		return new ReferenceInfo<User>(userId,User.class);
	}


	public String getAppointmentId() {
		return appointmentId;
	}
	
//	public TimeInterval getSyncInterval() {
//		return syncInterval;
//	}
//	public void setSyncInterval(TimeInterval syncInterval) {
//		this.syncInterval = syncInterval;
//	}
	
	public SyncStatus getStatus() {
		return status;
	}

	public int getRetries() 
	{
		return retries;
	}
	
	public void resetRetries()
	{
	    retries = 0;
        lastRetry = null;
	}

	public void setStatus(SyncStatus status) {
		if ( status != this.status)
		{
		    resetRetries();
		}
		this.status = status;
		if ( !status.isUnsynchronized())
		{
		    lastError = null;
		}
	}
	
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((appointmentId == null) ? 0 : appointmentId.hashCode());
		result = prime * result + ((userId == null) ? 0 : userId.hashCode());
		return result;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SynchronizationTask other = (SynchronizationTask) obj;
		if (appointmentId == null) {
			if (other.appointmentId != null)
				return false;
		} else if (!appointmentId.equals(other.appointmentId))
			return false;
		if (userId == null) {
			if (other.userId != null)
				return false;
		} else if (!userId.equals(other.userId))
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		return "SynchronizationTask [userId=" + userId + ", appointmentId="
				+ appointmentId  
				+ ", retries=" + retries
		        + ", lastRetry=" + lastRetry
				+ ", status=" + status + "]";
	}

	public boolean matches(Appointment appointment, User user) {
		if (!matches(user))
		{
			return false;
		}
		return matches(appointment);
	}

	public boolean matches(Appointment appointment) {
		Comparable other_appointmentId = appointment.getId();
		if (appointmentId == null) {
			if (other_appointmentId != null)
				return false;
		} else if (!appointmentId.equals(other_appointmentId))
		{
			return false;
		}
		return true;
	}

	public boolean matches(User user) {
		Comparable other_userId = user.getId();
		if (userId == null) {
			if (other_userId != null)
				return false;
		} 
		else if (!userId.equals(other_userId))
		{
			return false;
		}
		return true;
	}

	public void setPersistantId(String id) {
		this.persistantId = id;
	}
	
	public String getPersistantId()
	{
		return persistantId;
	}

	public boolean matchesUserId(ReferenceInfo<User> otherId)
	{
		if ( otherId == null )
		{
			return false;
		}
		final String secondId = otherId.getId();
		boolean b = secondId == this.userId ||  secondId.equals(this.userId);
		return b;
	}

    public Date getLastRetry() 
    {
        return lastRetry;
    }
    
    public void setLastRetry(Date lastRetry) 
    {
        this.lastRetry = lastRetry;
    }



	
}