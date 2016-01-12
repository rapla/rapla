package org.rapla.storage.impl.server;

import org.rapla.entities.domain.Appointment;

import java.util.HashSet;
import java.util.Set;

class AllocationChange
{
	public Set<Appointment> toChange =  new HashSet<Appointment>();
	public Set<Appointment> toRemove=  new HashSet<Appointment>();
	
	public String toString()
	{
		return "toChange="+toChange.toString() + ";toRemove=" + toRemove.toString();
	}
}