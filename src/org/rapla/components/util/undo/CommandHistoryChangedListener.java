package org.rapla.components.util.undo;

import java.util.EventListener;



public interface CommandHistoryChangedListener extends EventListener
{
	public void historyChanged();
}
