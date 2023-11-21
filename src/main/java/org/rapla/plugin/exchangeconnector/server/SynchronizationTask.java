package org.rapla.plugin.exchangeconnector.server;

import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.storage.ReferenceInfo;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

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
	String mailboxName;
	String userId;
	String appointmentId;

	String resourceId;
	Date lastRetry;
	private int retries = -1;
	String lastError;
	
	//TimeInterval syncInterval;
	SyncStatus status;
	private String persistantId;
	
	public SynchronizationTask(String mailboxName, ReferenceInfo<Appointment> appointmentId, ReferenceInfo<User> userId, ReferenceInfo<Allocatable> resourceId, int retries, Date lastRetry, String lastError) {
		this.userId = userId.getId();
		this.appointmentId = appointmentId.getId();
		this.mailboxName = mailboxName;
		status = SyncStatus.toUpdate;
		this.retries = retries;
		this.resourceId = resourceId.getId();
		this.lastRetry = lastRetry;
		this.lastError = lastError;
	}

	public SynchronizationTask(SynchronisationManager.SynchronizationBox box, ReferenceInfo<Appointment> appointmentId) {
		this( box.getMailboxName(), appointmentId, box.getUserId(), box.getResourceId(), 0, null, null);
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

	public String getResourceId() {
		return resourceId;
	}

	public ReferenceInfo<User> getUserRef()
	{
		return new ReferenceInfo<>(userId, User.class);
	}


	public String getAppointmentId() {
		return appointmentId;
	}

	public String getMailboxName() {
		return mailboxName;
	}

	public void setMailboxName(String mailboxName) {
		this.mailboxName = mailboxName;
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
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		SynchronizationTask that = (SynchronizationTask) o;
		return Objects.equals(userId, that.userId) && Objects.equals(appointmentId, that.appointmentId) && Objects.equals(resourceId, that.resourceId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(userId, appointmentId, resourceId);
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
            return other_appointmentId == null;
		} else return appointmentId.equals(other_appointmentId);
    }

	public boolean matches(User user) {
		Comparable other_userId = user.getId();
		if (userId == null) {
            return other_userId == null;
		} 
		else return userId.equals(other_userId);
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

	public boolean matchesMailbox(String mailboxName)
	{
		if ( mailboxName == null )
		{
			return false;
		}
		boolean b = mailboxName == this.mailboxName ||  mailboxName.equals(this.mailboxName);
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