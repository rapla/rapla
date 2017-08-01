package org.rapla.storage.impl.server;

import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;

import java.util.Collection;
import java.util.SortedSet;

public interface AllocationMap {
	SortedSet<Appointment> getAppointments(Allocatable allocatable);
	Collection<Allocatable> getAllocatables();
}
