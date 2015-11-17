package org.rapla.plugin.exchangeconnector;

import java.util.ArrayList;
import java.util.List;

import org.rapla.components.util.TimeInterval;

public class SynchronizationStatus {
	public String username;
	public boolean enabled;
	public List<SyncError> synchronizationErrors = new ArrayList<SyncError>();
	public int synchronizedEvents;
	public int unsynchronizedEvents;
	public TimeInterval syncInterval;
}
