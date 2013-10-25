package org.rapla.storage.impl.server;

import java.util.Collection;
import java.util.SortedSet;

import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;

public interface AllocationMap {
	SortedSet<Appointment> getAppointments(Allocatable allocatable);
	Collection<Allocatable> getAllocatables();
}
