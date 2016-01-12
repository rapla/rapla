package org.rapla.plugin.exchangeconnector;

import org.rapla.components.util.TimeInterval;

import java.util.ArrayList;
import java.util.List;

public class SynchronizationStatus {
	public String username;
	public boolean enabled;
	public List<SyncError> synchronizationErrors = new ArrayList<SyncError>();
	public int synchronizedEvents;
	public int unsynchronizedEvents;
	public TimeInterval syncInterval;
}
