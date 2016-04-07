package org.rapla.storage.impl.server;

import java.util.HashSet;
import java.util.Set;

import org.rapla.entities.domain.Appointment;

class AllocationChange
{
	public Set<Appointment> toChange =  new HashSet<Appointment>();
	public Set<Appointment> toRemove=  new HashSet<Appointment>();
	
	public String toString()
	{
		return "toChange="+toChange.toString() + ";toRemove=" + toRemove.toString();
	}
}