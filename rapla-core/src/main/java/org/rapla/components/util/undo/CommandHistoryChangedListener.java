package org.rapla.components.util.undo;

import java.util.EventListener;



@FunctionalInterface
public interface CommandHistoryChangedListener extends EventListener
{
	void historyChanged();
}
